package com.ttv20.rsyncbackup.storage

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
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
    fun queueRunsOneProfileAtATime() {
        val repository = repository()
        val second = BackupProfile(
            id = "second",
            name = "Second",
            serverId = "server-home",
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
    fun loadClearsInterruptedRunningJob() {
        val dataFile = temporaryFolder.newFile("state.json")
        val repository = repository(dataFile)
        repository.enqueueBackup("profile-phone", now = "2026-06-03T01:00:00Z")
        repository.startNextQueued(now = "2026-06-03T01:00:01Z")

        val restored = repository(dataFile)

        assertNull(restored.state.value.queue.runningProfileId)
        val profile = restored.state.value.profiles.single { it.id == "profile-phone" }
        assertEquals(RunStatus.CANCELLED, profile.status.lastStatus)
        assertEquals("Backup interrupted before completion", profile.status.lastMessage)
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

    private fun repository(dataFile: File = temporaryFolder.newFile()): AppRepository {
        val repository = AppRepository(
            dataFile = dataFile,
            defaultExcludes = "cache/\n",
        )
        repository.loadBlocking()
        return repository
    }
}
