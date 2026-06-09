package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.ConstraintSettings
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.storage.AppRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupLogReasonsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun networkConstraintBlockedRunIsLoggedWithNoNetworkReason() {
        val repository = AppRepository(
            dataFile = temporaryFolder.newFile("state.json"),
            defaultExcludes = "cache/\n",
        )
        repository.loadBlocking()
        val target = TargetRecord(
            id = "target-home",
            name = "Home backup target",
            user = "ttv20",
            lanHost = "192.168.3.200",
        )
        repository.upsertTarget(target)
        repository.upsertProfile(
            BackupProfile(
                id = "profile-phone",
                name = "Phone shared storage",
                sourcePath = "/storage/emulated/0",
                targetId = target.id,
                remotePath = "/mnt/backup/phone",
                targetMode = TargetMode.LAN_ONLY,
                excludes = "cache/",
            ),
        )
        val profile = repository.state.value.profiles.single()

        val log = repository.recordConstraintBlockedBackup(
            profile = profile,
            failures = listOf(ConstraintFailure("wifi_only", "Wi-Fi connection is required")),
            trigger = BackupRunTrigger.AUTOMATIC,
            now = "2026-06-03T01:00:00Z",
        )

        assertEquals(RunStatus.CANCELLED, log.status)
        assertEquals(BackupRunTrigger.AUTOMATIC, log.trigger)
        assertEquals(BackupEndReason.NO_NETWORK, log.endReason)
        assertEquals("Wi-Fi connection is required", log.endReasonDetail)
        assertEquals(log, repository.state.value.logs.single())
    }

    @Test
    fun scheduledConstraintBlockedRunLogsProfileAndCurrentDeviceState() {
        val repository = AppRepository(
            dataFile = temporaryFolder.newFile("state.json"),
            defaultExcludes = "cache/\n",
        )
        repository.loadBlocking()
        val target = TargetRecord(
            id = "target-home",
            name = "Home backup target",
            user = "ttv20",
            lanHost = "192.168.3.200",
        )
        val profile = BackupProfile(
            id = "profile-phone",
            name = "Phone shared storage",
            sourcePath = "/storage/emulated/0",
            targetId = target.id,
            remotePath = "/mnt/backup/phone",
            targetMode = TargetMode.LAN_ONLY,
            constraints = ConstraintSettings(
                wifiOnly = true,
                chargingOnly = true,
                selectedSsidOnly = true,
                selectedSsid = "Home WiFi",
            ),
            excludes = "cache/",
        )
        repository.upsertTarget(target)
        repository.upsertProfile(profile)
        val snapshot = ConstraintSnapshot(
            hasWifiConnection = false,
            isUnmetered = true,
            isCharging = false,
            isBatteryLow = false,
            ssid = "Guest WiFi",
        )
        val failures = BackupConstraintEvaluator.failures(profile, snapshot)

        val log = repository.recordConstraintBlockedBackup(
            profile = profile,
            failures = failures,
            trigger = BackupRunTrigger.AUTOMATIC,
            snapshot = snapshot,
            now = "2026-06-03T01:00:00Z",
        )

        assertEquals("Scheduled backup skipped: 3 constraints not met", log.summary)
        assertEquals(
            "Wi-Fi connection is required; Charging is required; Current WiFi network does not match selected WiFi network",
            log.endReasonDetail,
        )
        assertTrue(log.raw.contains("Profile: Phone shared storage"))
        assertTrue(log.raw.contains("Trigger: Scheduled"))
        assertTrue(log.raw.contains("- Wi-Fi only: yes"))
        assertTrue(log.raw.contains("- Charging only: yes"))
        assertTrue(log.raw.contains("- Selected Wi-Fi only: yes (Home WiFi)"))
        assertTrue(log.raw.contains("- Wi-Fi connected: no"))
        assertTrue(log.raw.contains("- Unmetered network: yes"))
        assertTrue(log.raw.contains("- Charging: no"))
        assertTrue(log.raw.contains("- Current Wi-Fi: Guest WiFi"))
        assertEquals(log, repository.state.value.logs.single())
        assertEquals(log.summary, repository.state.value.profiles.single().status.lastMessage)
    }
}
