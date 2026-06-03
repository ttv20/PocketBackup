package com.ttv20.rsyncbackup.ssh

import android.content.Context
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

data class ScannedHostKey(
    val hostname: String,
    val port: Int,
    val algorithm: String,
    val publicKey: String,
    val fingerprint: String,
)

class SshHostKeyScanner(private val context: Context) {
    fun scan(hostname: String, port: Int): ScannedHostKey = scanAll(hostname, port).first()

    fun scanAll(hostname: String, port: Int): List<ScannedHostKey> {
        val nativeInstall = NativeBinaryManager(context).ensureInstalled()
        val sshKeyscan = nativeInstall.requireTool("ssh-keyscan")

        val keyscanOutput = runCommand(
            command = listOf(
                sshKeyscan,
                "-T",
                "10",
                "-p",
                port.toString(),
                "-t",
                "ed25519,rsa",
                hostname,
            ),
        )
        return fingerprintScannedKeys(
            scannedKeys = SshHostKeyParser.parseKeyscanAll(hostname, port, keyscanOutput),
        )
    }

    fun scanAllOverTailscale(
        hostname: String,
        port: Int,
        user: String,
        tailscaleStateDir: File,
        tailscaleNodeName: String,
    ): List<ScannedHostKey> {
        val nativeInstall = NativeBinaryManager(context).ensureInstalled()
        val result = runCommandResult(
            command = listOf(
                nativeInstall.paths.tsnetHelper,
                "--state",
                tailscaleStateDir.absolutePath,
                "--hostname",
                tailscaleNodeName,
                "--timeout",
                "60s",
                "--ssh-keyscan",
                "--user",
                user,
                hostname,
                port.toString(),
            ),
        )
        require(result.exitCode == 0) {
            result.output.ifBlank { "No SSH host key returned for $hostname:$port over Tailscale" }
        }
        val scannedKeys = SshHostKeyParser.parseKnownHostsAll(hostname, port, result.output)
        return fingerprintScannedKeys(scannedKeys)
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    private fun fingerprintScannedKeys(scannedKeys: List<ScannedHostKey>): List<ScannedHostKey> =
        scannedKeys.map { scannedKey ->
            scannedKey.copy(fingerprint = SshHostKeyParser.fingerprintPublicKeyBlob(scannedKey.publicKey))
        }

    private fun runCommand(command: List<String>, acceptedExitCodes: Set<Int> = setOf(0)): String {
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
            .directory(context.filesDir)
        NativeBinaryManager.configureProcessEnvironment(processBuilder, context.filesDir)
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        require(exit in acceptedExitCodes) { output.ifBlank { "Command failed with exit $exit" } }
        return output
    }

    private fun runCommandResult(command: List<String>): CommandResult {
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
            .directory(context.filesDir)
        NativeBinaryManager.configureProcessEnvironment(processBuilder, context.filesDir)
        val process = processBuilder.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        return CommandResult(exit, output.trim())
    }
}

object SshHostKeyParser {
    fun parseKeyscan(hostname: String, port: Int, output: String): ScannedHostKey {
        return parseKeyscanAll(hostname, port, output).first()
    }

    fun parseKeyscanAll(hostname: String, port: Int, output: String): List<ScannedHostKey> {
        val scanned = parseHostKeyLines(hostname, port, output)
        require(scanned.isNotEmpty()) { "No SSH host key returned for $hostname:$port" }
        return scanned
    }

    fun parseKnownHostsAll(hostname: String, port: Int, output: String): List<ScannedHostKey> {
        val scanned = parseHostKeyLines(hostname, port, output)
        require(scanned.isNotEmpty()) { "No SSH host key returned for $hostname:$port" }
        return scanned
    }

    private fun parseHostKeyLines(hostname: String, port: Int, output: String): List<ScannedHostKey> {
        val scanned = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 3) return@mapNotNull null
                val algorithm = parts[1]
                val publicKey = parts[2]
                if (!isPublicKeyAlgorithm(algorithm) || publicKeyBlobBytes(publicKey) == null) return@mapNotNull null
                ScannedHostKey(
                    hostname = hostname,
                    port = port,
                    algorithm = algorithm,
                    publicKey = publicKey,
                    fingerprint = "",
                )
            }
            .distinctBy { it.algorithm to it.publicKey }
            .toList()
        return scanned
    }

    fun parseFingerprint(output: String): String {
        val parts = output.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?: error("No SSH fingerprint returned")
        require(parts.size >= 2 && parts[1].startsWith("SHA256:")) { "Invalid ssh-keygen fingerprint output" }
        return parts[1]
    }

    fun fingerprintPublicKeyBlob(publicKey: String): String {
        val keyData = requireNotNull(publicKeyBlobBytes(publicKey)) { "Invalid SSH public key blob" }
        val fingerprint = Base64.getEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(keyData),
        )
        return "SHA256:$fingerprint"
    }

    private fun publicKeyBlobBytes(publicKey: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(publicKey.trim()) }.getOrNull()

    private fun isPublicKeyAlgorithm(algorithm: String): Boolean =
        algorithm.startsWith("ssh-") ||
            algorithm.startsWith("ecdsa-") ||
            algorithm.startsWith("sk-")

    fun scannedHostKeyFromPublicKey(hostname: String, port: Int, key: PublicKey): ScannedHostKey {
        val keyType = KeyType.fromKey(key)
        require(keyType != KeyType.UNKNOWN) { "Unsupported SSH host key type: ${key.algorithm}" }
        val keyData = Buffer.PlainBuffer().also { buffer ->
            keyType.putPubKeyIntoBuffer(key, buffer)
        }.compactData
        val encodedKey = Base64.getEncoder().encodeToString(keyData)
        return ScannedHostKey(
            hostname = hostname,
            port = port,
            algorithm = keyType.toString(),
            publicKey = encodedKey,
            fingerprint = fingerprintPublicKeyBlob(encodedKey),
        )
    }
}

fun knownHostPattern(hostname: String, port: Int): String {
    if (port == 22 || hostname.startsWith("[")) return hostname
    return "[$hostname]:$port"
}
