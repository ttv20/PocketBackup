package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.model.GlobalSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsDefaultsTest {
    @Test
    fun consentDefaultIsNullAndDoesNotAllowNetwork() {
        val settings = GlobalSettings()

        assertNull(settings.diagnosticsEnabled)
        assertFalse(diagnosticsConsentAllowsNetwork(settings.diagnosticsEnabled))
    }

    @Test
    fun welcomeDefaultIsCheckedForNormalBuildsOnly() {
        assertTrue(diagnosticsWelcomeDefaultChecked(isFdroidBuild = false))
        assertFalse(diagnosticsWelcomeDefaultChecked(isFdroidBuild = true))
    }
}
