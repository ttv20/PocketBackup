package com.ttv20.rsyncbackup.ssh

import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.publicKeyWithSshKeyName
import com.ttv20.rsyncbackup.storage.SecretStore
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

data class GeneratedSshKey(
    val publicKey: String,
    val privateKeyAlias: String,
    val generatedAt: String,
)

class SshKeyManager(private val secretStore: SecretStore) {
    fun generateEd25519(
        alias: String = "ssh-private-key",
        keyName: String = "android-phone-pocketbackup",
    ): GeneratedSshKey {
        val keyPair = generateEd25519KeyPair()
        val publicKey = opensshEd25519PublicKey(keyPair.public.encoded, keyName)
        secretStore.put(alias, keyPair.private.encoded)
        return GeneratedSshKey(
            publicKey = publicKey,
            privateKeyAlias = alias,
            generatedAt = Instant.now().toString(),
        )
    }

    fun storeCustomPrivateKey(alias: String, privateKey: String) {
        secretStore.put(alias, privateKey.toByteArray(Charsets.UTF_8))
    }

    fun extractPublicKeyFromPrivateKey(
        sshKeygenPath: String,
        filesDir: File,
        workDir: File,
        privateKey: String,
        passphrase: String = "",
    ): String {
        val keyFile = File(workDir, "import-private-key-${System.nanoTime()}").also {
            it.parentFile?.mkdirs()
            it.writeText(privateKey.trimEnd() + "\n")
            it.privateFilePermissions()
        }
        return try {
            val command = buildList {
                add(sshKeygenPath)
                add("-y")
                if (passphrase.isNotBlank()) {
                    add("-P")
                    add(passphrase)
                }
                add("-f")
                add(keyFile.absolutePath)
            }
            val processBuilder = ProcessBuilder(command)
                .directory(filesDir)
                .redirectErrorStream(true)
            NativeBinaryManager.configureProcessEnvironment(processBuilder, filesDir)
            val process = processBuilder.start()
            process.outputStream.close()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("Public key extraction timed out")
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            require(process.exitValue() == 0) {
                output.ifBlank { "Public key extraction failed" }
            }
            output
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("ssh-") }
                ?: error("No OpenSSH public key was returned")
        } finally {
            keyFile.delete()
        }
    }

    fun deleteConfiguredKey(settings: GlobalSshKeySettings) {
        listOfNotNull(settings.privateKeySecretAlias, settings.passphraseSecretAlias)
            .distinct()
            .forEach(secretStore::delete)
    }

    private fun generateEd25519KeyPair(): KeyPair {
        CryptoProviders.ensureModernBouncyCastleProvider()
        return KeyPairGenerator
            .getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
            .generateKeyPair()
    }

    private fun opensshEd25519PublicKey(spki: ByteArray, keyName: String): String {
        val rawKey = spki.takeLast(32).toByteArray()
        val type = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        val buffer = ByteBuffer.allocate(4 + type.size + 4 + rawKey.size)
        buffer.putInt(type.size)
        buffer.put(type)
        buffer.putInt(rawKey.size)
        buffer.put(rawKey)
        val encoded = Base64.getEncoder().encodeToString(buffer.array())
        return publicKeyWithSshKeyName("ssh-ed25519 $encoded", keyName)
    }
}

private fun File.privateFilePermissions() {
    setReadable(false, false)
    setWritable(false, false)
    setExecutable(false, false)
    setReadable(true, true)
    setWritable(true, true)
}
