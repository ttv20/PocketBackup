package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test
    fun tailscaleModeRequiresTailscaleHost() {
        val state = InitialData.appState("cache/")
        val profile = state.profiles.first().copy(targetMode = TargetMode.TAILSCALE_ONLY)

        val issues = ProfileValidator.validate(profile, state)

        assertTrue(issues.any { it.code == "tailscale_host_missing" && it.severity == Severity.ERROR })
        assertTrue(issues.any { it.code == "tailscale_not_configured" })
    }

    @Test
    fun invalidAdvancedArgsAreRejected() {
        val state = InitialData.appState("cache/")
        val profile = state.profiles.first().copy(
            targetMode = TargetMode.LAN_ONLY,
            advancedArgs = "--exclude 'broken",
        )

        val issues = ProfileValidator.validate(profile, state)

        assertEquals("advanced_args_invalid", issues.single().code)
    }

    @Test
    fun lanModeRequiresLanHost() {
        val state = InitialData.appState("cache/")
        val target = state.targets.first().copy(lanHost = "", tailscaleHost = "home-tailnet")
        val profile = state.profiles.first().copy(targetMode = TargetMode.LAN_ONLY)

        val issues = ProfileValidator.validate(profile, state.copy(targets = listOf(target)))

        assertTrue(issues.any { it.code == "lan_host_missing" && it.severity == Severity.ERROR })
    }

    @Test
    fun tailscaleOnlyDoesNotRequireLanHost() {
        val state = InitialData.appState("cache/")
        val target = state.targets.first().copy(lanHost = "", tailscaleHost = "home-tailnet")
        val profile = state.profiles.first().copy(targetMode = TargetMode.TAILSCALE_ONLY)

        val issues = ProfileValidator.validate(
            profile,
            state.copy(
                tailscale = state.tailscale.copy(isConfigured = true),
                targets = listOf(target),
            ),
        )

        assertFalse(issues.any { it.code == "lan_host_missing" })
        assertFalse(issues.any { it.severity == Severity.ERROR })
    }

    @Test
    fun defaultRemotePathDoesNotWarnOnSave() {
        val state = InitialData.appState("cache/")
        val profile = state.profiles.first()

        val warnings = ProfileValidator.saveWarnings(profile, state)

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun broadDeleteEnabledRemotePathWarnsOnSave() {
        val state = InitialData.appState("cache/")
        val profile = state.profiles.first().copy(
            targetMode = TargetMode.LAN_ONLY,
            remotePath = "/mnt",
        )

        val warnings = ProfileValidator.saveWarnings(profile, state)

        assertTrue(warnings.any { it.code == "remote_path_broad_delete" })
    }
}
