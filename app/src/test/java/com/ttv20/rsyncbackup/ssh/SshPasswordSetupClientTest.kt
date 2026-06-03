package com.ttv20.rsyncbackup.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshPasswordSetupClientTest {
    @Test
    fun installScriptAppendsPublicKeyIdempotently() {
        val script = SshPasswordSetupClient.authorizedKeysInstallScript(
            "ssh-ed25519 AAAA test@example",
        )

        assertTrue(script.contains("mkdir -p \"${'$'}HOME/.ssh\""))
        assertTrue(script.contains("grep -qxF"))
        assertTrue(script.contains("authorized_keys"))
        assertTrue(script.contains("chmod 700 \"${'$'}HOME/.ssh\""))
        assertTrue(script.contains("chmod 600 \"${'$'}HOME/.ssh/authorized_keys\""))
        assertFalse(script.contains("password"))
    }
}
