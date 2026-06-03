package com.ttv20.rsyncbackup.backup

data class RsyncProgress(
    val filesDiscovered: Long? = null,
    val filesTransferred: Long? = null,
    val progressPercent: Int? = null,
    val bytesTransferred: String? = null,
    val bytesTransferredRaw: Long? = null,
    val speed: String? = null,
    val averageBytesPerSecond: Long? = null,
    val recentAverageBytesPerSecond: Long? = null,
    val duration: String? = null,
    val currentFile: String? = null,
    val finalStats: Map<String, String> = emptyMap(),
)

class RsyncOutputParser(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val stats = linkedMapOf<String, String>()
    private var firstTransferSample: TransferSample? = null
    private var lastTransferSample: TransferSample? = null
    private var filesDiscovered: Long? = null
    private var filesTransferred: Long? = null
    private var progressPercent: Int? = null
    private var bytesTransferred: String? = null
    private var bytesTransferredRaw: Long? = null
    private var speed: String? = null
    private var averageBytesPerSecond: Long? = null
    private var recentAverageBytesPerSecond: Long? = null
    private var duration: String? = null
    private var currentFile: String? = null

    fun accept(line: String): RsyncProgress {
        val progressLine = parseProgress2(line)
        if (!progressLine && line.isNotBlank() && !line.contains(": ")) {
            currentFile = line.trim()
        }

        parseStat(line, "Number of files")?.let {
            filesDiscovered = it.substringBefore(" ").replace(",", "").toLongOrNull()
        }
        parseStat(line, "Number of regular files transferred")?.let {
            filesTransferred = it.replace(",", "").toLongOrNull()
        }
        parseStat(line, "Total transferred file size")?.let {
            bytesTransferred = it
            bytesTransferredRaw = parseByteCount(it)
        }
        parseStat(line, "sent")?.let {
            speed = line.substringAfter("bytes/sec", missingDelimiterValue = "").trim().ifBlank { null }
        }
        parseStat(line, "total size")?.let {
            duration = line.substringAfter("speedup is", missingDelimiterValue = "").trim().ifBlank { null }
        }

        return snapshot()
    }

    fun isProgressLine(line: String): Boolean {
        val trimmed = line.trim()
        return PROGRESS2_WITH_COUNTS_REGEX.matches(trimmed) || PROGRESS2_SIMPLE_REGEX.matches(trimmed)
    }

    fun snapshot(): RsyncProgress =
        RsyncProgress(
            filesDiscovered = filesDiscovered,
            filesTransferred = filesTransferred,
            progressPercent = progressPercent,
            bytesTransferred = bytesTransferred,
            bytesTransferredRaw = bytesTransferredRaw,
            speed = speed,
            averageBytesPerSecond = averageBytesPerSecond,
            recentAverageBytesPerSecond = recentAverageBytesPerSecond,
            duration = duration,
            currentFile = currentFile,
            finalStats = stats.toMap(),
        )

    private fun parseStat(line: String, label: String): String? {
        if (!line.startsWith("$label:") && !line.startsWith("$label ")) return null
        val value = line.substringAfter(": ", missingDelimiterValue = line.substringAfter(label)).trim()
        stats[label] = value
        return value
    }

    private fun parseProgress2(line: String): Boolean {
        val trimmed = line.trim()
        val match = PROGRESS2_WITH_COUNTS_REGEX.find(trimmed)
        if (match != null) {
            setTransferredBytes(match.groupValues[1])
            progressPercent = match.groupValues[2].toIntOrNull()
            speed = match.groupValues[3]
            duration = match.groupValues[4]
            filesTransferred = match.groupValues[5].toLongOrNull() ?: filesTransferred
            filesDiscovered = match.groupValues[7].toLongOrNull() ?: filesDiscovered
            return true
        }
        val simpleMatch = PROGRESS2_SIMPLE_REGEX.find(trimmed) ?: return false
        setTransferredBytes(simpleMatch.groupValues[1])
        progressPercent = simpleMatch.groupValues[2].toIntOrNull()
        speed = simpleMatch.groupValues[3]
        duration = simpleMatch.groupValues[4]
        return true
    }

    private fun setTransferredBytes(rawValue: String) {
        val bytes = parseByteCount(rawValue) ?: return
        bytesTransferredRaw = bytes
        bytesTransferred = "$rawValue bytes"
        recordTransferSample(bytes)
    }

    private fun recordTransferSample(bytes: Long) {
        val now = nowMillis()
        if (lastTransferSample?.bytes == bytes) return
        val sample = TransferSample(now, bytes)
        if (firstTransferSample == null) {
            firstTransferSample = sample
        }
        lastTransferSample = sample
        val first = firstTransferSample
        val last = lastTransferSample
        averageBytesPerSecond = if (
            first != null &&
            last != null &&
            last.timeMillis > first.timeMillis &&
            last.bytes >= first.bytes
        ) {
            ((last.bytes - first.bytes) * 1000L) / (last.timeMillis - first.timeMillis)
        } else {
            null
        }
        recentAverageBytesPerSecond = averageBytesPerSecond
    }

    private fun parseByteCount(value: String): Long? =
        value.substringBefore(" ")
            .replace(",", "")
            .toLongOrNull()

    private data class TransferSample(
        val timeMillis: Long,
        val bytes: Long,
    )

    private companion object {
        val PROGRESS2_WITH_COUNTS_REGEX = Regex("""^([\d,]+)\s+(\d+)%\s+(\S+)\s+(\S+)\s+\(xfr#(\d+),\s*(?:to-chk|ir-chk)=(\d+)/(\d+)\)""")
        val PROGRESS2_SIMPLE_REGEX = Regex("""^([\d,]+)\s+(\d+)%\s+(\S+)\s+(\S+)$""")
    }
}
