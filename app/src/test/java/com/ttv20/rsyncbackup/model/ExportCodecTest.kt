package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCodecTest {
    @Test
    fun exportOmitsPrivateMaterialAliases() {
        val state = InitialData.appState("cache/").copy(
            sshKeySettings = GlobalSshKeySettings(
                publicKey = "ssh-ed25519 AAA public",
                privateKeySecretAlias = "secret-private-key",
                passphraseSecretAlias = "secret-passphrase",
            ),
            tailscale = TailscaleStateMetadata(
                isConfigured = true,
                nodeName = "phone-rsync",
                stateSecretAlias = "tailscale-state",
            ),
        )

        val encoded = ExportCodec.encode(state.toExportDocument(now = "2026-06-03T00:00:00Z"))

        assertTrue(encoded.contains("ssh-ed25519 AAA public"))
        assertFalse(encoded.contains("secret-private-key"))
        assertFalse(encoded.contains("secret-passphrase"))
        assertFalse(encoded.contains("tailscale-state"))
    }

    @Test
    fun exportUsesTargetSchemaNames() {
        val encoded = ExportCodec.encode(InitialData.appState("cache/").toExportDocument())

        assertTrue(encoded.contains("\"targets\""))
        assertTrue(encoded.contains("\"targetId\""))
        assertFalse(encoded.contains("\"servers\""))
        assertFalse(encoded.contains("\"serverId\""))
    }

    @Test
    fun importRestoresNonSecretConfigurationOnly() {
        val original = InitialData.appState("cache/").copy(
            sshKeySettings = GlobalSshKeySettings(
                publicKey = "ssh-ed25519 AAA public",
                privateKeySecretAlias = "secret-private-key",
            ),
            tailscale = TailscaleStateMetadata(
                isConfigured = true,
                nodeName = "phone-rsync",
                stateSecretAlias = "tailscale-state",
            ),
        )

        val imported = InitialData.appState("other/")
            .withImportedConfiguration(ExportCodec.decode(ExportCodec.encode(original.toExportDocument())))

        assertEquals(original.targets, imported.targets)
        assertEquals(original.profiles.map { it.id }, imported.profiles.map { it.id })
        assertEquals("ssh-ed25519 AAA public", imported.sshKeySettings.publicKey)
        assertNull(imported.sshKeySettings.privateKeySecretAlias)
        assertFalse(imported.tailscale.isConfigured)
        assertNull(imported.tailscale.stateSecretAlias)
    }
}
