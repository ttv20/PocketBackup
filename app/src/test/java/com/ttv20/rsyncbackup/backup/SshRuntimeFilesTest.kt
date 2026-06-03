package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.ServerRecord
import com.ttv20.rsyncbackup.model.TrustedHostFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshRuntimeFilesTest {
    @Test
    fun binaryGeneratedKeyIsWrittenAsPkcs8Pem() {
        val text = SshRuntimeFiles.privateKeyText(byteArrayOf(1, 2, 3, 4))

        assertTrue(text.startsWith("-----BEGIN PRIVATE KEY-----\n"))
        assertTrue(text.endsWith("-----END PRIVATE KEY-----\n"))
    }

    @Test
    fun customPemKeyIsPreserved() {
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----\n"

        assertEquals(pem, SshRuntimeFiles.privateKeyText(pem.toByteArray()))
    }

    @Test
    fun knownHostsUsesTrustedPublicKeyBlobAndPort() {
        val server = ServerRecord(
            id = "server",
            name = "Home",
            user = "ttv20",
            lanHost = "192.168.3.200",
            port = 2222,
            defaultRemotePath = "/mnt/backup/phone",
        )
        val text = SshRuntimeFiles.knownHostsText(
            server = server,
            trustedHostFingerprints = listOf(
                TrustedHostFingerprint(
                    id = "fingerprint",
                    serverId = "server",
                    hostnames = listOf("192.168.3.200"),
                    port = 2222,
                    algorithm = "ssh-ed25519",
                    fingerprint = "SHA256:abc",
                    publicKey = "ssh-ed25519 AAAATESTKEY comment",
                    confirmedAt = "2026-06-03T01:00:00Z",
                ),
            ),
        )

        assertEquals("[192.168.3.200]:2222 ssh-ed25519 AAAATESTKEY\n", text)
    }
}
