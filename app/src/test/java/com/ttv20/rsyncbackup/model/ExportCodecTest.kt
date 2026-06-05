package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCodecTest {
    @Test
    fun exportOmitsPrivateMaterialAliases() {
        val state = configuredState().copy(
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
        val encoded = ExportCodec.encode(configuredState().toExportDocument())

        assertTrue(encoded.contains("\"targets\""))
        assertTrue(encoded.contains("\"targetId\""))
        assertFalse(encoded.contains("\"servers\""))
        assertFalse(encoded.contains("\"serverId\""))
    }

    @Test
    fun importRestoresNonSecretConfigurationOnly() {
        val original = configuredState().copy(
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

        val imported = AppState()
            .withImportedConfiguration(ExportCodec.decode(ExportCodec.encode(original.toExportDocument())))

        assertEquals(original.targets, imported.targets)
        assertEquals(original.profiles.map { it.id }, imported.profiles.map { it.id })
        assertEquals("ssh-ed25519 AAA public", imported.sshKeySettings.publicKey)
        assertNull(imported.sshKeySettings.privateKeySecretAlias)
        assertFalse(imported.tailscale.isConfigured)
        assertNull(imported.tailscale.stateSecretAlias)
    }

    @Test
    fun importMigratesLegacyGlobalSelectedSsidToProfileConstraint() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-06-03T00:00:00Z",
              "settings": {
                "phoneHostname": "android-phone",
                "logRetentionLimit": 20,
                "selectedSsid": "Home WiFi"
              },
              "tailscale": {
                "isConfigured": false,
                "nodeName": "android-rsync"
              },
              "targets": [
                {
                  "id": "target-home",
                  "name": "Home backup target",
                  "user": "ttv20",
                  "lanHost": "192.168.3.200",
                  "port": 22
                }
              ],
              "profiles": [
                {
                  "id": "profile-phone",
                  "name": "Phone shared storage",
                  "sourcePath": "/storage/emulated/0",
                  "targetId": "target-home",
                  "remotePath": "/mnt/backup/phone",
                  "targetMode": "LAN_ONLY",
                  "schedule": {},
                  "constraints": {
                    "selectedSsidOnly": true
                  },
                  "excludes": "cache/"
                }
              ],
              "trustedHostFingerprints": []
            }
        """.trimIndent()

        val document = ExportCodec.decode(legacyJson)

        assertEquals("Home WiFi", document.profiles.single().constraints.selectedSsid)
    }

    private fun configuredState(): AppState {
        val target = TargetRecord(
            id = "target-home",
            name = "Home backup target",
            user = "ttv20",
            lanHost = "192.168.3.200",
            port = 22,
        )
        val profile = BackupProfile(
            id = "profile-phone",
            name = "Phone shared storage",
            sourcePath = "/storage/emulated/0",
            targetId = target.id,
            remotePath = "/mnt/backup/phone",
            targetMode = TargetMode.LAN_ONLY,
            excludes = "cache/",
        )
        return AppState(
            targets = listOf(target),
            profiles = listOf(profile),
        )
    }
}
