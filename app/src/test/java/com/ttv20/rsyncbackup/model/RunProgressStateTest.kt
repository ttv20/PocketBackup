package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunProgressStateTest {
    @Test
    fun transferProgressPercentUsesPlannedBytes() {
        val progress = RunProgressState(
            bytesTransferredRaw = 250L,
            plannedTransferBytesRaw = 1_000L,
        )

        assertEquals(25, progress.transferProgressPercent())
    }

    @Test
    fun transferProgressPercentClampsToComplete() {
        val progress = RunProgressState(
            bytesTransferredRaw = 1_200L,
            plannedTransferBytesRaw = 1_000L,
        )

        assertEquals(100, progress.transferProgressPercent())
    }

    @Test
    fun transferProgressPercentRequiresPositivePlan() {
        assertNull(
            RunProgressState(
                bytesTransferredRaw = 10L,
                plannedTransferBytesRaw = 0L,
            ).transferProgressPercent(),
        )
    }
}
