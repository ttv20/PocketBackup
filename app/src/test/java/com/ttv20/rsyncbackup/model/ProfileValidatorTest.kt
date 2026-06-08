package com.ttv20.rsyncbackup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test
    fun tailscaleModeRequiresTailscaleHost() {
        val state = configuredState()
        val profile = state.profiles.first().copy(targetMode = TargetMode.TAILSCALE_ONLY)

        val issues = ProfileValidator.validate(profile, state)

        assertTrue(issues.any { it.code == "tailscale_host_missing" && it.severity == Severity.ERROR })
        assertTrue(issues.any { it.code == "tailscale_not_configured" })
    }

    @Test
    fun invalidAdvancedArgsAreRejected() {
        val state = configuredState()
        val profile = state.profiles.first().copy(
            targetMode = TargetMode.LAN_ONLY,
            advancedArgs = "--exclude 'broken",
        )

        val issues = ProfileValidator.validate(profile, state)

        assertEquals("advanced_args_invalid", issues.single().code)
    }

    @Test
    fun selectedSsidConstraintRequiresProfileSsid() {
        val state = configuredState()
        val profile = state.profiles.first().copy(
            constraints = ConstraintSettings(selectedSsidOnly = true),
        )

        val issues = ProfileValidator.validate(profile, state)

        assertTrue(issues.any { it.code == "ssid_missing" })
    }

    @Test
    fun weeklyScheduleRequiresAtLeastOneDay() {
        val state = configuredState()
        val profile = state.profiles.first().copy(
            schedule = BackupSchedule(
                type = ScheduleType.WEEKLY,
                weeklyDays = emptyList(),
            ),
        )

        val issues = ProfileValidator.validate(profile, state)

        assertTrue(issues.any { it.code == "weekly_days_missing" && it.severity == Severity.ERROR })
    }

    @Test
    fun lanModeRequiresLanHost() {
        val state = configuredState()
        val target = state.targets.first().copy(lanHost = "", tailscaleHost = "home-tailnet")
        val profile = state.profiles.first().copy(targetMode = TargetMode.LAN_ONLY)

        val issues = ProfileValidator.validate(profile, state.copy(targets = listOf(target)))

        assertTrue(issues.any { it.code == "lan_host_missing" && it.severity == Severity.ERROR })
    }

    @Test
    fun tailscaleOnlyDoesNotRequireLanHost() {
        val state = configuredState()
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

    private fun configuredState(): AppState {
        val target = TargetRecord(
            id = "target-home",
            name = "Home backup target",
            user = "ttv20",
            lanHost = "192.168.3.200",
            port = 22,
            defaultRemotePath = "/mnt/backup/phone",
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
