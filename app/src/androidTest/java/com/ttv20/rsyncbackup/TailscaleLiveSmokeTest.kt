package com.ttv20.rsyncbackup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TailscaleLiveSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: RsyncBackupApplication
        get() = composeRule.activity.application as RsyncBackupApplication

    @Test
    fun authenticatesAndChecksReachabilityThroughUi() {
        val authKey = requiredSmokeArg("authKey")
        val testHost = requiredSmokeArg("testHost")
        val testPort = requiredSmokeArg("testPort")
        val nodeName = smokeArg("nodeName") ?: "android-rsync-live-smoke"

        openScreen("Tailscale")
        composeRule.onNodeWithText("Tailscale setup/status/test/reset").assertIsDisplayed()
        replaceText("tailscale-node-name-field", nodeName)
        replaceText("tailscale-auth-key-field", authKey)
        clickTag("tailscale-authenticate-button")

        composeRule.waitUntil(120_000) {
            val tailscale = app.repository.state.value.tailscale
            (tailscale.isConfigured && tailscale.stateSecretAlias != null) || tailscale.lastError != null
        }
        val authenticatedState = app.repository.state.value.tailscale
        assertNull(authenticatedState.lastError)
        assertTrue(authenticatedState.isConfigured)
        assertNotNull(authenticatedState.stateSecretAlias)
        assertNotNull(authenticatedState.lastLoginAt)
        assertFalse("Auth key must not be persisted in app state", appStateText().contains(authKey))

        runReachabilityTest(testHost, testPort)
        assertFalse("Auth key must not be persisted after reachability test", appStateText().contains(authKey))
    }

    @Test
    fun checksReachabilityWithExistingStateThroughUi() {
        val testHost = requiredSmokeArg("testHost")
        val testPort = requiredSmokeArg("testPort")
        val tailscale = app.repository.state.value.tailscale
        assumeTrue("Skipping existing-state Tailscale smoke; Tailscale is not configured", tailscale.isConfigured)
        assumeTrue(
            "Skipping existing-state Tailscale smoke; state alias is missing",
            !tailscale.stateSecretAlias.isNullOrBlank(),
        )

        openScreen("Tailscale")
        composeRule.onNodeWithText("Tailscale setup/status/test/reset").assertIsDisplayed()
        runReachabilityTest(testHost, testPort)
    }

    private fun appStateText(): String =
        composeRule.activity.filesDir.resolve("app-state.json").readText()

    private fun replaceText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        composeRule.waitForIdle()
    }

    private fun clickTag(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun runReachabilityTest(testHost: String, testPort: String): com.ttv20.rsyncbackup.model.TailscaleStateMetadata {
        replaceText("tailscale-test-host-field", testHost)
        replaceText("tailscale-test-port-field", testPort)
        clickTag("tailscale-test-button")

        composeRule.waitUntil(120_000) {
            val tailscale = app.repository.state.value.tailscale
            tailscale.lastReachabilityTestAt != null || tailscale.lastError != null
        }
        val testedState = app.repository.state.value.tailscale
        assertNull(testedState.lastError)
        assertNotNull(testedState.lastReachabilityTestAt)
        return testedState
    }

    private fun openScreen(label: String) {
        composeRule.onAllNodesWithText(label)[0].performClick()
        composeRule.waitForIdle()
    }

    private fun requiredSmokeArg(name: String): String {
        val value = smokeArg(name)
        assumeTrue("Skipping live Tailscale smoke; missing instrumentation arg $name", !value.isNullOrBlank())
        return value!!
    }

    private fun smokeArg(name: String): String? =
        InstrumentationRegistry.getArguments().getString(name)
}
