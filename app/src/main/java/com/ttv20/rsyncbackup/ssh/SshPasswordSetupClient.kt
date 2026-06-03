package com.ttv20.rsyncbackup.ssh

import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.backup.RsyncCommandBuilder
import com.ttv20.rsyncbackup.backup.SshRuntimeFiles
import com.ttv20.rsyncbackup.model.ServerRecord
import com.ttv20.rsyncbackup.model.TrustedHostFingerprint
import net.schmizz.sshj.SSHClient
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

data class SshPasswordSetupResult(
    val exitStatus: Int,
    val output: String,
) {
    val isSuccess: Boolean get() = exitStatus == 0
}

class SshPasswordSetupClient {
    fun installPublicKey(
        server: ServerRecord,
        trustedHostFingerprints: List<TrustedHostFingerprint>,
        publicKey: String,
        password: String,
        workDir: File,
        hostname: String = server.lanHost,
        socketFactory: SocketFactory? = null,
    ): SshPasswordSetupResult {
        require(publicKey.isNotBlank()) { "No SSH public key is configured" }
        require(password.isNotBlank()) { "Password is required for one-time setup" }

        val knownHostsText = SshRuntimeFiles.knownHostsText(server, trustedHostFingerprints)
        require(knownHostsText.isNotBlank()) { "Scan and trust the server host key before password setup" }

        val knownHostsFile = File(workDir, "setup-known-hosts-${System.nanoTime()}").also {
            it.parentFile?.mkdirs()
            it.writeText(knownHostsText)
        }
        return try {
            CryptoProviders.ensureModernBouncyCastleProvider()
            SSHClient().use { ssh ->
                socketFactory?.let { ssh.setSocketFactory(it) }
                ssh.loadKnownHosts(knownHostsFile)
                ssh.connect(hostname, server.port)
                ssh.authPassword(server.user, password)
                ssh.startSession().use { session ->
                    val command = session.exec(authorizedKeysInstallScript(publicKey))
                    val stdout = command.inputStream.bufferedReader().readText()
                    val stderr = command.errorStream.bufferedReader().readText()
                    command.join(30, TimeUnit.SECONDS)
                    SshPasswordSetupResult(
                        exitStatus = command.exitStatus ?: -1,
                        output = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n"),
                    )
                }
            }
        } finally {
            (socketFactory as? Closeable)?.close()
            knownHostsFile.delete()
        }
    }

    fun installPublicKeyWithNativeSsh(
        server: ServerRecord,
        trustedHostFingerprints: List<TrustedHostFingerprint>,
        publicKey: String,
        password: String,
        workDir: File,
        filesDir: File,
        tsnetHelperPath: String,
        tailscaleStateDir: File,
        tailscaleNodeName: String,
        hostname: String,
    ): SshPasswordSetupResult {
        require(publicKey.isNotBlank()) { "No SSH public key is configured" }
        require(password.isNotBlank()) { "Password is required for one-time setup" }

        val knownHostsText = SshRuntimeFiles.knownHostsText(server, trustedHostFingerprints)
        require(knownHostsText.isNotBlank()) { "Scan and trust the server host key before password setup" }

        val suffix = System.nanoTime().toString()
        val knownHostsFile = File(workDir, "setup-known-hosts-$suffix").also {
            it.parentFile?.mkdirs()
            it.writeText(knownHostsText)
            it.privateFilePermissions()
        }
        val passwordFile = File(workDir, "setup-password-$suffix").also {
            it.writeText(password)
            it.privateFilePermissions()
        }
        val publicKeyFile = File(workDir, "setup-public-key-$suffix").also {
            it.writeText(publicKey.trim())
            it.privateFilePermissions()
        }

        return try {
            runTsnetSshSetup(
                command = listOf(
                    tsnetHelperPath,
                    "--state",
                    tailscaleStateDir.absolutePath,
                    "--hostname",
                    tailscaleNodeName,
                    "--timeout",
                    "60s",
                    "--ssh-install-authorized-key",
                    "--user",
                    server.user,
                    "--password-file",
                    passwordFile.absolutePath,
                    "--public-key-file",
                    publicKeyFile.absolutePath,
                    "--known-hosts",
                    knownHostsFile.absolutePath,
                    hostname,
                    server.port.toString(),
                ),
                filesDir = filesDir,
            )
        } finally {
            knownHostsFile.delete()
            passwordFile.delete()
            publicKeyFile.delete()
        }
    }

    private fun runTsnetSshSetup(command: List<String>, filesDir: File): SshPasswordSetupResult {
        val processBuilder = ProcessBuilder(command)
            .directory(filesDir)
            .redirectErrorStream(true)
        NativeBinaryManager.configureProcessEnvironment(processBuilder, filesDir)

        val process = processBuilder.start()
        process.outputStream.close()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            val output = process.inputStream.bufferedReader().readText().trim()
            return SshPasswordSetupResult(
                exitStatus = -1,
                output = output.ifBlank { "Password setup timed out" },
            )
        }
        val output = process.inputStream.bufferedReader().readText().trim()
        return SshPasswordSetupResult(
            exitStatus = process.exitValue(),
            output = output,
        )
    }

    companion object {
        fun authorizedKeysInstallScript(publicKey: String): String {
            val quotedKey = RsyncCommandBuilder.shellQuote(publicKey.trim())
            return """
                set -eu
                umask 077
                mkdir -p "${'$'}HOME/.ssh"
                touch "${'$'}HOME/.ssh/authorized_keys"
                if ! grep -qxF -- $quotedKey "${'$'}HOME/.ssh/authorized_keys"; then
                  printf '%s\n' $quotedKey >> "${'$'}HOME/.ssh/authorized_keys"
                fi
                chmod 700 "${'$'}HOME/.ssh"
                chmod 600 "${'$'}HOME/.ssh/authorized_keys"
            """.trimIndent()
        }

    }
}

private fun File.privateFilePermissions(executable: Boolean = false) {
    setReadable(false, false)
    setWritable(false, false)
    setExecutable(false, false)
    setReadable(true, true)
    setWritable(true, true)
    if (executable) setExecutable(true, true)
}
