package com.ttv20.rsyncbackup.tailscale

import android.content.Context
import android.util.Log
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.storage.SecretStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class TailscaleCommandResult(
    val success: Boolean,
    val exitCode: Int?,
    val output: String,
    val stateSecretAlias: String? = null,
)

class TailscaleManager(
    private val context: Context,
    private val secretStore: SecretStore,
    private val nativeBinaryManager: NativeBinaryManager = NativeBinaryManager(context),
    private val timeoutSeconds: Long = 60,
) {
    fun authenticate(nodeName: String, authKey: String): TailscaleCommandResult {
        clearPlainState()
        stateDir().mkdirs()
        val result = runHelper(
            args = listOf(
                "--state",
                stateDir().absolutePath,
                "--hostname",
                nodeName,
                "--timeout",
                "${timeoutSeconds}s",
                "--up",
            ),
            authKey = authKey,
        )
        return if (result.success) {
            persistState(STATE_SECRET_ALIAS)
            clearPlainState()
            result.copy(stateSecretAlias = STATE_SECRET_ALIAS)
        } else {
            clearPlainState()
            result
        }
    }

    fun testReachability(
        nodeName: String,
        stateSecretAlias: String?,
        host: String,
        port: Int,
    ): TailscaleCommandResult {
        runCatching { restoreState(stateSecretAlias) }
            .onFailure { error ->
                clearPlainState()
                return TailscaleCommandResult(
                    success = false,
                    exitCode = null,
                    output = "Tailscale state restore failed: ${error.message}",
                )
            }
        val result = runHelper(
            args = listOf(
                "--state",
                stateDir().absolutePath,
                "--hostname",
                nodeName,
                "--timeout",
                "${timeoutSeconds}s",
                "--check",
                host,
                port.toString(),
            ),
        )
        if (result.success) {
            persistState(stateSecretAlias ?: STATE_SECRET_ALIAS)
        }
        clearPlainState()
        return result
    }

    fun restoreState(stateSecretAlias: String?) {
        val alias = requireNotNull(stateSecretAlias) { "Tailscale state is not configured" }
        val bytes = requireNotNull(secretStore.get(alias)) { "Tailscale state is missing" }
        restoreDirectory(bytes, stateDir())
    }

    fun persistState(alias: String = STATE_SECRET_ALIAS) {
        secretStore.put(alias, archiveDirectory(stateDir()))
    }

    fun clearPlainState() {
        stateDir().deleteRecursively()
    }

    fun reset(stateSecretAlias: String?) {
        clearPlainState()
        secretStore.delete(stateSecretAlias ?: STATE_SECRET_ALIAS)
    }

    fun <T> withRestoredState(stateSecretAlias: String?, block: (File) -> T): T {
        restoreState(stateSecretAlias)
        return try {
            val result = block(stateDir())
            persistState(stateSecretAlias ?: STATE_SECRET_ALIAS)
            result
        } finally {
            clearPlainState()
        }
    }

    private fun stateDir(): File = File(context.filesDir, "tailscale-state")

    private fun runHelper(args: List<String>, authKey: String? = null): TailscaleCommandResult {
        val nativeInstall = nativeBinaryManager.ensureInstalled()
        if (!nativeInstall.isComplete) {
            return TailscaleCommandResult(
                success = false,
                exitCode = null,
                output = "Missing native binaries: ${nativeInstall.missing.joinToString()}",
            )
        }

        Log.i(TAG, "Running tsnet helper: ${args.joinToString(" ")}")
        val processBuilder = ProcessBuilder(listOf(nativeInstall.paths.tsnetHelper) + args)
            .directory(context.filesDir)
            .redirectErrorStream(true)
        NativeBinaryManager.configureProcessEnvironment(processBuilder, context.filesDir)
        if (!authKey.isNullOrBlank()) {
            processBuilder.environment()["TS_AUTHKEY"] = authKey
        }

        val process = runCatching { processBuilder.start() }
            .getOrElse { error ->
                return TailscaleCommandResult(
                    success = false,
                    exitCode = null,
                    output = "Tailscale helper failed to start: ${error.message}",
                )
            }
        process.outputStream.close()
        val finished = process.waitFor(timeoutSeconds + 10, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            Log.w(TAG, "Tailscale helper timed out: ${args.joinToString(" ")}")
            return TailscaleCommandResult(
                success = false,
                exitCode = null,
                output = "Tailscale helper timed out",
            )
        }
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.exitValue()
        if (exitCode == 0) {
            Log.i(TAG, "Tailscale helper succeeded: ${output.take(MAX_LOG_OUTPUT)}")
        } else {
            Log.w(TAG, "Tailscale helper failed exit=$exitCode: ${output.take(MAX_LOG_OUTPUT)}")
        }
        return TailscaleCommandResult(
            success = exitCode == 0,
            exitCode = exitCode,
            output = output,
        )
    }

    companion object {
        private const val TAG = "PocketBackupTailscale"
        private const val MAX_LOG_OUTPUT = 4000
        const val STATE_SECRET_ALIAS = "tailscale-state"

        fun archiveDirectory(directory: File): ByteArray {
            val root = directory.canonicalFile
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                if (!root.exists()) return@use
                root.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val relativePath = root.toPath()
                            .relativize(file.canonicalFile.toPath())
                            .toString()
                            .replace(File.separatorChar, '/')
                        zip.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            return bytes.toByteArray()
        }

        fun restoreDirectory(bytes: ByteArray, directory: File) {
            val root = directory.canonicalFile
            root.deleteRecursively()
            root.mkdirs()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(root, entry.name).canonicalFile
                    require(output.path == root.path || output.path.startsWith(root.path + File.separator)) {
                        "Invalid Tailscale state archive entry"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }
}
