package com.ttv20.rsyncbackup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenshotSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: RsyncBackupApplication
        get() = composeRule.activity.application as RsyncBackupApplication

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun capturesKeyOnboardingSteps() {
        val initialTargetCount = app.repository.state.value.targets.size
        val initialProfileCount = app.repository.state.value.profiles.size

        capture("01-welcome")

        clickTag("onboarding-start-button")
        composeRule.onNodeWithText("Permissions").assertIsDisplayed()
        capture("02-permissions")

        clickTag("onboarding-permissions-continue-button")
        composeRule.onNodeWithText("SSH Access").assertIsDisplayed()
        clickTag("ssh-generate-key-button")
        composeRule.waitUntil(10_000) {
            app.repository.state.value.sshKeySettings.publicKey?.startsWith("ssh-ed25519 ") == true
        }
        composeRule.onNodeWithText("Copy public key").assertIsDisplayed()
        capture("03-ssh-access")

        clickTag("onboarding-continue-button")
        composeRule.onNodeWithText("Tailscale Connection").assertIsDisplayed()
        composeRule.onNodeWithText("Not connected").assertIsDisplayed()
        capture("04-tailscale-connection")

        clickTag("onboarding-continue-button")
        composeRule.onNodeWithText("New Target").assertIsDisplayed()
        composeRule.onNodeWithText("Scan LAN").assertIsDisplayed()
        capture("05-new-target")

        clickTag("onboarding-save-target-button")
        assertEquals(initialTargetCount, app.repository.state.value.targets.size)
        composeRule.onNodeWithText("New Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Selected target").assertIsDisplayed()
        capture("06-new-profile")

        clickTag("onboarding-save-profile-button")
        assertEquals(initialProfileCount, app.repository.state.value.profiles.size)
        composeRule.onNodeWithText("Review And Dry Run").assertIsDisplayed()
        capture("07-review")

        clickTag("onboarding-dry-run-button")
        composeRule.onNodeWithText("Dry run result").assertIsDisplayed()
        capture("08-dry-run-result")
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

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val screenshotDir = screenshotDir()
        val screenshotFile = File(screenshotDir, "$name.png")
        assertTrue("Failed to capture $screenshotFile", device.takeScreenshot(screenshotFile))
        assertTrue("Screenshot is empty: $screenshotFile", screenshotFile.length() > 0)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().context
        return File(context.getExternalFilesDir(null), "onboarding-screenshots").also { it.mkdirs() }
    }
}
