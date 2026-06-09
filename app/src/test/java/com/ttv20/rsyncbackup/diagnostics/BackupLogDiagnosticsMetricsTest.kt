package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupLogDiagnosticsMetricsTest {
    @Test
    fun extractsFinishedBackupMetricsFromRsyncSummary() {
        val log = BackupLog(
            id = "log-1",
            profileId = "profile-1",
            profileName = "Phone",
            startedAt = "2026-06-09T10:00:00Z",
            finishedAt = "2026-06-09T10:00:05Z",
            status = RunStatus.SUCCESS,
            raw = """
                Number of files: 1,200 (reg: 1,000, dir: 200)
                Number of regular files transferred: 12
                Total file size: 4,096,000 bytes
                Total transferred file size: 128,000 bytes
                sent 129,024 bytes  received 512 bytes  25,907.20 bytes/sec
                total size is 4,096,000  speedup is 31.62
                Dry run elapsed: 750 ms
                Dry run planned transfer: 128000 bytes
                Number of files: 1,200 (reg: 1,000, dir: 200)
                Number of regular files transferred: 12
                Total file size: 4,096,000 bytes
                Total transferred file size: 128,000 bytes
                sent 129,024 bytes  received 512 bytes  25,907.20 bytes/sec
                total size is 4,096,000  speedup is 31.62
                Backup completed
            """.trimIndent(),
        )

        val attributes = BackupLogDiagnosticsMetrics.attributes(log)

        assertEquals(1200L, attributes[DiagnosticsAttributes.BACKUP_TOTAL_FILES])
        assertEquals(4_096_000L, attributes[DiagnosticsAttributes.BACKUP_TOTAL_BYTES])
        assertEquals(12L, attributes[DiagnosticsAttributes.BACKUP_CHANGED_FILES])
        assertEquals(128_000L, attributes[DiagnosticsAttributes.BACKUP_CHANGED_BYTES])
        assertEquals(5_000L, attributes[DiagnosticsAttributes.BACKUP_DURATION_MS])
        assertEquals(750L, attributes[DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS])
        assertEquals(25_907.20, attributes[DiagnosticsAttributes.BACKUP_AVERAGE_BYTES_PER_SECOND])
    }

    @Test
    fun usesDryRunTotalsWhenBackupWasSkippedBecauseNothingChanged() {
        val log = BackupLog(
            id = "log-2",
            profileId = "profile-1",
            profileName = "Phone",
            startedAt = "2026-06-09T10:00:00Z",
            finishedAt = "2026-06-09T10:00:02Z",
            status = RunStatus.SUCCESS,
            raw = """
                Number of files: 100 (reg: 90, dir: 10)
                Number of regular files transferred: 0
                Total file size: 64,000 bytes
                Total transferred file size: 0 bytes
                Dry run elapsed: 500 ms
                Dry run planned transfer: 0 bytes
                Backup completed: no data to transfer
            """.trimIndent(),
        )

        val attributes = BackupLogDiagnosticsMetrics.attributes(log)

        assertEquals(100L, attributes[DiagnosticsAttributes.BACKUP_TOTAL_FILES])
        assertEquals(64_000L, attributes[DiagnosticsAttributes.BACKUP_TOTAL_BYTES])
        assertEquals(0L, attributes[DiagnosticsAttributes.BACKUP_CHANGED_FILES])
        assertEquals(0L, attributes[DiagnosticsAttributes.BACKUP_CHANGED_BYTES])
        assertEquals(2_000L, attributes[DiagnosticsAttributes.BACKUP_DURATION_MS])
        assertEquals(500L, attributes[DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS])
    }
}
