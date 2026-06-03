package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupProfile

data class ConstraintSnapshot(
    val hasWifiConnection: Boolean = false,
    val isUnmetered: Boolean = false,
    val isCharging: Boolean = false,
    val isBatteryLow: Boolean = false,
    val ssid: String? = null,
)

data class ConstraintFailure(
    val code: String,
    val message: String,
)

object BackupConstraintEvaluator {
    fun failures(
        profile: BackupProfile,
        snapshot: ConstraintSnapshot,
        selectedSsid: String?,
    ): List<ConstraintFailure> {
        val constraints = profile.constraints
        val failures = mutableListOf<ConstraintFailure>()

        if (constraints.wifiOnly && !snapshot.hasWifiConnection) {
            failures += ConstraintFailure("wifi_only", "Wi-Fi connection is required")
        }
        if (constraints.unmeteredOnly && !snapshot.isUnmetered) {
            failures += ConstraintFailure("unmetered_only", "Unmetered network is required")
        }
        if (constraints.chargingOnly && !snapshot.isCharging) {
            failures += ConstraintFailure("charging_only", "Charging is required")
        }
        if (constraints.batteryNotLow && snapshot.isBatteryLow) {
            failures += ConstraintFailure("battery_low", "Battery is low")
        }
        if (constraints.selectedSsidOnly) {
            val expected = selectedSsid?.trim().orEmpty()
            val actual = snapshot.ssid?.trim().orEmpty()
            if (expected.isBlank()) {
                failures += ConstraintFailure("ssid_not_configured", "Selected SSID is not configured")
            } else if (actual.isBlank()) {
                failures += ConstraintFailure("ssid_unavailable", "Current SSID is unavailable")
            } else if (actual.trim('"') != expected.trim('"')) {
                failures += ConstraintFailure("ssid_mismatch", "Current SSID does not match selected SSID")
            }
        }

        return failures
    }
}
