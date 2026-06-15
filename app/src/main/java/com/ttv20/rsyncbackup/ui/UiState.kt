@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord

internal enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Outlined.Dashboard),
    Profiles("Profiles", Icons.Outlined.Folder),
    Targets("Targets", Icons.Outlined.Storage),
    Logs("Logs", Icons.Outlined.Article),
    SshKeys("SSH Access", Icons.Outlined.Key),
    Tailscale("Tailscale", Icons.Outlined.Cloud),
    Settings("Settings", Icons.Outlined.Settings),
}

internal enum class SettingsExportAction {
    Copy,
    Save,
}

internal enum class SettingsImportSource {
    Paste,
    File,
}

internal val MainScreens = listOf(Screen.Dashboard, Screen.Profiles, Screen.Targets, Screen.Logs)
internal const val MIN_PORT = 1
internal const val MAX_PORT = 65535
internal const val IMPORTED_SSH_PRIVATE_KEY_ALIAS = "imported-ssh-private-key"
internal const val IMPORTED_SSH_PASSPHRASE_ALIAS = "imported-ssh-passphrase"
internal const val IMPORTED_TAILSCALE_STATE_ALIAS = "imported-tailscale-state"
internal const val PERMISSION_ALL_FILES_ACCESS = "all_files_access"
internal const val PERMISSION_BATTERY_OPTIMIZATION = "battery_optimization"
internal const val PERMISSION_EXACT_ALARM = "exact_alarm"
internal const val PERMISSION_NOTIFICATIONS = "notifications"
internal const val PERMISSION_WIFI_STATE = "wifi_state"
internal const val PRIVACY_POLICY_URL =
    "https://codeberg.org/ttv20/PocketBackup/src/branch/main/PRIVACY.md"

internal enum class OnboardingStep(val title: String) {
    Welcome("Welcome"),
    Permissions("Permissions"),
    Tailscale("Tailscale Connection"),
    NewTarget("New Target"),
    NewProfile("New Profile"),
    Review("Review And Dry Run"),
}

internal val OnboardingSteps = OnboardingStep.entries.toList()

internal enum class PendingOnboardingNavigation {
    Back,
    Skip,
}

internal data class RemotePathBrowseRequest(
    val title: String,
    val startPath: String,
    val target: TargetRecord,
    val routes: List<Route>,
)
