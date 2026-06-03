package com.ttv20.rsyncbackup.backup

import java.io.File
import java.io.IOException

enum class BackupStopReason {
    CANCELLED,
    FORCE_STOPPED,
}

data class CommandRunResult(
    val exitCode: Int?,
    val stopReason: BackupStopReason? = null,
)

class BackupProcessController {
    private val lock = Any()

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    var stopReason: BackupStopReason? = null
        private set

    fun reset() {
        synchronized(lock) {
            if (activeProcess == null) {
                stopReason = null
            }
        }
    }

    fun requestGracefulCancel(): Boolean =
        requestStop(BackupStopReason.CANCELLED) { destroy() }

    fun requestForceStop(): Boolean =
        requestStop(BackupStopReason.FORCE_STOPPED) { destroyForcibly() }

    fun isGracefulCancelPending(): Boolean =
        stopReason == BackupStopReason.CANCELLED && activeProcess?.isAlive == true

    fun run(
        command: List<String>,
        directory: File,
        stdin: String? = null,
        configure: (ProcessBuilder) -> Unit = {},
        onLine: (String) -> Unit = {},
    ): CommandRunResult {
        synchronized(lock) {
            stopReason?.let { return CommandRunResult(exitCode = null, stopReason = it) }
        }

        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
            .directory(directory)
        configure(processBuilder)
        val process = processBuilder.start()

        synchronized(lock) {
            activeProcess = process
            when (stopReason) {
                BackupStopReason.CANCELLED -> process.destroy()
                BackupStopReason.FORCE_STOPPED -> process.destroyForcibly()
                null -> Unit
            }
        }

        return try {
            writeStdin(process, stdin)
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(onLine)
            }
            CommandRunResult(exitCode = process.waitFor(), stopReason = stopReason)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            requestForceStop()
            CommandRunResult(exitCode = null, stopReason = BackupStopReason.FORCE_STOPPED)
        } catch (error: IOException) {
            stopReason?.let { CommandRunResult(exitCode = null, stopReason = it) } ?: throw error
        } finally {
            synchronized(lock) {
                if (activeProcess === process) {
                    activeProcess = null
                }
            }
        }
    }

    private fun writeStdin(process: Process, stdin: String?) {
        if (stdin != null) {
            process.outputStream.bufferedWriter().use { it.write(stdin) }
        } else {
            process.outputStream.close()
        }
    }

    private fun requestStop(reason: BackupStopReason, stop: Process.() -> Unit): Boolean =
        synchronized(lock) {
            if (stopReason != BackupStopReason.FORCE_STOPPED) {
                stopReason = reason
            }
            val process = activeProcess
            process?.let { stop.invoke(it) }
            process != null
        }
}
