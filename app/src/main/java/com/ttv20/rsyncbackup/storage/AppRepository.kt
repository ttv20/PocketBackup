package com.ttv20.rsyncbackup.storage

import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupQueueState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ExportCodec
import com.ttv20.rsyncbackup.model.ExportDocument
import com.ttv20.rsyncbackup.model.InitialData
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.ServerRecord
import com.ttv20.rsyncbackup.model.withImportedConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant

class AppRepository(
    private val dataFile: File,
    private val defaultExcludes: String,
) {
    private val mutableState = MutableStateFlow(InitialData.appState(defaultExcludes))
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    @Synchronized
    fun loadBlocking() {
        val loaded = if (dataFile.exists()) {
            runCatching {
                ExportCodec.json.decodeFromString(AppState.serializer(), dataFile.readText())
            }.getOrElse {
                InitialData.appState(defaultExcludes)
            }
        } else {
            InitialData.appState(defaultExcludes)
        }
        mutableState.value = recoverInterruptedRunningBackup(loaded).copy(runProgress = RunProgressState())
        saveBlocking()
    }

    @Synchronized
    fun update(transform: (AppState) -> AppState) {
        mutableState.value = transform(mutableState.value)
        saveBlocking()
    }

    @Synchronized
    fun updateTransient(transform: (AppState) -> AppState) {
        mutableState.value = transform(mutableState.value)
    }

    fun upsertProfile(profile: BackupProfile) {
        update { state ->
            state.copy(
                profiles = state.profiles.filterNot { it.id == profile.id } + profile,
            )
        }
    }

    fun removeProfile(profileId: String) {
        update { state ->
            state.copy(
                profiles = state.profiles.filterNot { it.id == profileId },
                queue = state.queue.copy(
                    queuedProfileIds = state.queue.queuedProfileIds.filterNot { it == profileId },
                    runningProfileId = state.queue.runningProfileId.takeIf { it != profileId },
                ),
            )
        }
    }

    fun upsertServer(server: ServerRecord) {
        update { state ->
            state.copy(servers = state.servers.filterNot { it.id == server.id } + server)
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

    fun enqueueBackup(profileId: String, now: String = Instant.now().toString()) {
        update { state ->
            if (state.profiles.none { it.id == profileId }) return@update state
            val queue = state.queue
            val queued = if (profileId == queue.runningProfileId || profileId in queue.queuedProfileIds) {
                queue.queuedProfileIds
            } else {
                queue.queuedProfileIds + profileId
            }
            state.copy(
                queue = queue.copy(queuedProfileIds = queued),
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
        mutableState.value = state.copy(
            queue = BackupQueueState(
                runningProfileId = nextProfileId,
                queuedProfileIds = state.queue.queuedProfileIds.drop(1),
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
                state.copy(queue = state.queue.copy(runningProfileId = null))
            } else {
                state
            }
        }
    }

    private fun recoverInterruptedRunningBackup(state: AppState): AppState {
        val interruptedProfileId = state.queue.runningProfileId ?: return state
        return state.copy(
            queue = state.queue.copy(runningProfileId = null),
            runProgress = RunProgressState(),
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
