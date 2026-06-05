package com.ttv20.rsyncbackup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.ttv20.rsyncbackup.model.InitialData
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.TargetMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class UserDrivenSetupSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: RsyncBackupApplication
        get() = composeRule.activity.application as RsyncBackupApplication

    @Test
    fun configuresTargetInstallsKeyAndRunsBackupThroughUi() {
        val host = requiredSmokeArg("host")
        val port = requiredSmokeArg("port").toInt()
        val user = requiredSmokeArg("user")
        val password = requiredSmokeArg("password")
        val remotePath = requiredSmokeArg("remotePath")
        val sourceText = smokeArg("sourceText") ?: "ampere-redroid-ui-setup-smoke"
        val sourceDir = prepareSourceTree(sourceText)

        openScreen("SSH Access")
        clickTag("ssh-generate-key-button")
        composeRule.waitUntil(10_000) {
            app.repository.state.value.sshKeySettings.publicKey?.startsWith("ssh-ed25519 ") == true
        }

        openScreen("Targets")
        openFirstTargetEditor()
        replaceText("target-user-field", user)
        replaceText("target-lan-host-field", host)
        replaceText("target-port-field", port.toString())
        replaceText("target-default-remote-path-field", remotePath)
        clickTag("target-save-button")
        composeRule.waitUntil(10_000) {
            val target = app.repository.state.value.targets.first { it.id == InitialData.DEFAULT_TARGET_ID }
            target.user == user &&
                target.lanHost == host &&
                target.port == port &&
                target.defaultRemotePath == remotePath
        }

        openScreen("Targets")
        openFirstTargetEditor()
        clickTag("target-scan-lan-button")
        waitForTag("target-trust-scanned-key-button", 45_000)
        clickTag("target-trust-scanned-key-button")
        composeRule.waitUntil(10_000) {
            app.repository.state.value.trustedHostFingerprints.any {
                it.hostnames.contains(host) && it.port == port && it.publicKey != null
            }
        }

        replaceText("target-setup-password-field", password)
        clickTag("target-install-over-lan-button")
        waitForText("Public key installed over LAN", 90_000)

        openScreen("Profiles")
        openFirstProfileEditor()
        replaceText("profile-source-path-field", sourceDir.absolutePath)
        replaceText("profile-remote-path-field", remotePath)
        clickTag("target-mode-lan_only")
        composeRule.onNodeWithText("Advanced settings").performClick()
        composeRule.waitForIdle()
        clickTag("profile-constraint-battery-not-low-switch")
        clickTag("profile-save-button")
        composeRule.waitUntil(10_000) {
            val profile = app.repository.state.value.profiles.first { it.id == InitialData.DEFAULT_PROFILE_ID }
            profile.sourcePath == sourceDir.absolutePath &&
                profile.remotePath == remotePath &&
                profile.targetMode == TargetMode.LAN_ONLY &&
                !profile.constraints.batteryNotLow
        }

        openScreen("Dashboard")
        clickTag("dashboard-run-profile-${InitialData.DEFAULT_PROFILE_ID}")
        composeRule.waitUntil(180_000) {
            val status = app.repository.state.value.profiles
                .first { it.id == InitialData.DEFAULT_PROFILE_ID }
                .status.lastStatus
            status == RunStatus.SUCCESS || status == RunStatus.FAILED || status == RunStatus.CANCELLED
        }

        val profile = app.repository.state.value.profiles.first { it.id == InitialData.DEFAULT_PROFILE_ID }
        assertEquals(profile.status.lastMessage, RunStatus.SUCCESS, profile.status.lastStatus)
        assertTrue(app.repository.state.value.logs.any { it.profileId == InitialData.DEFAULT_PROFILE_ID })
    }

    private fun prepareSourceTree(sourceText: String): File {
        val sourceDir = File(composeRule.activity.filesDir, "ui-setup-smoke-source").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        File(sourceDir, "hello.txt").writeText(sourceText)
        File(sourceDir, "nested").mkdirs()
        File(sourceDir, "nested/info.txt").writeText("configured-through-ui\n")
        return sourceDir
    }

    private fun replaceText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        composeRule.waitForIdle()
    }

    private fun clickTag(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
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

    private fun requiredSmokeArg(name: String): String {
        val value = smokeArg(name)
        assumeTrue("Skipping UI-driven setup smoke; missing instrumentation arg $name", !value.isNullOrBlank())
        return value!!
    }

    private fun smokeArg(name: String): String? =
        InstrumentationRegistry.getArguments().getString(name)
}
