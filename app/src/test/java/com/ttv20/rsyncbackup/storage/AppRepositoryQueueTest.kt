package com.ttv20.rsyncbackup.storage

import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.ExportCodec
import com.ttv20.rsyncbackup.model.InitialData
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.TrustedHostFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppRepositoryQueueTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun freshRepositoryStartsWithoutDefaultProfileOrTarget() {
        val repository = AppRepository(
            dataFile = temporaryFolder.newFile(),
            defaultExcludes = "cache/\n",
        )

        repository.loadBlocking()

        assertEquals(emptyList<TargetRecord>(), repository.state.value.targets)
        assertEquals(emptyList<BackupProfile>(), repository.state.value.profiles)
    }

    @Test
    fun queueRunsOneProfileAtATime() {
        val repository = repository()
        val second = BackupProfile(
            id = "second",
            name = "Second",
            targetId = "target-home",
            remotePath = "/mnt/backup/second",
            excludes = "cache/\n",
        )
        repository.upsertProfile(second)

        repository.enqueueBackup("profile-phone", now = "2026-06-03T01:00:00Z")
        repository.enqueueBackup("second", now = "2026-06-03T01:00:01Z")
        repository.enqueueBackup("second", now = "2026-06-03T01:00:02Z")

        assertEquals(listOf("profile-phone", "second"), repository.state.value.queue.queuedProfileIds)
        assertEquals("profile-phone", repository.startNextQueued(now = "2026-06-03T01:00:03Z"))
        assertEquals("profile-phone", repository.state.value.queue.runningProfileId)
        assertEquals(listOf("second"), repository.state.value.queue.queuedProfileIds)
        assertNull(repository.startNextQueued())

        repository.completeRunning("profile-phone")

        assertEquals("second", repository.startNextQueued(now = "2026-06-03T01:00:04Z"))
        assertEquals("second", repository.state.value.queue.runningProfileId)
    }

    @Test
    fun enqueueMarksProfileQueued() {
        val repository = repository()

        repository.enqueueBackup("profile-phone", now = "2026-06-03T01:00:00Z")

        val profile = repository.state.value.profiles.single { it.id == "profile-phone" }
        assertEquals(RunStatus.QUEUED, profile.status.lastStatus)
        assertEquals("Queued", profile.status.lastMessage)
    }

    @Test
    fun upsertProfilePreservesExistingOrder() {
        val repository = repository()
        val second = BackupProfile(
            id = "second",
            name = "Second",
            targetId = "target-home",
            remotePath = "/mnt/backup/second",
            excludes = "cache/\n",
        )
        repository.upsertProfile(second)

        repository.upsertProfile(
            repository.state.value.profiles.first().copy(name = "Renamed phone"),
        )

        assertEquals(listOf("profile-phone", "second"), repository.state.value.profiles.map { it.id })
        assertEquals("Renamed phone", repository.state.value.profiles.first().name)
    }

    @Test
    fun queuePreservesRunTrigger() {
        val repository = repository()

        repository.enqueueBackup(
            profileId = "profile-phone",
            now = "2026-06-03T01:00:00Z",
            trigger = BackupRunTrigger.AUTOMATIC,
        )

        assertEquals("profile-phone", repository.startNextQueued(now = "2026-06-03T01:00:01Z"))
        assertEquals(BackupRunTrigger.AUTOMATIC, repository.state.value.queue.runningTrigger)

        repository.completeRunning("profile-phone")

        assertNull(repository.state.value.queue.runningTrigger)
    }

    @Test
    fun loadClearsInterruptedRunningJob() {
        val dataFile = temporaryFolder.newFile("state.json")
        val repository = repository(dataFile)
        repository.enqueueBackup(
            profileId = "profile-phone",
            now = "2026-06-03T01:00:00Z",
            trigger = BackupRunTrigger.AUTOMATIC,
        )
        repository.startNextQueued(now = "2026-06-03T01:00:01Z")

        val restored = repository(dataFile)

        assertNull(restored.state.value.queue.runningProfileId)
        val profile = restored.state.value.profiles.single { it.id == "profile-phone" }
        assertEquals(RunStatus.CANCELLED, profile.status.lastStatus)
        assertEquals("Backup interrupted before completion", profile.status.lastMessage)
        val log = restored.state.value.logs.first()
        assertEquals(RunStatus.CANCELLED, log.status)
        assertEquals(BackupRunTrigger.AUTOMATIC, log.trigger)
        assertEquals(BackupEndReason.CRASH, log.endReason)
        assertEquals("2026-06-03T01:00:01Z", log.startedAt)
    }

    @Test
    fun loadClearsPersistedLiveProgress() {
        val dataFile = temporaryFolder.newFile("state.json")
        val repository = repository(dataFile)
        repository.setRunProgress(
            RunProgressState(
                profileId = "profile-phone",
                profileName = "Phone",
                phase = RunProgressPhase.RUNNING_RSYNC,
                message = "Running rsync",
                currentFile = "DCIM/photo.jpg",
            ),
            persist = true,
        )

        val restored = repository(dataFile)

        assertEquals(RunProgressPhase.IDLE, restored.state.value.runProgress.phase)
        assertNull(restored.state.value.runProgress.profileId)
    }

    @Test
    fun appendLogBoundsPersistedRawOutput() {
        val dataFile = temporaryFolder.newFile("state.json")
        val repository = repository(dataFile)
        val raw = buildString {
            appendLine("first line that should not be retained")
            repeat(40_000) { append('x') }
            appendLine()
            appendLine("sent 123 bytes received 45 bytes")
            append("last useful line")
        }

        repository.appendLog(
            BackupLog(
                id = "log-large",
                profileId = "profile-phone",
                profileName = "Phone",
                startedAt = "2026-06-03T01:00:00Z",
                finishedAt = "2026-06-03T01:00:01Z",
                status = RunStatus.CANCELLED,
                raw = raw,
            ),
        )

        val persistedRaw = repository.state.value.logs.single().raw
        val decodedRaw = ExportCodec.decodeAppState(dataFile.readText()).logs.single().raw
        assertTrue(persistedRaw.length < raw.length)
        assertEquals(persistedRaw, decodedRaw)
        assertTrue(persistedRaw.startsWith("[Earlier raw backup output omitted"))
        assertTrue(persistedRaw.contains("sent 123 bytes received 45 bytes"))
        assertTrue(persistedRaw.endsWith("last useful line"))
    }

    @Test
    fun removeProfileClearsQueueAndRunProgress() {
        val repository = repository()
        repository.enqueueBackup("profile-phone", now = "2026-06-03T01:00:00Z")
        repository.startNextQueued(now = "2026-06-03T01:00:01Z")
        repository.setRunProgress(
            RunProgressState(
                profileId = "profile-phone",
                profileName = "Phone",
                phase = RunProgressPhase.RUNNING_RSYNC,
                message = "Running rsync",
            ),
            persist = true,
        )

        repository.removeProfile("profile-phone")

        val state = repository.state.value
        assertEquals(emptyList<BackupProfile>(), state.profiles)
        assertNull(state.queue.runningProfileId)
        assertEquals(emptyList<String>(), state.queue.queuedProfileIds)
        assertEquals(RunProgressPhase.IDLE, state.runProgress.phase)
        assertNull(state.runProgress.profileId)
    }

    @Test
    fun removeTargetDeletesDependentProfilesAndUnusedFingerprints() {
        val repository = repository()
        val otherTarget = TargetRecord(
            id = "target-office",
            name = "Office backup target",
            user = "ttv20",
            lanHost = "192.168.3.201",
            port = 22,
            defaultRemotePath = "/mnt/backup/office",
        )
        val otherProfile = BackupProfile(
            id = "profile-office",
            name = "Office phone",
            targetId = otherTarget.id,
            remotePath = otherTarget.defaultRemotePath,
            targetMode = TargetMode.LAN_ONLY,
            excludes = "cache/",
        )
        repository.update { state ->
            state.copy(
                targets = state.targets + otherTarget,
                profiles = state.profiles + otherProfile,
                trustedHostFingerprints = listOf(
                    trustedHostFingerprint("home-fingerprint", InitialData.DEFAULT_TARGET_ID),
                    trustedHostFingerprint("office-fingerprint", otherTarget.id),
                ),
            )
        }
        repository.enqueueBackup("profile-phone", now = "2026-06-03T01:00:00Z")
        repository.enqueueBackup("profile-office", now = "2026-06-03T01:00:01Z")
        repository.startNextQueued(now = "2026-06-03T01:00:02Z")
        repository.setRunProgress(
            RunProgressState(profileId = "profile-phone", phase = RunProgressPhase.RUNNING_RSYNC),
            persist = true,
        )

        repository.removeTarget(InitialData.DEFAULT_TARGET_ID)

        val state = repository.state.value
        assertEquals(listOf("target-office"), state.targets.map { it.id })
        assertEquals(listOf("profile-office"), state.profiles.map { it.id })
        assertNull(state.queue.runningProfileId)
        assertEquals(listOf("profile-office"), state.queue.queuedProfileIds)
        assertEquals(RunProgressPhase.IDLE, state.runProgress.phase)
        assertEquals(listOf("office-fingerprint"), state.trustedHostFingerprints.map { it.id })
    }

    @Test
    fun removeTargetKeepsSharedFingerprintGroup() {
        val repository = repository()
        val sharedTarget = TargetRecord(
            id = "target-shared",
            name = "Shared target",
            user = "ttv20",
            lanHost = "192.168.3.202",
            port = 22,
            defaultRemotePath = "/mnt/backup/shared",
            fingerprintGroupId = InitialData.DEFAULT_TARGET_ID,
        )
        repository.update { state ->
            state.copy(
                targets = state.targets + sharedTarget,
                trustedHostFingerprints = listOf(
                    trustedHostFingerprint("shared-fingerprint", InitialData.DEFAULT_TARGET_ID),
                ),
            )
        }

        repository.removeTarget(InitialData.DEFAULT_TARGET_ID)

        val state = repository.state.value
        assertEquals(listOf("target-shared"), state.targets.map { it.id })
        assertEquals(listOf("shared-fingerprint"), state.trustedHostFingerprints.map { it.id })
    }

    @Test
    fun loadRemovesAbandonedOnboardingDraftProfileAndTarget() {
        val dataFile = temporaryFolder.newFile("state.json")
        val base = seededState()
        val defaultTarget = base.targets.single()
        val duplicateTarget = defaultTarget.copy(
            id = "onboarding-target",
            name = "New target 2",
            fingerprintGroupId = "onboarding-target",
        )
        val duplicateProfile = base.profiles.single().copy(
            id = "onboarding-profile",
            name = "Phone backup",
            targetId = duplicateTarget.id,
            remotePath = duplicateTarget.defaultRemotePath,
            remoteSafetyReviewedAt = "2026-06-04T07:00:00Z",
        )
        dataFile.writeText(
            ExportCodec.json.encodeToString(
                AppState.serializer(),
                base.copy(
                    targets = base.targets + duplicateTarget,
                    profiles = base.profiles + duplicateProfile,
                ),
            ),
        )

        val restored = repository(dataFile)

        assertEquals(listOf(InitialData.DEFAULT_TARGET_ID), restored.state.value.targets.map { it.id })
        assertEquals(listOf(InitialData.DEFAULT_PROFILE_ID), restored.state.value.profiles.map { it.id })
    }

    private fun repository(dataFile: File = temporaryFolder.newFile()): AppRepository {
        val repository = AppRepository(
            dataFile = dataFile,
            defaultExcludes = "cache/\n",
        )
        repository.loadBlocking()
        if (repository.state.value.profiles.isEmpty()) {
            repository.update { seededState() }
        }
        return repository
    }

    private fun seededState(): AppState {
        val target = TargetRecord(
            id = InitialData.DEFAULT_TARGET_ID,
            name = "Home backup target",
            user = "ttv20",
            lanHost = "192.168.3.200",
            port = 22,
            defaultRemotePath = "/mnt/backup/phone",
        )
        val profile = BackupProfile(
            id = InitialData.DEFAULT_PROFILE_ID,
            name = "Phone shared storage",
            sourcePath = "/storage/emulated/0",
            targetId = target.id,
            remotePath = "/mnt/backup/phone",
            targetMode = TargetMode.LAN_ONLY,
            excludes = "cache/",
        )
        return AppState(
            targets = listOf(target),
            profiles = listOf(profile),
        )
    }

    private fun trustedHostFingerprint(id: String, targetId: String): TrustedHostFingerprint =
        TrustedHostFingerprint(
            id = id,
            targetId = targetId,
            hostnames = listOf("192.168.3.200"),
            port = 22,
            algorithm = "ssh-ed25519",
            fingerprint = "SHA256:test",
            publicKey = "ssh-ed25519 AAAATEST",
            confirmedAt = "2026-06-03T01:00:00Z",
        )
}
