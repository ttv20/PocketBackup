package com.ttv20.rsyncbackup.backup

import java.io.Closeable
import java.io.File
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class TailscaleTcpForward private constructor(
    val host: String,
    val port: Int,
    val targetHost: String,
    private val process: Process,
    private val outputThread: Thread,
) : Closeable {
    override fun close() {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        outputThread.join(1_000)
    }

    companion object {
        fun start(
            tsnetHelperPath: String,
            filesDir: File,
            tailscaleStateDir: File,
            tailscaleNodeName: String,
            targetHost: String,
            targetPort: Int,
        ): TailscaleTcpForward {
            val processBuilder = ProcessBuilder(
                tsnetHelperPath,
                "--state",
                tailscaleStateDir.absolutePath,
                "--hostname",
                tailscaleNodeName,
                "--timeout",
                "60s",
                "--listen",
                "127.0.0.1:0",
                targetHost,
                targetPort.toString(),
            )
                .directory(filesDir)
                .redirectErrorStream(true)
            NativeBinaryManager.configureProcessEnvironment(processBuilder, filesDir)

            val process = processBuilder.start()
            process.outputStream.close()
            val lines = Collections.synchronizedList(mutableListOf<String>())
            val queue = LinkedBlockingQueue<String>()
            val outputThread = thread(name = "tailscale-forward-output", isDaemon = true) {
                runCatching {
                    process.inputStream.bufferedReader().useLines { sequence ->
                        sequence.forEach { line ->
                            lines += line
                            queue.offer(line)
                        }
                    }
                }
            }

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline) {
                val line = queue.poll(250, TimeUnit.MILLISECONDS)
                if (line != null && line.startsWith("listening ")) {
                    val endpoint = parseEndpoint(line.removePrefix("listening ").trim())
                    return TailscaleTcpForward(
                        host = endpoint.first,
                        port = endpoint.second,
                        targetHost = targetHost,
                        process = process,
                        outputThread = outputThread,
                    )
                }
                if (!process.isAlive) {
                    outputThread.join(1_000)
                    error(lines.joinToString("\n").ifBlank { "Tailscale forward failed to start" })
                }
            }

            process.destroyForcibly()
            outputThread.join(1_000)
            error(lines.joinToString("\n").ifBlank { "Tailscale forward did not report a local port" })
        }

        private fun parseEndpoint(endpoint: String): Pair<String, Int> {
            val separator = endpoint.lastIndexOf(':')
            require(separator > 0 && separator < endpoint.lastIndex - 1) { "Invalid Tailscale forward endpoint: $endpoint" }
            return endpoint.substring(0, separator).trim('[', ']') to endpoint.substring(separator + 1).toInt()
        }
    }
}
