package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.model.GlobalSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsDefaultsTest {
    @Test
    fun optInRequiredDefaultDoesNotAllowNetwork() {
        val settings = GlobalSettings()

        assertNull(settings.diagnosticsEnabled)
        assertFalse(
            diagnosticsConsentAllowsNetwork(
                consent = settings.diagnosticsEnabled,
                userOptInRequired = true,
                diagnosticsBackendConfigured = true,
            ),
        )
    }

    @Test
    fun optOutDefaultAllowsNetworkUntilDisabled() {
        val settings = GlobalSettings()

        assertNull(settings.diagnosticsEnabled)
        assertTrue(
            diagnosticsConsentAllowsNetwork(
                consent = settings.diagnosticsEnabled,
                userOptInRequired = false,
                diagnosticsBackendConfigured = true,
            ),
        )
        assertFalse(
            diagnosticsConsentAllowsNetwork(
                consent = false,
                userOptInRequired = false,
                diagnosticsBackendConfigured = true,
            ),
        )
    }

    @Test
    fun backendDisabledNeverAllowsNetwork() {
        assertFalse(
            diagnosticsConsentAllowsNetwork(
                consent = true,
                userOptInRequired = true,
                diagnosticsBackendConfigured = false,
            ),
        )
        assertFalse(
            diagnosticsConsentAllowsNetwork(
                consent = null,
                userOptInRequired = false,
                diagnosticsBackendConfigured = false,
            ),
        )
    }

    @Test
    fun welcomeDefaultIsCheckedForOptOutNormalBuildsOnly() {
        assertTrue(
            diagnosticsWelcomeDefaultChecked(
                isFdroidBuild = false,
                userOptInRequired = false,
                diagnosticsBackendConfigured = true,
            ),
        )
        assertFalse(
            diagnosticsWelcomeDefaultChecked(
                isFdroidBuild = true,
                userOptInRequired = false,
                diagnosticsBackendConfigured = true,
            ),
        )
        assertFalse(
            diagnosticsWelcomeDefaultChecked(
                isFdroidBuild = false,
                userOptInRequired = true,
                diagnosticsBackendConfigured = true,
            ),
        )
        assertFalse(
            diagnosticsWelcomeDefaultChecked(
                isFdroidBuild = false,
                userOptInRequired = false,
                diagnosticsBackendConfigured = false,
            ),
        )
    }
}
