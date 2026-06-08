package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TailscaleNodeNameTest {
    @Test
    fun suggestedNodeNameUsesHostnameSafeSuffix() {
        assertEquals("pixel-9-pro-rsync", suggestedTailscaleNodeName(" Pixel 9 Pro "))
        assertEquals("tom-s-phone-rsync", suggestedTailscaleNodeName("Tom's Phone"))
        assertEquals("android-phone-rsync", suggestedTailscaleNodeName("   "))
    }

    @Test
    fun suggestedSshKeyNameUsesProductSuffix() {
        assertEquals("pixel-9-pro-pocket-backup", suggestedSshKeyName(" Pixel 9 Pro "))
        assertEquals("tom-s-phone-pocket-backup", suggestedSshKeyName("Tom's Phone"))
        assertEquals("android-phone-pocket-backup", suggestedSshKeyName("   "))
    }

    @Test
    fun settingsUpdateRefreshesGeneratedSshKeyName() {
        val state = InitialData.appState("cache/").copy(
            sshKeySettings = GlobalSshKeySettings(
                publicKey = "ssh-ed25519 AAAATEST old-name",
                privateKeySecretAlias = "ssh-private-key",
                generatedAt = "2026-06-08T00:00:00Z",
            ),
        )

        val updated = state.withUpdatedSettings(state.settings.copy(phoneHostname = "Pixel 9 Pro"))

        assertEquals("ssh-ed25519 AAAATEST pixel-9-pro-pocket-backup", updated.sshKeySettings.publicKey)
    }

    @Test
    fun settingsUpdatePreservesCustomSshKeyName() {
        val state = InitialData.appState("cache/").copy(
            sshKeySettings = GlobalSshKeySettings(
                publicKey = "ssh-ed25519 AAAATEST custom@example",
                privateKeySecretAlias = "custom-ssh-private-key",
                customPrivateKeyLabel = "Custom key",
            ),
        )

        val updated = state.withUpdatedSettings(state.settings.copy(phoneHostname = "Pixel 9 Pro"))

        assertEquals("ssh-ed25519 AAAATEST custom@example", updated.sshKeySettings.publicKey)
    }

    @Test
    fun detectedPhoneHostnameUpdatesDefaultTailscaleNodeName() {
        val state = InitialData.appState("cache/")

        val updated = state.withDetectedPhoneHostname("Pixel 9 Pro")

        assertEquals("Pixel 9 Pro", updated.settings.phoneHostname)
        assertEquals("pixel-9-pro-rsync", updated.tailscale.nodeName)
        assertEquals("pixel-9-pro-rsync", effectiveTailscaleNodeName(updated))
    }

    @Test
    fun settingsUpdateRefreshesPreviouslySuggestedTailscaleNodeName() {
        val state = InitialData.appState("cache/")
            .withDetectedPhoneHostname("Pixel 8")

        val updated = state.withUpdatedSettings(state.settings.copy(phoneHostname = "Pixel 9"))

        assertEquals("pixel-9-rsync", updated.tailscale.nodeName)
    }

    @Test
    fun detectedPhoneHostnamePreservesCustomOrConfiguredNodeName() {
        val custom = InitialData.appState("cache/").copy(
            tailscale = TailscaleStateMetadata(nodeName = "manual-node"),
        ).withDetectedPhoneHostname("Pixel 9")
        val configured = InitialData.appState("cache/").copy(
            tailscale = TailscaleStateMetadata(isConfigured = true, nodeName = "connected-node"),
        ).withDetectedPhoneHostname("Pixel 9")

        assertEquals("manual-node", custom.tailscale.nodeName)
        assertEquals("connected-node", configured.tailscale.nodeName)
    }
}
