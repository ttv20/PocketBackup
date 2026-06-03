package com.ttv20.rsyncbackup.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BackupProcessControllerTest {
    @Test
    fun gracefulCancelTerminatesActiveProcess() {
        val controller = BackupProcessController()
        val ready = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<CommandRunResult> {
            controller.run(
                command = listOf("sh", "-c", "echo ready; exec sleep 30"),
                directory = File("."),
                onLine = { if (it == "ready") ready.countDown() },
            )
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        assertTrue(controller.requestGracefulCancel())

        val result = future.get(3, TimeUnit.SECONDS)
        assertEquals(BackupStopReason.CANCELLED, result.stopReason)
        executor.shutdownNow()
    }

    @Test
    fun stopRequestPreventsStartingNewCommand() {
        val controller = BackupProcessController()

        controller.requestGracefulCancel()
        val result = controller.run(
            command = listOf("sh", "-c", "exit 0"),
            directory = File("."),
        )

        assertEquals(BackupStopReason.CANCELLED, result.stopReason)
    }
}
