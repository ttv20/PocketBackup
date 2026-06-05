package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ConstraintSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConstraintEvaluatorTest {
    @Test
    fun returnsNoFailuresWhenDisabledConstraintsAreNotMet() {
        val failures = BackupConstraintEvaluator.failures(
            profile = profile(ConstraintSettings(batteryNotLow = false)),
            snapshot = ConstraintSnapshot(isBatteryLow = true),
        )

        assertTrue(failures.isEmpty())
    }

    @Test
    fun reportsEnabledConstraintFailures() {
        val failures = BackupConstraintEvaluator.failures(
            profile = profile(
                ConstraintSettings(
                    wifiOnly = true,
                    unmeteredOnly = true,
                    chargingOnly = true,
                    batteryNotLow = true,
                    selectedSsidOnly = true,
                    selectedSsid = "Home",
                ),
            ),
            snapshot = ConstraintSnapshot(
                hasWifiConnection = false,
                isUnmetered = false,
                isCharging = false,
                isBatteryLow = true,
                ssid = "Guest",
            ),
        )

        assertEquals(
            listOf("wifi_only", "unmetered_only", "charging_only", "battery_low", "ssid_mismatch"),
            failures.map { it.code },
        )
    }

    @Test
    fun normalizesQuotedWifiSsid() {
        val failures = BackupConstraintEvaluator.failures(
            profile = profile(ConstraintSettings(selectedSsidOnly = true, selectedSsid = "Home")),
            snapshot = ConstraintSnapshot(ssid = "\"Home\""),
        )

        assertTrue(failures.isEmpty())
    }

    private fun profile(constraints: ConstraintSettings = ConstraintSettings()) =
        BackupProfile(
            id = "profile",
            name = "Phone",
            targetId = "target",
            remotePath = "/mnt/backup/phone",
            constraints = constraints,
            excludes = "cache/\n",
        )
}
