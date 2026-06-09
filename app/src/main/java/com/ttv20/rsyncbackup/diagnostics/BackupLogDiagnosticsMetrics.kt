package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.model.BackupLog
import java.time.Duration
import java.time.Instant

internal object BackupLogDiagnosticsMetrics {
    fun attributes(log: BackupLog): Map<String, Any> {
        val rawLines = log.raw.lineSequence().toList()
        val dryRunMarkerIndex = rawLines.indexOfLast { it.startsWith(DRY_RUN_PLANNED_PREFIX) }
        val dryRunLines = if (dryRunMarkerIndex >= 0) rawLines.take(dryRunMarkerIndex) else emptyList()
        val backupLines = if (dryRunMarkerIndex >= 0) rawLines.drop(dryRunMarkerIndex + 1) else rawLines
        val dryRunStats = parseStats(dryRunLines)
        val backupStats = parseStats(backupLines)
        val totalStats = backupStats.takeIf { it.totalFiles != null || it.totalBytes != null } ?: dryRunStats
        val changedStats = backupStats.takeIf { it.changedFiles != null || it.changedBytes != null } ?: dryRunStats

        return buildMap {
            totalStats.totalFiles?.let { put(DiagnosticsAttributes.BACKUP_TOTAL_FILES, it) }
            totalStats.totalBytes?.let { put(DiagnosticsAttributes.BACKUP_TOTAL_BYTES, it) }
            changedStats.changedFiles?.let { put(DiagnosticsAttributes.BACKUP_CHANGED_FILES, it) }
            changedStats.changedBytes?.let { put(DiagnosticsAttributes.BACKUP_CHANGED_BYTES, it) }
            durationMillis(log.startedAt, log.finishedAt)?.let { put(DiagnosticsAttributes.BACKUP_DURATION_MS, it) }
            parseDryRunDurationMillis(rawLines)?.let { put(DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS, it) }
            backupStats.averageBytesPerSecond?.let { put(DiagnosticsAttributes.BACKUP_AVERAGE_BYTES_PER_SECOND, it) }
        }
    }

    private fun parseStats(lines: List<String>): RsyncSummaryStats {
        var stats = RsyncSummaryStats()
        lines.forEach { line ->
            when {
                line.startsWith("Number of files:") -> {
                    stats = stats.copy(totalFiles = firstInteger(line.substringAfter(":")))
                }
                line.startsWith("Number of regular files transferred:") -> {
                    stats = stats.copy(changedFiles = firstInteger(line.substringAfter(":")))
                }
                line.startsWith("Total file size:") -> {
                    stats = stats.copy(totalBytes = byteCount(line.substringAfter(":")))
                }
                line.startsWith("Total transferred file size:") -> {
                    stats = stats.copy(changedBytes = byteCount(line.substringAfter(":")))
                }
                line.startsWith("sent ") && line.contains(" bytes/sec") -> {
                    stats = stats.copy(averageBytesPerSecond = bytesPerSecond(line))
                }
            }
        }
        return stats
    }

    private fun firstInteger(value: String): Long? =
        INTEGER_REGEX.find(value)?.value?.replace(",", "")?.toLongOrNull()

    private fun byteCount(value: String): Long? =
        firstInteger(value)

    private fun bytesPerSecond(value: String): Double? =
        BYTES_PER_SECOND_REGEX.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()

    private fun parseDryRunDurationMillis(lines: List<String>): Long? =
        lines
            .firstOrNull { it.startsWith(DRY_RUN_ELAPSED_PREFIX) }
            ?.removePrefix(DRY_RUN_ELAPSED_PREFIX)
            ?.substringBefore(" ms")
            ?.trim()
            ?.toLongOrNull()

    private fun durationMillis(startedAt: String, finishedAt: String?): Long? {
        val finished = finishedAt ?: return null
        return runCatching {
            Duration.between(Instant.parse(startedAt), Instant.parse(finished)).toMillis()
        }.getOrNull()?.takeIf { it >= 0L }
    }

    private data class RsyncSummaryStats(
        val totalFiles: Long? = null,
        val totalBytes: Long? = null,
        val changedFiles: Long? = null,
        val changedBytes: Long? = null,
        val averageBytesPerSecond: Double? = null,
    )

    private val INTEGER_REGEX = Regex("""[\d,]+""")
    private val BYTES_PER_SECOND_REGEX = Regex("""([\d,.]+)\s+bytes/sec""")
    private const val DRY_RUN_PLANNED_PREFIX = "Dry run planned transfer:"
    private const val DRY_RUN_ELAPSED_PREFIX = "Dry run elapsed:"
}
