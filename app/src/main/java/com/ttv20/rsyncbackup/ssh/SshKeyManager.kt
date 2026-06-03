package com.ttv20.rsyncbackup.ssh

import com.ttv20.rsyncbackup.storage.SecretStore
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64

data class GeneratedSshKey(
    val publicKey: String,
    val privateKeyAlias: String,
    val generatedAt: String,
)

class SshKeyManager(private val secretStore: SecretStore) {
    fun generateEd25519(alias: String = "ssh-private-key"): GeneratedSshKey {
        val keyPair = generateEd25519KeyPair()
        val publicKey = opensshEd25519PublicKey(keyPair.public.encoded)
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

    private fun generateEd25519KeyPair(): KeyPair {
        CryptoProviders.ensureModernBouncyCastleProvider()
        return KeyPairGenerator
            .getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
            .generateKeyPair()
    }

    private fun opensshEd25519PublicKey(spki: ByteArray): String {
        val rawKey = spki.takeLast(32).toByteArray()
        val type = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        val buffer = ByteBuffer.allocate(4 + type.size + 4 + rawKey.size)
        buffer.putInt(type.size)
        buffer.put(type)
        buffer.putInt(rawKey.size)
        buffer.put(rawKey)
        val encoded = Base64.getEncoder().encodeToString(buffer.array())
        return "ssh-ed25519 $encoded android-rsync-backup"
    }
}
