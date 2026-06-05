package com.ttv20.rsyncbackup.backup

import android.content.Context
import android.net.wifi.WifiManager
import java.util.Locale

class AndroidWifiNetworkReader(private val context: Context) {
    @Suppress("DEPRECATION")
    fun currentSsid(): String? =
        runCatching {
            context.applicationContext
                .getSystemService(WifiManager::class.java)
                ?.connectionInfo
                ?.ssid
                ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
                ?.cleanSsid()
        }.getOrNull()

    @Suppress("DEPRECATION")
    fun knownSsids(): List<String> {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        runCatching { wifiManager?.startScan() }
        val visibleSsids = runCatching {
            wifiManager
                ?.scanResults
                .orEmpty()
                .mapNotNull { it.SSID.cleanSsid() }
        }.getOrDefault(emptyList())

        return (listOfNotNull(currentSsid()) + visibleSsids)
            .distinctBy { it.lowercase(Locale.US) }
    }
}

private fun String?.cleanSsid(): String? =
    this
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }
