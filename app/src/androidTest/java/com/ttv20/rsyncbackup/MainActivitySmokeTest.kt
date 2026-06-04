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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensPermissionSetupWhenRequiredPermissionIsMissing() {
        composeRule.onNodeWithText("Permission setup/status").assertIsDisplayed()
    }

    @Test
    fun canNavigateToCoreScreens() {
        openScreen("Profiles")
        assertAnyTextDisplayed("Profiles")
        openFirstProfileEditor()
        composeRule.onNodeWithText("Profile Edit").assertIsDisplayed()

        openScreen("Servers")
        assertAnyTextDisplayed("Servers")
        openFirstServerEditor()
        composeRule.onNodeWithText("Server Edit").assertIsDisplayed()

        openScreen("SSH keys")
        composeRule.onNodeWithText("SSH key management").assertIsDisplayed()

        openScreen("Tailscale")
        composeRule.onNodeWithText("Tailscale setup/status/test/reset").assertIsDisplayed()

        openScreen("Logs")
        composeRule.onNodeWithText("Last 20").assertIsDisplayed()

        openScreen("Settings")
        composeRule.onNodeWithText("Settings and import/export").assertIsDisplayed()
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
        assertVisibleText("profile-editor-scroll", "Add server")
        composeRule.onAllNodesWithText("Remote safety").assertCountEquals(0)
        assertVisibleText("profile-editor-scroll", "Delete remote files not present locally")
        assertVisibleText("profile-editor-scroll", "Advanced rsync args")
        assertVisibleText("profile-editor-scroll", "Command preview")

        openScreen("Servers")
        openFirstServerEditor()
        assertVisibleText("server-editor-scroll", "Server Edit")
        assertVisibleText("server-editor-scroll", "Primary LAN host")
        assertVisibleText("server-editor-scroll", "Fallback Tailscale host")
        assertVisibleText("server-editor-scroll", "Default remote path")
        assertVisibleText("server-editor-scroll", "Trusted fingerprint")
        assertVisibleText("server-editor-scroll", "Scan LAN")
        assertVisibleText("server-editor-scroll", "One-time password setup")
        assertVisibleText("server-editor-scroll", "Install over LAN")

        openScreen("SSH keys")
        assertVisibleText("ssh-keys-scroll", "SSH key management")
        assertVisibleText("ssh-keys-scroll", "Generated key")
        assertVisibleText("ssh-keys-scroll", "Generate")
        assertVisibleText("ssh-keys-scroll", "Custom private key")
        assertVisibleText("ssh-keys-scroll", "Private key")
        assertVisibleText("ssh-keys-scroll", "Passphrase")
        assertVisibleText("ssh-keys-scroll", "Store")

        openScreen("Tailscale")
        assertVisibleText("tailscale-scroll", "Tailscale setup/status/test/reset")
        assertVisibleText("tailscale-scroll", "Node name")
        assertVisibleText("tailscale-scroll", "Auth key")
        assertVisibleText("tailscale-scroll", "Authenticate")
        assertVisibleText("tailscale-scroll", "Reset")
        assertVisibleText("tailscale-scroll", "Test host")
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
    fun addButtonsRevealCreatedProfileAndServer() {
        val app = composeRule.activity.application as RsyncBackupApplication

        openScreen("Servers")
        val serverCount = app.repository.state.value.servers.size
        clickTag("servers-add-button")
        composeRule.onNodeWithText("New Server").assertIsDisplayed()
        clickTag("server-save-button")
        composeRule.waitUntil(5_000) {
            app.repository.state.value.servers.size == serverCount + 1
        }
        assertAnyTextDisplayed(app.repository.state.value.servers.last().name)

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
    fun profileEditorCanCreateServerInline() {
        val app = composeRule.activity.application as RsyncBackupApplication

        openScreen("Profiles")
        openFirstProfileEditor()
        val serverCount = app.repository.state.value.servers.size
        clickTag("profile-add-server-button")
        composeRule.waitUntil(5_000) {
            app.repository.state.value.servers.size == serverCount + 1
        }

        val createdServer = app.repository.state.value.servers.last()
        assertAnyTextDisplayed(createdServer.name)
        assertTrue(app.repository.state.value.servers.any { it.id == createdServer.id })
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

    private fun openFirstServerEditor() {
        composeRule.onAllNodesWithText("Edit")[0].performClick()
        composeRule.waitForIdle()
    }
}
