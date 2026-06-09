package com.ttv20.rsyncbackup.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsSanitizerTest {
    @Test
    fun attributesKeepOnlyAllowedSafeKeys() {
        val sanitized = DiagnosticsSanitizer.sanitizeAttributes(
            mapOf(
                DiagnosticsAttributes.PROFILE_ID to "profile-1",
                DiagnosticsAttributes.TARGET_ID to "target-1",
                DiagnosticsAttributes.TRIGGER_TYPE to "automatic",
                DiagnosticsAttributes.TARGET_MODE to "lan_first_tailscale_fallback",
                DiagnosticsAttributes.ROUTE_USED to "tailscale",
                DiagnosticsAttributes.DRY_RUN_ENABLED to true,
                DiagnosticsAttributes.DELETE_ENABLED to false,
                DiagnosticsAttributes.SCHEDULE_TYPE to "exact_daily",
                DiagnosticsAttributes.DELAY_MS to 65_000L,
                DiagnosticsAttributes.PERMISSION_TYPE to "notifications",
                DiagnosticsAttributes.RESULT to "granted",
                DiagnosticsAttributes.PROFILE_COUNT to 2,
                DiagnosticsAttributes.TARGET_COUNT to 1,
                DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS to listOf("rsync", "ssh", "/bad/path"),
                DiagnosticsAttributes.RSYNC_EXIT_CODE to 23,
                DiagnosticsAttributes.EXIT_CODE to 21,
                DiagnosticsAttributes.FAILURE_STAGE to "dry_run",
                DiagnosticsAttributes.FAILURE_CATEGORY to "rsync_exit_nonzero",
                DiagnosticsAttributes.RSYNC_WARNING_24 to false,
                DiagnosticsAttributes.BACKUP_TOTAL_FILES to 1200L,
                DiagnosticsAttributes.BACKUP_TOTAL_BYTES to 4_096_000L,
                DiagnosticsAttributes.BACKUP_CHANGED_FILES to 12L,
                DiagnosticsAttributes.BACKUP_CHANGED_BYTES to 128_000L,
                DiagnosticsAttributes.BACKUP_DURATION_MS to 5_000L,
                DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS to 750L,
                DiagnosticsAttributes.BACKUP_AVERAGE_BYTES_PER_SECOND to 25_907.20,
                "remote_host" to "backup.example.com",
                "username" to "ttv20",
                "source_path" to "/storage/emulated/0/DCIM",
                "rsync_output" to "sent secret-file.jpg",
                "command" to "rsync /storage/emulated/0 backup.example.com:/srv",
                "ssid" to "Home WiFi",
            ),
        )

        assertEquals("profile-1", sanitized[DiagnosticsAttributes.PROFILE_ID])
        assertEquals("target-1", sanitized[DiagnosticsAttributes.TARGET_ID])
        assertEquals("automatic", sanitized[DiagnosticsAttributes.TRIGGER_TYPE])
        assertEquals("lan_first_tailscale_fallback", sanitized[DiagnosticsAttributes.TARGET_MODE])
        assertEquals("tailscale", sanitized[DiagnosticsAttributes.ROUTE_USED])
        assertEquals(true, sanitized[DiagnosticsAttributes.DRY_RUN_ENABLED])
        assertEquals(false, sanitized[DiagnosticsAttributes.DELETE_ENABLED])
        assertEquals("exact_daily", sanitized[DiagnosticsAttributes.SCHEDULE_TYPE])
        assertEquals(65_000L, sanitized[DiagnosticsAttributes.DELAY_MS])
        assertEquals("notifications", sanitized[DiagnosticsAttributes.PERMISSION_TYPE])
        assertEquals("granted", sanitized[DiagnosticsAttributes.RESULT])
        assertEquals(2, sanitized[DiagnosticsAttributes.PROFILE_COUNT])
        assertEquals(1, sanitized[DiagnosticsAttributes.TARGET_COUNT])
        assertEquals("rsync,ssh", sanitized[DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS])
        assertEquals(23, sanitized[DiagnosticsAttributes.RSYNC_EXIT_CODE])
        assertEquals(21, sanitized[DiagnosticsAttributes.EXIT_CODE])
        assertEquals("dry_run", sanitized[DiagnosticsAttributes.FAILURE_STAGE])
        assertEquals("rsync_exit_nonzero", sanitized[DiagnosticsAttributes.FAILURE_CATEGORY])
        assertEquals(false, sanitized[DiagnosticsAttributes.RSYNC_WARNING_24])
        assertEquals(1200L, sanitized[DiagnosticsAttributes.BACKUP_TOTAL_FILES])
        assertEquals(4_096_000L, sanitized[DiagnosticsAttributes.BACKUP_TOTAL_BYTES])
        assertEquals(12L, sanitized[DiagnosticsAttributes.BACKUP_CHANGED_FILES])
        assertEquals(128_000L, sanitized[DiagnosticsAttributes.BACKUP_CHANGED_BYTES])
        assertEquals(5_000L, sanitized[DiagnosticsAttributes.BACKUP_DURATION_MS])
        assertEquals(750L, sanitized[DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS])
        assertEquals(25_907.20, sanitized[DiagnosticsAttributes.BACKUP_AVERAGE_BYTES_PER_SECOND])
        assertFalse(sanitized.containsKey("remote_host"))
        assertFalse(sanitized.containsKey("username"))
        assertFalse(sanitized.containsKey("source_path"))
        assertFalse(sanitized.containsKey("rsync_output"))
        assertFalse(sanitized.containsKey("command"))
        assertFalse(sanitized.containsKey("ssid"))
    }

    @Test
    fun crashTextScrubsSensitiveStrings() {
        val scrubbed = DiagnosticsSanitizer.scrubCrashText(
            """
            rsync -av /storage/emulated/0/DCIM ttv20@nas.local:/backups/phone
            user=ttv20 host=nas.local ssid=HomeWiFi path=/storage/emulated/0/Downloads
            failed to reach 192.168.3.200
            """.trimIndent(),
        )

        assertTrue(scrubbed.contains("[command omitted]"))
        assertTrue(scrubbed.contains("user=[omitted]"))
        assertTrue(scrubbed.contains("host=[omitted]"))
        assertTrue(scrubbed.contains("ssid=[omitted]"))
        assertTrue(scrubbed.contains("path=[omitted]"))
        assertTrue(scrubbed.contains("[ip omitted]"))
        assertFalse(scrubbed.contains("/storage/emulated/0"))
        assertFalse(scrubbed.contains("ttv20@nas.local"))
        assertFalse(scrubbed.contains("192.168.3.200"))
    }
}
