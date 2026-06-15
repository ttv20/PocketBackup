@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

internal val UrlRedactionRegex = Regex("""https?://\S+""")
internal val PreferredCustomTabsPackages = listOf(
    "com.android.chrome",
    "com.google.android.apps.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.chrome.canary",
    "com.brave.browser",
    "com.microsoft.emmx",
    "com.kiwibrowser.browser",
    "org.mozilla.firefox",
    "org.mozilla.fenix",
    "com.sec.android.app.sbrowser",
)
internal val NonBrowserCustomTabsPackages = setOf(
    "fe.linksheet",
    "fe.linksheet.nightly",
)
