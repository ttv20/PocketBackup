package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun exportOmitsDeviceLocalSettings() {
        val state = configuredState().copy(
            settings = GlobalSettings(
                phoneHostname = "personal-phone",
                logRetentionLimit = 37,
                exactAlarmFallbackEnabled = false,
                allFilesAccessRequested = false,
                batteryOptimizationExemptionRequested = false,
                themePreference = ThemePreference.DARK,
                onboardingCompletedAt = "2026-06-03T00:00:00Z",
                onboardingSkippedAt = "2026-06-03T00:01:00Z",
                onboardingLastStep = "Review",
            ),
        )

        val encoded = ExportCodec.encode(state.toExportDocument(now = "2026-06-03T00:00:00Z"))

        assertTrue(encoded.contains("\"logRetentionLimit\": 37"))
        assertFalse(encoded.contains("phoneHostname"))
        assertFalse(encoded.contains("personal-phone"))
        assertFalse(encoded.contains("exactAlarmFallbackEnabled"))
        assertFalse(encoded.contains("allFilesAccessRequested"))
        assertFalse(encoded.contains("batteryOptimizationExemptionRequested"))
        assertFalse(encoded.contains("themePreference"))
        assertFalse(encoded.contains("onboardingCompletedAt"))
        assertFalse(encoded.contains("onboardingSkippedAt"))
        assertFalse(encoded.contains("onboardingLastStep"))
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
    fun exportScrubsTargetSshKeyOverrides() {
        val state = configuredState().let { configured ->
            configured.copy(
                targets = listOf(
                    configured.targets.single().copy(
                        sshKeySettings = GlobalSshKeySettings(
                            publicKey = "ssh-ed25519 AAA target-public",
                            privateKeySecretAlias = "target-private-key",
                            passphraseSecretAlias = "target-passphrase",
                        ),
                    ),
                ),
            )
        }

        val document = state.toExportDocument()
        val encoded = ExportCodec.encode(document)

        assertNull(document.targets.single().sshKeySettings)
        assertFalse(encoded.contains("target-private-key"))
        assertFalse(encoded.contains("target-passphrase"))
        assertFalse(encoded.contains("ssh-ed25519 AAA target-public"))
    }

    @Test
    fun privateKeyExportIsEncryptedAndPasswordProtected() {
        val payload = SshPrivateKeyExportPayload(
            publicKey = "ssh-ed25519 AAA public",
            privateKeyPem = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n",
            passphrase = "key-passphrase",
        )
        val encrypted = SshPrivateKeyExportCrypto.encrypt(
            payload = payload,
            password = "export-password",
            iterations = 100_000,
        )
        val encoded = ExportCodec.encode(
            configuredState().toExportDocument(
                now = "2026-06-03T00:00:00Z",
                sshPrivateKey = encrypted,
            ),
        )

        assertTrue(encoded.contains("\"sshPrivateKey\""))
        assertFalse(encoded.contains(payload.privateKeyPem))
        assertFalse(encoded.contains("key-passphrase"))

        val decoded = ExportCodec.decode(encoded)
        assertNotNull(decoded.sshPrivateKey)
        val decodedPrivateKey = requireNotNull(decoded.sshPrivateKey)
        assertEquals(payload, SshPrivateKeyExportCrypto.decrypt(decodedPrivateKey, "export-password"))
        val error = assertThrows(IllegalArgumentException::class.java) {
            SshPrivateKeyExportCrypto.decrypt(decodedPrivateKey, "wrong-password")
        }
        assertTrue(error.message?.contains("incorrect") == true)
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
    fun importKeepsDeviceLocalSettings() {
        val current = AppState(
            settings = GlobalSettings(
                phoneHostname = "current-phone",
                logRetentionLimit = 5,
                exactAlarmFallbackEnabled = false,
                allFilesAccessRequested = false,
                batteryOptimizationExemptionRequested = false,
                themePreference = ThemePreference.DARK,
                onboardingCompletedAt = "2026-06-04T00:00:00Z",
            ),
        )
        val exported = configuredState()
            .copy(
                settings = GlobalSettings(
                    phoneHostname = "exported-phone",
                    logRetentionLimit = 42,
                    allFilesAccessRequested = true,
                    batteryOptimizationExemptionRequested = true,
                    themePreference = ThemePreference.LIGHT,
                    onboardingCompletedAt = "2026-06-03T00:00:00Z",
                ),
            )
            .toExportDocument()

        val imported = current.withImportedConfiguration(exported)

        assertEquals("current-phone", imported.settings.phoneHostname)
        assertEquals(42, imported.settings.logRetentionLimit)
        assertFalse(imported.settings.exactAlarmFallbackEnabled)
        assertFalse(imported.settings.allFilesAccessRequested)
        assertFalse(imported.settings.batteryOptimizationExemptionRequested)
        assertEquals(ThemePreference.DARK, imported.settings.themePreference)
        assertEquals("2026-06-04T00:00:00Z", imported.settings.onboardingCompletedAt)
    }

    @Test
    fun exportAndImportPreserveDryRunEstimatePreference() {
        val original = configuredState().copy(
            profiles = configuredState().profiles.map { it.copy(dryRunBeforeBackup = false) },
        )

        val imported = AppState()
            .withImportedConfiguration(ExportCodec.decode(ExportCodec.encode(original.toExportDocument())))

        assertFalse(imported.profiles.single().dryRunBeforeBackup)
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
