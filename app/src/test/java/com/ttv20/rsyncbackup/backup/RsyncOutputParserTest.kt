package com.ttv20.rsyncbackup.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class RsyncOutputParserTest {
    @Test
    fun parsesFinalStats() {
        val parser = RsyncOutputParser()
        parser.accept("Number of files: 1,234 (reg: 1,200, dir: 34)")
        parser.accept("Number of regular files transferred: 42")
        parser.accept("Total transferred file size: 10,240 bytes")

        val progress = parser.snapshot()

        assertEquals(1234L, progress.filesDiscovered)
        assertEquals(42L, progress.filesTransferred)
        assertEquals("10,240 bytes", progress.bytesTransferred)
        assertEquals(10240L, progress.bytesTransferredRaw)
    }

    @Test
    fun parsesProgress2Lines() {
        val parser = RsyncOutputParser()
        val progress = parser.accept("1,024 42% 1.00MB/s 0:00:01 (xfr#3, to-chk=7/10)")

        assertEquals(10L, progress.filesDiscovered)
        assertEquals(3L, progress.filesTransferred)
        assertEquals(42, progress.progressPercent)
        assertEquals("1,024 bytes", progress.bytesTransferred)
        assertEquals(1024L, progress.bytesTransferredRaw)
        assertEquals("1.00MB/s", progress.speed)
        assertEquals("0:00:01", progress.duration)
    }

    @Test
    fun parsesShortProgress2LinesWithoutTreatingThemAsFileNames() {
        val parser = RsyncOutputParser()
        parser.accept("DCIM/photo.jpg")
        val progress = parser.accept("120,717,274 76% 2.86MB/s 0:00:12")

        assertEquals(true, parser.isProgressLine("120,717,274 76% 2.86MB/s 0:00:12"))
        assertEquals(76, progress.progressPercent)
        assertEquals("120,717,274 bytes", progress.bytesTransferred)
        assertEquals(120717274L, progress.bytesTransferredRaw)
        assertEquals("2.86MB/s", progress.speed)
        assertEquals("0:00:12", progress.duration)
        assertEquals("DCIM/photo.jpg", progress.currentFile)
    }

    @Test
    fun calculatesFullAverageSpeedFromProgress2Bytes() {
        var now = 0L
        val parser = RsyncOutputParser { now }

        parser.accept("1,000 1% 1.00KB/s 0:00:01")
        now = 60_000L
        parser.accept("61,000 10% 1.00KB/s 0:01:00")
        now = 180_000L
        val progress = parser.accept("181,000 20% 1.00KB/s 0:03:00")

        assertEquals(1000L, progress.averageBytesPerSecond)
        assertEquals(1000L, progress.recentAverageBytesPerSecond)
    }
}
