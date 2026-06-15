@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.ttv20.rsyncbackup.model.ConstraintSettings

@Composable
internal fun PrimaryConstraintEditor(constraints: ConstraintSettings, onChange: (ConstraintSettings) -> Unit) {
    ToggleRow("Wi-Fi only", constraints.wifiOnly) { onChange(constraints.copy(wifiOnly = it)) }
    ToggleRow("Charging only", constraints.chargingOnly) { onChange(constraints.copy(chargingOnly = it)) }
}

@Composable
internal fun AdvancedConstraintEditor(
    constraints: ConstraintSettings,
    knownWifiSsids: List<String>,
    onChange: (ConstraintSettings) -> Unit,
) {
    ToggleRow("Unmetered only", constraints.unmeteredOnly) { onChange(constraints.copy(unmeteredOnly = it)) }
    ToggleRow(
        label = "Battery not low",
        checked = constraints.batteryNotLow,
        switchTag = "profile-constraint-battery-not-low-switch",
    ) {
        onChange(constraints.copy(batteryNotLow = it))
    }
    ToggleRow("Selected WiFi network only", constraints.selectedSsidOnly) {
        onChange(
            constraints.copy(
                selectedSsidOnly = it,
                selectedSsid = if (it) {
                    constraints.selectedSsid ?: knownWifiSsids.firstOrNull()
                } else {
                    constraints.selectedSsid
                },
            ),
        )
    }
    if (constraints.selectedSsidOnly) {
        WifiSsidSelector(
            selectedSsid = constraints.selectedSsid,
            knownWifiSsids = knownWifiSsids,
        ) {
            onChange(constraints.copy(selectedSsid = it))
        }
    }
    ToggleRow("Manual override allowed", constraints.manualOverrideAllowed) {
        onChange(constraints.copy(manualOverrideAllowed = it))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WifiSsidSelector(
    selectedSsid: String?,
    knownWifiSsids: List<String>,
    onChange: (String?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(knownWifiSsids.isNotEmpty()) }
    LaunchedEffect(knownWifiSsids) {
        expanded = knownWifiSsids.isNotEmpty()
    }

    ExposedDropdownMenuBox(
        expanded = expanded && knownWifiSsids.isNotEmpty(),
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedSsid.orEmpty(),
            onValueChange = {
                onChange(it.ifBlank { null })
                expanded = knownWifiSsids.isNotEmpty()
            },
            label = { Text("WiFi network") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .testTag("profile-wifi-network-field"),
        )
        ExposedDropdownMenu(
            expanded = expanded && knownWifiSsids.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            knownWifiSsids.forEach { ssid ->
                DropdownMenuItem(
                    text = { Text(ssid, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onChange(ssid)
                        expanded = false
                    },
                )
            }
        }
    }
}
