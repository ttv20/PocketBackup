package com.ttv20.rsyncbackup.backup

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager

class AndroidConstraintSnapshotReader(private val context: Context) {
    fun read(): ConstraintSnapshot {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        return ConstraintSnapshot(
            hasWifiConnection = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            isUnmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
            isCharging = battery.isCharging(),
            isBatteryLow = battery.isBatteryLow(),
            ssid = currentSsid(),
        )
    }

    private fun currentSsid(): String? =
        runCatching {
            context.applicationContext
                .getSystemService(WifiManager::class.java)
                ?.connectionInfo
                ?.ssid
                ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
        }.getOrNull()

    private fun Intent?.isCharging(): Boolean {
        val status = this?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun Intent?.isBatteryLow(): Boolean {
        val level = this?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return false
        val scale = this.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return false
        return (level * 100 / scale) <= 15
    }
}
