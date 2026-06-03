package com.ttv20.rsyncbackup.ssh

import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.concurrent.thread

class TsnetProxySocketFactory(
    private val tsnetHelperPath: String,
    private val filesDir: File,
    private val tailscaleStateDir: File,
    private val tailscaleNodeName: String,
    private val remoteHost: String,
    private val remotePort: Int,
    private val connectTimeoutMillis: Int = 10_000,
) : SocketFactory(), Closeable {
    private val bridgeThreads = Collections.synchronizedList(mutableListOf<Thread>())

    override fun createSocket(): Socket = openSocket()

    override fun createSocket(host: String?, port: Int): Socket = openSocket()

    override fun createSocket(host: String?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        openSocket()

    override fun createSocket(host: InetAddress?, port: Int): Socket = openSocket()

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket = openSocket()

    override fun close() {
        bridgeThreads.toList().forEach { thread ->
            thread.join(2_000)
        }
    }

    private fun openSocket(): Socket {
        val loopback = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 1, loopback)
        val acceptThread = thread(name = "tsnet-proxy-accept", isDaemon = true) {
            runCatching {
                server.use {
                    val localSocket = it.accept()
                    it.close()
                    localSocket.use(::bridge)
                }
            }.also {
                bridgeThreads.remove(Thread.currentThread())
            }
        }
        bridgeThreads += acceptThread

        return Socket().also { socket ->
            try {
                socket.connect(InetSocketAddress(loopback, server.localPort), connectTimeoutMillis)
            } catch (error: Exception) {
                runCatching { server.close() }
                bridgeThreads.remove(acceptThread)
                throw error
            }
        }
    }

    private fun bridge(localSocket: Socket) {
        val processBuilder = ProcessBuilder(
            tsnetHelperPath,
            "--state",
            tailscaleStateDir.absolutePath,
            "--hostname",
            tailscaleNodeName,
            remoteHost,
            remotePort.toString(),
        ).directory(filesDir)
        NativeBinaryManager.configureProcessEnvironment(processBuilder, filesDir)

        val process = processBuilder.start()
        val stderr = ByteArrayOutputStream()
        val toProcess = thread(name = "tsnet-proxy-to-process", isDaemon = true) {
            runCatching { localSocket.getInputStream().copyTo(process.outputStream) }
            runCatching { process.outputStream.close() }
        }
        val fromProcess = thread(name = "tsnet-proxy-from-process", isDaemon = true) {
            runCatching { process.inputStream.copyTo(localSocket.getOutputStream()) }
            runCatching { localSocket.shutdownOutput() }
        }
        val errorPump = thread(name = "tsnet-proxy-stderr", isDaemon = true) {
            runCatching { process.errorStream.copyTo(stderr) }
        }

        try {
            while (!localSocket.isClosed) {
                if (process.waitFor(250, TimeUnit.MILLISECONDS)) break
                if (!toProcess.isAlive && !fromProcess.isAlive) break
            }
        } finally {
            runCatching { localSocket.close() }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            toProcess.join(1_000)
            fromProcess.join(1_000)
            errorPump.join(1_000)
        }
    }
}
