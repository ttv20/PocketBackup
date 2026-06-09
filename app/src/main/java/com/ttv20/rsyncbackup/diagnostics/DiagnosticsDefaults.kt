package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.BuildConfig

fun diagnosticsConsentAllowsNetwork(consent: Boolean?): Boolean = consent == true

fun diagnosticsWelcomeDefaultChecked(isFdroidBuild: Boolean = BuildConfig.IS_FDROID_BUILD): Boolean =
    !isFdroidBuild
