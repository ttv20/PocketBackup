package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusMarkerTest {
    @Test
    fun statusMarkerContainsRequiredFields() {
        val marker = BackupStatusMarker(
            profileId = "profile",
            profileName = "Phone",
            phoneHostname = "android-phone",
            appVersion = "0.1.0",
            sourcePath = "/storage/emulated/0",
            targetHostUsed = "192.168.3.200",
            targetMode = TargetMode.LAN_ONLY,
            status = "success",
            finishTime = "2026-06-03T00:00:00Z",
            rsyncExitCode = 0,
        )

        val json = marker.toJson()
        val decoded = ExportCodec.json.decodeFromString(BackupStatusMarker.serializer(), json)

        assertEquals("profile", decoded.profileId)
        assertEquals(0, decoded.rsyncExitCode)
        assertTrue(json.contains("targetHostUsed"))
    }
}
