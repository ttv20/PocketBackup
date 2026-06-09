package com.ttv20.rsyncbackup.storage

import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupQueueState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.ExportCodec
import com.ttv20.rsyncbackup.model.ExportDocument
import com.ttv20.rsyncbackup.model.InitialData
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.withImportedConfiguration
import com.ttv20.rsyncbackup.model.withUpdatedSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.util.UUID

private const val MAX_PERSISTED_LOG_RAW_CHARS = 32_768
private const val TRUNCATED_RAW_LOG_PREFIX = "[Earlier raw backup output omitted to keep app storage bounded]\n"

private fun <T> List<T>.replaceOrAppend(item: T, matches: (T) -> Boolean): List<T> {
    val existingIndex = indexOfFirst(matches)
    return if (existingIndex == -1) {
        this + item
    } else {
        toMutableList().apply { set(existingIndex, item) }
    }
}

class AppRepository(
    private val dataFile: File,
    val defaultExcludes: String,
) {
    private val mutableState = MutableStateFlow(InitialData.appState(defaultExcludes))
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    @Synchronized
    fun loadBlocking() {
        val loaded = if (dataFile.exists()) {
            runCatching {
                ExportCodec.decodeAppState(dataFile.readText())
            }.getOrElse {
                InitialData.appState(defaultExcludes)
            }
        } else {
            InitialData.appState(defaultExcludes)
        }
        val recovered = removeAbandonedOnboardingDuplicates(
            recoverInterruptedRunningBackups(loaded),
        ).copy(runProgress = RunProgressState())
        mutableState.value = sanitizeStateForStorage(recovered.withUpdatedSettings(recovered.settings))
        saveBlocking()
    }

    @Synchronized
    fun update(transform: (AppState) -> AppState) {
        mutableState.value = sanitizeStateForStorage(transform(mutableState.value))
        saveBlocking()
    }

    @Synchronized
    fun updateTransient(transform: (AppState) -> AppState) {
        mutableState.value = transform(mutableState.value)
    }

    fun upsertProfile(profile: BackupProfile) {
        update { state ->
            state.copy(
                profiles = state.profiles.replaceOrAppend(profile) { it.id == profile.id },
            )
        }
    }

    fun removeProfile(profileId: String) {
        update { state ->
            stateWithoutProfiles(state, setOf(profileId))
        }
    }

    fun upsertTarget(target: TargetRecord) {
        update { state ->
            state.copy(targets = state.targets.replaceOrAppend(target) { it.id == target.id })
        }
    }

    fun removeTarget(targetId: String) {
        update { state ->
            val target = state.targets.firstOrNull { it.id == targetId } ?: return@update state
            val remainingTargets = state.targets.filterNot { it.id == targetId }
            val removedProfileIds = state.profiles
                .filter { it.targetId == targetId }
                .map { it.id }
                .toSet()
            val remainingFingerprintLookupIds = remainingTargets
                .flatMap { listOf(it.id, it.fingerprintGroupId) }
                .toSet()
            val removedFingerprintLookupIds = setOf(target.id, target.fingerprintGroupId) - remainingFingerprintLookupIds

            stateWithoutProfiles(state, removedProfileIds).copy(
                targets = remainingTargets,
                trustedHostFingerprints = state.trustedHostFingerprints.filterNot {
                    it.targetId in removedFingerprintLookupIds
                },
            )
        }
    }

    fun appendLog(log: BackupLog) {
        update { state ->
            val retained = (listOf(log) + state.logs).take(state.settings.logRetentionLimit.coerceAtLeast(1))
            state.copy(logs = retained)
        }
    }

    fun clearLogs() {
        update { state -> state.copy(logs = emptyList()) }
    }

    fun enqueueBackup(
        profileId: String,
        now: String = Instant.now().toString(),
        trigger: BackupRunTrigger = BackupRunTrigger.MANUAL,
    ) {
        update { state ->
            if (state.profiles.none { it.id == profileId }) return@update state
            val queue = state.queue
            val isAlreadyQueuedOrRunning = profileId == queue.runningProfileId || profileId in queue.queuedProfileIds
            val queued = if (isAlreadyQueuedOrRunning) {
                queue.queuedProfileIds
            } else {
                queue.queuedProfileIds + profileId
            }
            state.copy(
                queue = queue.copy(
                    queuedProfileIds = queued,
                    queuedTriggers = if (isAlreadyQueuedOrRunning) {
                        queue.queuedTriggers
                    } else {
                        queue.queuedTriggers + (profileId to trigger)
                    },
                ),
                profiles = state.profiles.map { profile ->
                    if (profile.id == profileId && profile.status.lastStatus != RunStatus.RUNNING) {
                        profile.copy(
                            status = profile.status.copy(
                                lastRunAt = now,
                                lastStatus = RunStatus.QUEUED,
                                lastMessage = "Queued",
                            ),
                        )
                    } else {
                        profile
                    }
                },
            )
        }
    }

    @Synchronized
    fun startNextQueued(now: String = Instant.now().toString()): String? {
        val state = mutableState.value
        if (state.queue.runningProfileId != null) return null
        val nextProfileId = state.queue.queuedProfileIds.firstOrNull() ?: return null
        val trigger = state.queue.queuedTriggers[nextProfileId] ?: BackupRunTrigger.MANUAL
        mutableState.value = state.copy(
            queue = BackupQueueState(
                runningProfileId = nextProfileId,
                queuedProfileIds = state.queue.queuedProfileIds.drop(1),
                runningTrigger = trigger,
                queuedTriggers = state.queue.queuedTriggers - nextProfileId,
            ),
            profiles = state.profiles.map { profile ->
                if (profile.id == nextProfileId) {
                    profile.copy(
                        status = profile.status.copy(
                            lastRunAt = now,
                            lastStatus = RunStatus.RUNNING,
                            lastMessage = "Backup running",
                        ),
                    )
                } else {
                    profile
                }
            },
        )
        saveBlocking()
        return nextProfileId
    }

    fun completeRunning(profileId: String) {
        update { state ->
            if (state.queue.runningProfileId == profileId) {
                state.copy(
                    queue = state.queue.copy(
                        runningProfileId = null,
                        runningTrigger = null,
                    ),
                )
            } else {
                state
            }
        }
    }

    private fun stateWithoutProfiles(state: AppState, profileIds: Set<String>): AppState {
        if (profileIds.isEmpty()) return state
        val removingRunningProfile = state.queue.runningProfileId?.let { it in profileIds } == true
        return state.copy(
            profiles = state.profiles.filterNot { it.id in profileIds },
            queue = state.queue.copy(
                queuedProfileIds = state.queue.queuedProfileIds.filterNot { it in profileIds },
                runningProfileId = state.queue.runningProfileId.takeUnless { it != null && it in profileIds },
                runningTrigger = state.queue.runningTrigger.takeUnless { removingRunningProfile },
                queuedTriggers = state.queue.queuedTriggers.filterKeys { it !in profileIds },
            ),
            runProgress = state.runProgress.takeUnless {
                it.profileId != null && it.profileId in profileIds
            }
                ?: RunProgressState(),
        )
    }

    private fun sanitizeStateForStorage(state: AppState): AppState {
        val retainedLogs = state.logs
            .take(state.settings.logRetentionLimit.coerceAtLeast(1))
            .map { it.withBoundedRawOutput() }
        return state.copy(logs = retainedLogs)
    }

    private fun BackupLog.withBoundedRawOutput(): BackupLog {
        if (raw.length <= MAX_PERSISTED_LOG_RAW_CHARS) return this
        val tailLength = (MAX_PERSISTED_LOG_RAW_CHARS - TRUNCATED_RAW_LOG_PREFIX.length)
            .coerceAtLeast(0)
        val boundedRaw = TRUNCATED_RAW_LOG_PREFIX + raw.takeLast(tailLength).trimStart('\n', '\r')
        return copy(raw = boundedRaw)
    }

    private fun removeAbandonedOnboardingDuplicates(state: AppState): AppState {
        val defaultProfile = state.profiles.firstOrNull { it.id == InitialData.DEFAULT_PROFILE_ID } ?: return state
        val defaultTarget = state.targets.firstOrNull { it.id == defaultProfile.targetId } ?: return state
        val duplicateProfiles = state.profiles.filter { profile ->
            isAbandonedOnboardingProfile(profile, defaultProfile, defaultTarget, state.targets)
        }
        if (duplicateProfiles.isEmpty()) return state

        val duplicateProfileIds = duplicateProfiles.map { it.id }.toSet()
        val remainingProfiles = state.profiles.filterNot { it.id in duplicateProfileIds }
        val duplicateTargetIds = duplicateProfiles.map { it.targetId }.toSet()
        val removableTargetIds = state.targets
            .filter { target -> target.id in duplicateTargetIds }
            .filter { target ->
                remainingProfiles.none { it.targetId == target.id } &&
                    state.trustedHostFingerprints.none {
                        it.targetId == target.id || it.targetId == target.fingerprintGroupId
                    }
            }
            .map { it.id }
            .toSet()
        val removingRunningProfile = state.queue.runningProfileId in duplicateProfileIds

        return state.copy(
            targets = state.targets.filterNot { it.id in removableTargetIds },
            profiles = remainingProfiles,
            queue = state.queue.copy(
                runningProfileId = state.queue.runningProfileId.takeUnless { it in duplicateProfileIds },
                queuedProfileIds = state.queue.queuedProfileIds.filterNot { it in duplicateProfileIds },
                runningTrigger = state.queue.runningTrigger.takeUnless { removingRunningProfile },
                queuedTriggers = state.queue.queuedTriggers.filterKeys { it !in duplicateProfileIds },
            ),
            runProgress = state.runProgress.takeUnless { it.profileId in duplicateProfileIds }
                ?: RunProgressState(),
        )
    }

    private fun isAbandonedOnboardingProfile(
        profile: BackupProfile,
        defaultProfile: BackupProfile,
        defaultTarget: TargetRecord,
        targets: List<TargetRecord>,
    ): Boolean {
        if (profile.id == defaultProfile.id) return false
        if (profile.name != "Phone backup") return false
        if (profile.sourcePath != defaultProfile.sourcePath) return false
        if (profile.status.lastStatus != RunStatus.NEVER_RUN || profile.status.lastRunAt != null) return false
        val target = targets.firstOrNull { it.id == profile.targetId } ?: return false
        return isAbandonedOnboardingTarget(target, defaultTarget)
    }

    private fun isAbandonedOnboardingTarget(target: TargetRecord, defaultTarget: TargetRecord): Boolean =
        target.id != defaultTarget.id &&
            target.name.startsWith("New target") &&
            target.user == defaultTarget.user &&
            target.lanHost == defaultTarget.lanHost &&
            target.port == defaultTarget.port &&
            target.tailscaleHost == defaultTarget.tailscaleHost &&
            target.defaultRemotePath == defaultTarget.defaultRemotePath &&
            target.publicKeyInstalledAt == null &&
            target.keyOnlyLoginVerifiedAt == null

    private fun recoverInterruptedRunningBackups(state: AppState): AppState {
        val recoveredQueuedRun = recoverQueuedRunningBackup(state)
        return recoverOrphanedRunningProfiles(recoveredQueuedRun)
    }

    private fun recoverQueuedRunningBackup(state: AppState): AppState {
        val interruptedProfileId = state.queue.runningProfileId ?: return state
        val recoveredAt = Instant.now().toString()
        val interruptedProfile = state.profiles.firstOrNull { it.id == interruptedProfileId }
        val interruptedLog = interruptedProfile?.let { profile ->
            interruptedBackupLog(
                profile = profile,
                trigger = state.queue.runningTrigger ?: BackupRunTrigger.MANUAL,
                recoveredAt = recoveredAt,
                detail = "App stopped while backup was running",
            )
        }
        return state.copy(
            queue = state.queue.copy(runningProfileId = null, runningTrigger = null),
            runProgress = RunProgressState(),
            logs = interruptedLog?.let { log ->
                (listOf(log) + state.logs).take(state.settings.logRetentionLimit.coerceAtLeast(1))
            } ?: state.logs,
            profiles = state.profiles.map { profile ->
                if (profile.id == interruptedProfileId && profile.status.lastStatus == RunStatus.RUNNING) {
                    profile.copy(
                        status = profile.status.copy(
                            lastStatus = RunStatus.CANCELLED,
                            lastMessage = "Backup interrupted before completion",
                        ),
                    )
                } else {
                    profile
                }
            },
        )
    }

    private fun recoverOrphanedRunningProfiles(state: AppState): AppState {
        val orphanedProfiles = state.profiles.filter { profile ->
            profile.status.lastStatus == RunStatus.RUNNING && profile.id != state.queue.runningProfileId
        }
        if (orphanedProfiles.isEmpty()) return state

        val recoveredAt = Instant.now().toString()
        val logs = orphanedProfiles
            .map { profile ->
                interruptedBackupLog(
                    profile = profile,
                    trigger = BackupRunTrigger.MANUAL,
                    recoveredAt = recoveredAt,
                    detail = "App state said this backup was running, but no running job was recorded",
                )
            }
        val orphanedProfileIds = orphanedProfiles.map { it.id }.toSet()
        return state.copy(
            runProgress = RunProgressState(),
            logs = (logs + state.logs).take(state.settings.logRetentionLimit.coerceAtLeast(1)),
            profiles = state.profiles.map { profile ->
                if (profile.id in orphanedProfileIds) {
                    profile.copy(
                        status = profile.status.copy(
                            lastStatus = RunStatus.CANCELLED,
                            lastMessage = "Backup interrupted before completion",
                        ),
                    )
                } else {
                    profile
                }
            },
        )
    }

    private fun interruptedBackupLog(
        profile: BackupProfile,
        trigger: BackupRunTrigger,
        recoveredAt: String,
        detail: String,
    ): BackupLog =
        BackupLog(
            id = UUID.randomUUID().toString(),
            profileId = profile.id,
            profileName = profile.name,
            startedAt = profile.status.lastRunAt ?: recoveredAt,
            finishedAt = recoveredAt,
            status = RunStatus.CANCELLED,
            trigger = trigger,
            endReason = BackupEndReason.CRASH,
            endReasonDetail = detail,
            summary = "Backup cancelled: interrupted before completion",
            raw = "Backup interrupted before completion",
        )

    fun setRunProgress(progress: RunProgressState, persist: Boolean = false) {
        val transform: (AppState) -> AppState = { it.copy(runProgress = progress) }
        if (persist) {
            update(transform)
        } else {
            updateTransient(transform)
        }
    }

    fun appendRunProgressOutput(
        progress: RunProgressState,
        outputLine: String?,
        persist: Boolean = false,
    ) {
        val recentOutput = if (outputLine == null) {
            progress.recentOutput
        } else {
            (progress.recentOutput + outputLine).takeLast(50)
        }
        setRunProgress(progress.copy(recentOutput = recentOutput), persist)
    }

    fun markRunningMessage(
        profileId: String,
        message: String,
        now: String = Instant.now().toString(),
        progressPhase: RunProgressPhase? = null,
    ) {
        updateTransient { state ->
            state.copy(
                runProgress = if (state.runProgress.profileId == profileId && progressPhase != null) {
                    state.runProgress.copy(
                        phase = progressPhase,
                        message = message,
                        updatedAt = now,
                    )
                } else {
                    state.runProgress
                },
                profiles = state.profiles.map { profile ->
                    if (profile.id == profileId && profile.status.lastStatus == RunStatus.RUNNING) {
                        profile.copy(
                            status = profile.status.copy(
                                lastMessage = message,
                            ),
                        )
                    } else {
                        profile
                    }
                },
            )
        }
    }

    fun markProfile(profileId: String, status: RunStatus, message: String, now: String = Instant.now().toString()) {
        update { state ->
            state.copy(
                profiles = state.profiles.map { profile ->
                    if (profile.id == profileId) {
                        profile.copy(
                            status = profile.status.copy(
                                lastRunAt = now,
                                lastSuccessAt = if (status == RunStatus.SUCCESS) now else profile.status.lastSuccessAt,
                                lastStatus = status,
                                lastMessage = message,
                            ),
                        )
                    } else {
                        profile
                    }
                },
            )
        }
    }

    fun importConfiguration(document: ExportDocument) {
        update { it.withImportedConfiguration(document) }
    }

    @Synchronized
    private fun saveBlocking() {
        dataFile.parentFile?.mkdirs()
        val tmp = File(dataFile.parentFile, "${dataFile.name}.tmp")
        tmp.writeText(ExportCodec.json.encodeToString(AppState.serializer(), mutableState.value))
        if (!tmp.renameTo(dataFile)) {
            tmp.copyTo(dataFile, overwrite = true)
            tmp.delete()
        }
    }
}
