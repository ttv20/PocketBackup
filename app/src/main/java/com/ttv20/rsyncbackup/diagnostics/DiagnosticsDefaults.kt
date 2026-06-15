package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.BuildConfig

fun diagnosticsConsentAllowsNetwork(
    consent: Boolean?,
    userOptInRequired: Boolean = BuildConfig.DIAGNOSTICS_USER_OPT_IN_REQUIRED,
    diagnosticsBackendConfigured: Boolean = BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED,
): Boolean =
    if (!diagnosticsBackendConfigured) {
        false
    } else if (userOptInRequired) {
        consent == true
    } else {
        consent != false
    }

fun diagnosticsWelcomeDefaultChecked(
    isFdroidBuild: Boolean = BuildConfig.IS_FDROID_BUILD,
    userOptInRequired: Boolean = BuildConfig.DIAGNOSTICS_USER_OPT_IN_REQUIRED,
    diagnosticsBackendConfigured: Boolean = BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED,
): Boolean =
    diagnosticsBackendConfigured && !isFdroidBuild && !userOptInRequired
