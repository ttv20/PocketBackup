package com.ttv20.rsyncbackup.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator

class SshHostKeyParserTest {
    @Test
    fun parsesFirstHostKeyFromKeyscanOutput() {
        val scanned = SshHostKeyParser.parseKeyscan(
            hostname = "192.168.3.200",
            port = 2222,
            output = """
                # 192.168.3.200:2222 SSH-2.0-OpenSSH
                [192.168.3.200]:2222 ssh-ed25519 AAAATESTKEY
            """.trimIndent(),
        )

        assertEquals("192.168.3.200", scanned.hostname)
        assertEquals(2222, scanned.port)
        assertEquals("ssh-ed25519", scanned.algorithm)
        assertEquals("AAAATESTKEY", scanned.publicKey)
    }

    @Test
    fun parsesAllHostKeysFromKeyscanOutput() {
        val scanned = SshHostKeyParser.parseKeyscanAll(
            hostname = "192.168.3.200",
            port = 2222,
            output = """
                # 192.168.3.200:2222 SSH-2.0-OpenSSH
                [192.168.3.200]:2222 ssh-ed25519 AAAAED25519
                [192.168.3.200]:2222 ssh-rsa AAAARSA
            """.trimIndent(),
        )

        assertEquals(listOf("ssh-ed25519", "ssh-rsa"), scanned.map { it.algorithm })
        assertEquals(listOf("AAAAED25519", "AAAARSA"), scanned.map { it.publicKey })
    }

    @Test
    fun parsesKnownHostsWrittenBySshAcceptNew() {
        val scanned = SshHostKeyParser.parseKnownHostsAll(
            hostname = "workstation.tailnet",
            port = 22,
            output = """
                workstation.tailnet ssh-ed25519 AAAAED25519
            """.trimIndent(),
        )

        assertEquals("workstation.tailnet", scanned.single().hostname)
        assertEquals(22, scanned.single().port)
        assertEquals("ssh-ed25519", scanned.single().algorithm)
        assertEquals("AAAAED25519", scanned.single().publicKey)
    }

    @Test
    fun parsesSha256FingerprintFromSshKeygenOutput() {
        val fingerprint = SshHostKeyParser.parseFingerprint(
            "256 SHA256:abc123 no comment (ED25519)\n",
        )

        assertEquals("SHA256:abc123", fingerprint)
    }

    @Test
    fun formatsScannedHostKeyFromSshjPublicKey() {
        val keyPair = KeyPairGenerator.getInstance("RSA").also { it.initialize(1024) }
            .generateKeyPair()

        val scanned = SshHostKeyParser.scannedHostKeyFromPublicKey(
            hostname = "bardugo-home",
            port = 22,
            key = keyPair.public,
        )

        assertEquals("bardugo-home", scanned.hostname)
        assertEquals(22, scanned.port)
        assertEquals("ssh-rsa", scanned.algorithm)
        assertTrue(scanned.publicKey.isNotBlank())
        assertTrue(scanned.fingerprint.startsWith("SHA256:"))
        assertEquals(scanned.fingerprint, SshHostKeyParser.fingerprintPublicKeyBlob(scanned.publicKey))
    }

    @Test
    fun formatsKnownHostPatternForNonStandardPort() {
        assertEquals("[example.test]:2222", knownHostPattern("example.test", 2222))
        assertEquals("example.test", knownHostPattern("example.test", 22))
    }
}
