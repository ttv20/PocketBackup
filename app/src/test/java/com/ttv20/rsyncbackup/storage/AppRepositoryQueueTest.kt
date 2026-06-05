package com.ttv20.rsyncbackup.storage

import com.ttv20.rsyncbackup.model.BackupEndReason
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
