package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.BuildConfig

internal object DiagnosticsEndpoints {
    fun eventEndpoint(): DiagnosticsEndpoint? =
        directOpenObserveEndpoint()
            ?: proxyEndpoint(path = "android/events")

    fun crashEndpoint(): DiagnosticsEndpoint? =
        directOpenObserveEndpoint()
            ?: proxyEndpoint(path = "android/crashes")

    private fun directOpenObserveEndpoint(): DiagnosticsEndpoint? {
        val url = BuildConfig.OPENOBSERVE_INGEST_URL.trim().takeIf { it.isNotBlank() } ?: return null
        val authHeader = BuildConfig.OPENOBSERVE_AUTH_HEADER.trim().takeIf { it.isNotBlank() } ?: return null
        return DiagnosticsEndpoint(url = url, authorizationHeader = authHeader)
    }

    private fun proxyEndpoint(path: String): DiagnosticsEndpoint? {
        val baseUrl = BuildConfig.DIAGNOSTICS_PROXY_URL.trimEnd('/').takeIf { it.isNotBlank() } ?: return null
        return DiagnosticsEndpoint(url = "$baseUrl/$path", authorizationHeader = null)
    }
}

data class DiagnosticsEndpoint(
    val url: String,
    val authorizationHeader: String?,
)
