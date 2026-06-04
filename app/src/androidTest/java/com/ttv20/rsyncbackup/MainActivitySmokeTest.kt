package com.ttv20.rsyncbackup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshInstallOpensWelcomeAndSkipOpensDashboard() {
        val app = composeRule.activity.application as RsyncBackupApplication

        composeRule.onNodeWithText("Welcome").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-skip-button").performClick()
        composeRule.waitUntil(5_000) {
            app.repository.state.value.settings.onboardingSkippedAt != null
        }

        assertNotNull(app.repository.state.value.settings.onboardingSkippedAt)
        composeRule.onNodeWithTag("dashboard").assertIsDisplayed()
    }

    @Test
    fun canNavigateToCoreScreens() {
        openScreen("Profiles")
        assertAnyTextDisplayed("Profiles")
        openFirstProfileEditor()
        composeRule.onNodeWithText("Profile Edit").assertIsDisplayed()

        openScreen("Targets")
        assertAnyTextDisplayed("Targets")
        openFirstTargetEditor()
        composeRule.onNodeWithText("Target Edit").assertIsDisplayed()

        openScreen("SSH Access")
        composeRule.onNodeWithText("SSH Access").assertIsDisplayed()

        openScreen("Tailscale")
        composeRule.onNodeWithText("Tailscale Connection").assertIsDisplayed()

        openScreen("Logs")
        composeRule.onNodeWithText("Last 20").assertIsDisplayed()

        openScreen("Settings")
        composeRule.onNodeWithText("Settings and import/export").assertIsDisplayed()
    }

    @Test
    fun setupGuideExposesKeyOnboardingSteps() {
        val app = composeRule.activity.application as RsyncBackupApplication
        val initialTargetCount = app.repository.state.value.targets.size
        val initialProfileCount = app.repository.state.value.profiles.size

        openScreen("Settings")
        clickTag("settings-run-setup-guide")
        composeRule.onNodeWithText("Welcome").assertIsDisplayed()
        clickTag("onboarding-start-button")

        composeRule.onNodeWithText("Permissions").assertIsDisplayed()
        clickTag("onboarding-permissions-continue-button")

        composeRule.onNodeWithText("SSH Access").assertIsDisplayed()
        clickTag("ssh-generate-key-button")
        composeRule.waitUntil(10_000) {
            app.repository.state.value.sshKeySettings.publicKey?.startsWith("ssh-ed25519 ") == true
        }
        clickTag("ssh-public-key-copy-button")
        clickTag("onboarding-continue-button")

        composeRule.onNodeWithText("Tailscale Connection").assertIsDisplayed()
        clickTag("onboarding-continue-button")

        composeRule.onNodeWithText("New Target").assertIsDisplayed()
        assertVisibleText("onboarding-newtarget", "Scan LAN")
        assertVisibleText("onboarding-newtarget", "Install public key")
        clickTag("onboarding-save-target-button")
        assertEquals(initialTargetCount, app.repository.state.value.targets.size)

        composeRule.onNodeWithText("New Profile").assertIsDisplayed()
        assertVisibleText("onboarding-newprofile", "Selected target")
        clickTag("onboarding-save-profile-button")
        assertEquals(initialProfileCount, app.repository.state.value.profiles.size)

        composeRule.onNodeWithText("Review And Dry Run").assertIsDisplayed()
        clickTag("onboarding-dry-run-button")
        composeRule.onNodeWithText("Dry run result").assertIsDisplayed()
    }

    @Test
    fun settingsShowRequiredPermissionStates() {
        openScreen("Settings")

        composeRule.onNodeWithText("Permission setup/status").assertIsDisplayed()
        composeRule.onNodeWithText("All files access").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Battery optimization exemption").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Exact alarm access").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi/SSID access").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun setupScreensExposeEndToEndControls() {
        openScreen("Profiles")
        openFirstProfileEditor()
        assertVisibleText("profile-editor-scroll", "Profile Edit")
        assertVisibleText("profile-editor-scroll", "Source path")
        assertVisibleText("profile-editor-scroll", "Pick")
        assertVisibleText("profile-editor-scroll", "Remote path")
        assertVisibleText("profile-editor-scroll", "Add target")
        composeRule.onAllNodesWithText("Remote safety").assertCountEquals(0)
        assertVisibleText("profile-editor-scroll", "Delete remote files not present locally")
        assertVisibleText("profile-editor-scroll", "Advanced rsync args")
        assertVisibleText("profile-editor-scroll", "Command preview")

        openScreen("Targets")
        openFirstTargetEditor()
        assertVisibleText("target-editor-scroll", "Target Edit")
        assertVisibleText("target-editor-scroll", "Target readiness")
        assertVisibleText("target-editor-scroll", "Primary LAN host")
        assertVisibleText("target-editor-scroll", "Fallback Tailscale host")
        assertVisibleText("target-editor-scroll", "Default remote path")
        assertVisibleText("target-editor-scroll", "Trusted fingerprint")
        assertVisibleText("target-editor-scroll", "Scan LAN")
        assertVisibleText("target-editor-scroll", "One-time password setup")
        assertVisibleText("target-editor-scroll", "Install over LAN")

        openScreen("SSH Access")
        assertVisibleText("ssh-keys-scroll", "SSH Access")
        assertVisibleText("ssh-keys-scroll", "No SSH key yet")
        assertVisibleText("ssh-keys-scroll", "Generate app key")
        assertVisibleText("ssh-keys-scroll", "Use existing key")
        assertVisibleText("ssh-keys-scroll", "Private key")
        assertVisibleText("ssh-keys-scroll", "Passphrase")
        assertVisibleText("ssh-keys-scroll", "Store existing key")
        assertVisibleText("ssh-keys-scroll", "Key details")

        openScreen("Tailscale")
        assertVisibleText("tailscale-scroll", "Tailscale Connection")
        assertVisibleText("tailscale-scroll", "Not connected")
        assertVisibleText("tailscale-scroll", "Node name")
        assertVisibleText("tailscale-scroll", "Auth key")
        assertVisibleText("tailscale-scroll", "Connect Tailscale")
        assertVisibleText("tailscale-scroll", "Reset Tailscale")
        assertVisibleText("tailscale-scroll", "Target Tailscale host")
        assertVisibleText("tailscale-scroll", "Test route")
        assertVisibleText("tailscale-scroll", "Key expiry advice acknowledged")

        openScreen("Settings")
        assertVisibleText("settings-scroll", "Settings and import/export")
        assertVisibleText("settings-scroll", "Phone hostname")
        assertVisibleText("settings-scroll", "Selected SSID")
        assertVisibleText("settings-scroll", "Export")
        assertVisibleText("settings-scroll", "Import")
        assertVisibleText("settings-scroll", "Configuration JSON")
    }

    @Test
    fun addButtonsRevealCreatedProfileAndTarget() {
        val app = composeRule.activity.application as RsyncBackupApplication

        openScreen("Targets")
        val targetCount = app.repository.state.value.targets.size
        clickTag("targets-add-button")
        composeRule.onNodeWithText("New Target").assertIsDisplayed()
        clickTag("target-save-button")
        composeRule.waitUntil(5_000) {
            app.repository.state.value.targets.size == targetCount + 1
        }
        assertAnyTextDisplayed(app.repository.state.value.targets.last().name)

        openScreen("Profiles")
        val profileCount = app.repository.state.value.profiles.size
        clickTag("profiles-add-button")
        composeRule.onNodeWithText("New Profile").assertIsDisplayed()
        clickTag("profile-save-button")
        composeRule.waitUntil(5_000) {
            app.repository.state.value.profiles.size == profileCount + 1
        }
        assertAnyTextDisplayed(app.repository.state.value.profiles.last().name)
    }

    @Test
    fun profileEditorCanCreateTargetInline() {
        val app = composeRule.activity.application as RsyncBackupApplication

        openScreen("Profiles")
        openFirstProfileEditor()
        val targetCount = app.repository.state.value.targets.size
        clickTag("profile-add-target-button")
        composeRule.waitUntil(5_000) {
            app.repository.state.value.targets.size == targetCount + 1
        }

        val createdTarget = app.repository.state.value.targets.last()
        assertAnyTextDisplayed(createdTarget.name)
        assertTrue(app.repository.state.value.targets.any { it.id == createdTarget.id })
    }

    private fun assertVisibleText(scrollContainerTag: String, text: String) {
        try {
            composeRule.onAllNodesWithText(text)[0].performScrollTo().assertIsDisplayed()
            return
        } catch (error: AssertionError) {
            // Fall through to gesture scrolling for controls whose semantics node is
            // already present but outside the compact viewport.
        }
        var lastError: AssertionError? = null
        repeat(8) {
            try {
                composeRule.onAllNodesWithText(text)[0].assertIsDisplayed()
                return
            } catch (error: AssertionError) {
                lastError = error
                composeRule.onNodeWithTag(scrollContainerTag).performTouchInput { swipeUp() }
                composeRule.waitForIdle()
            }
        }
        throw lastError ?: AssertionError("Text not displayed: $text")
    }

    private fun assertAnyTextDisplayed(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        var lastError: AssertionError? = null
        nodes.indices.forEach { index ->
            try {
                composeRule.onAllNodesWithText(text)[index].assertIsDisplayed()
                return
            } catch (error: AssertionError) {
                lastError = error
            }
        }
        throw lastError ?: AssertionError("Text not found: $text")
    }

    private fun clickTag(tag: String) {
        try {
            composeRule.onNodeWithTag(tag).performScrollTo()
        } catch (error: AssertionError) {
            // Fixed-position controls do not expose ScrollTo.
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun openScreen(label: String) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.requestScreenForTest(label)
        }
        composeRule.waitForIdle()
    }

    private fun openFirstProfileEditor() {
        composeRule.onAllNodesWithText("Edit")[0].performClick()
        composeRule.waitForIdle()
    }

    private fun openFirstTargetEditor() {
        composeRule.onAllNodesWithText("Edit")[0].performClick()
        composeRule.waitForIdle()
    }
}
