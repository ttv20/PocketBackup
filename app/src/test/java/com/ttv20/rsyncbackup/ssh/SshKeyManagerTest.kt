package com.ttv20.rsyncbackup.ssh

import com.ttv20.rsyncbackup.storage.SecretStore
import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshKeyManagerTest {
    @Test
    fun generateEd25519StoresPrivateKeyAndOpenSshPublicKey() {
        val secretStore = InMemorySecretStore()
        val generated = SshKeyManager(secretStore).generateEd25519("test-key")

        assertEquals("test-key", generated.privateKeyAlias)
        assertTrue(generated.publicKey.startsWith("ssh-ed25519 "))
        assertNotNull(generated.generatedAt)
        assertTrue((secretStore.get("test-key") ?: ByteArray(0)).isNotEmpty())

        val publicKeyParts = generated.publicKey.split(Regex("\\s+"))
        assertEquals("ssh-ed25519", publicKeyParts[0])
        val publicKeyBlob = ByteBuffer.wrap(Base64.getDecoder().decode(publicKeyParts[1]))
        assertArrayEquals("ssh-ed25519".toByteArray(Charsets.US_ASCII), publicKeyBlob.readBytes())
        assertEquals(32, publicKeyBlob.readBytes().size)
    }

    private fun ByteBuffer.readBytes(): ByteArray {
        val size = int
        return ByteArray(size).also { get(it) }
    }

    private class InMemorySecretStore : SecretStore {
        private val entries = mutableMapOf<String, ByteArray>()

        override fun put(alias: String, bytes: ByteArray) {
            entries[alias] = bytes
        }

        override fun get(alias: String): ByteArray? = entries[alias]

        override fun delete(alias: String) {
            entries.remove(alias)
        }
    }
}
