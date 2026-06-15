@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.permissions.PermissionIntents
import com.ttv20.rsyncbackup.permissions.PermissionStateReader
import com.ttv20.rsyncbackup.storage.AppRepository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun PermissionSettingsSection(
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    onRefreshPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var pendingSettingsPermissionType by remember { mutableStateOf<String?>(null) }
    val settingsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingSettingsPermissionType?.let { permissionType ->
            val refreshed = PermissionStateReader(context).read()
            trackPermissionResult(context, permissionType, refreshed.isPermissionGranted(permissionType))
        }
        pendingSettingsPermissionType = null
        onRefreshPermissions()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        trackPermissionResult(context, PERMISSION_NOTIFICATIONS, granted)
        onRefreshPermissions()
    }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        trackPermissionResult(context, PERMISSION_WIFI_STATE, granted)
        onRefreshPermissions()
    }
    fun openSettingsPermission(permissionType: String, intent: Intent) {
        trackPermissionPromptOpened(context, permissionType)
        pendingSettingsPermissionType = permissionType
        runCatching {
            settingsPermissionLauncher.launch(intent)
        }.onFailure {
            pendingSettingsPermissionType = null
            trackPermissionResult(context, permissionType, false)
        }
    }
    SectionCard {
        Text("Permission setup/status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(if (permissions.allRequiredGranted) "All required permissions approved" else "Approve every required item")
        Text("Required", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        PermissionRow(
            label = "All files access",
            detail = "Needed to read the folders you choose for backup.",
            granted = permissions.allFilesAccess,
        ) {
            openSettingsPermission(PERMISSION_ALL_FILES_ACCESS, PermissionIntents.allFilesAccess(context))
        }
        PermissionRow(
            label = "Battery optimization exemption",
            detail = "Keeps scheduled backups from being stopped in the background.",
            granted = permissions.batteryOptimizationExempt,
        ) {
            openSettingsPermission(PERMISSION_BATTERY_OPTIMIZATION, PermissionIntents.batteryOptimization(context))
        }
        PermissionRow(
            label = "Exact alarm access",
            detail = "Lets scheduled backups start at the configured time.",
            granted = permissions.exactAlarmAccess,
        ) {
            openSettingsPermission(PERMISSION_EXACT_ALARM, PermissionIntents.exactAlarm(context))
        }
        PermissionRow(
            label = "Notifications",
            detail = "Shows backup progress, completion, and failures.",
            granted = permissions.notifications,
        ) {
            trackPermissionPromptOpened(context, PERMISSION_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                trackPermissionResult(context, PERMISSION_NOTIFICATIONS, true)
                onRefreshPermissions()
            }
        }
        Text("Optional", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        PermissionRow(
            label = "Wi-Fi/SSID access",
            detail = "Only needed if you want backups to run only on Wi-Fi or only on a specific network.",
            granted = permissions.wifiStateAccess,
        ) {
            trackPermissionPromptOpened(context, PERMISSION_WIFI_STATE)
            wifiPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        OutlinedButton(onClick = onRefreshPermissions) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

internal fun formatBytesUi(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) "$bytes ${units[unitIndex]}" else "%.1f %s".format(Locale.US, value, units[unitIndex])
}

internal fun formatTimestampUi(value: String): String {
    val compact = value
        .substringBefore('.')
        .removeSuffix("Z")
        .replace('T', ' ')
    return compact.takeIf { it.length >= 16 }?.take(16) ?: value
}

internal fun compactMiddleUi(value: String, maxLength: Int = 48): String {
    if (value.length <= maxLength) return value
    val keepStart = (maxLength * 0.45f).toInt().coerceAtLeast(12)
    val keepEnd = (maxLength - keepStart - 3).coerceAtLeast(12)
    return value.take(keepStart).trimEnd('/') + "..." + value.takeLast(keepEnd).trimStart('/')
}

@Composable
internal fun LogsScreen(state: AppState, repository: AppRepository) {
    var showClearPrompt by rememberSaveable { mutableStateOf(false) }

    if (showClearPrompt) {
        DeleteConfirmationDialog(
            title = "Clear logs?",
            message = "This removes all backup logs from this device. This cannot be undone.",
            confirmLabel = "Clear",
            onConfirm = {
                showClearPrompt = false
                repository.clearLogs()
            },
            onDismiss = { showClearPrompt = false },
            confirmButtonTestTag = "logs-confirm-clear-button",
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SectionHeader("Logs")
                }
                OutlinedButton(
                    onClick = { showClearPrompt = true },
                    enabled = state.logs.isNotEmpty(),
                    modifier = Modifier.testTag("logs-clear-button"),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
        }
        items(state.logs) { log ->
            SectionCard {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    EntityIcon(
                        icon = when (log.status) {
                            RunStatus.SUCCESS -> Icons.Outlined.CheckCircle
                            RunStatus.WARNING -> Icons.Outlined.Warning
                            RunStatus.FAILED, RunStatus.CANCELLED -> Icons.Outlined.Error
                            else -> Icons.Outlined.Sync
                        },
                        tone = log.status.tone(),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(log.profileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StatusBadge(log.status.displayLabel(), log.status.tone())
                        }
                        ProfileMetaLine(Icons.Outlined.Schedule, logTimeSummary(log))
                        Text(
                            log.summary.ifBlank { "No summary" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CompactLogBlock(log)
                log.endReasonDetail
                    ?.takeIf { it.isNotBlank() && it != log.summary }
                    ?.let { detail ->
                        Text(
                            "Reason detail: $detail",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (log.status == RunStatus.SUCCESS) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                unsuccessfulLogLastOutput(log)?.let { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (state.logs.isEmpty()) {
            item {
                SectionCard {
                    Text("No logs yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Run a profile to record the first result.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun CompactLogBlock(log: BackupLog) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            ProgressMetric("Run", log.trigger.label(), Modifier.weight(0.8f))
            ProgressMetric(log.finishedLabel(), log.finishedAt?.let { formatRunTimestamp(it) } ?: "Running", Modifier.weight(1f))
            ProgressMetric("Data", log.finalByteSummary(), Modifier.weight(1f))
        }
    }
}

internal fun RunStatus.tone(): MetricTone =
    when (this) {
        RunStatus.SUCCESS -> MetricTone.Success
        RunStatus.WARNING -> MetricTone.Warning
        RunStatus.FAILED, RunStatus.CANCELLED -> MetricTone.Destructive
        RunStatus.RUNNING, RunStatus.QUEUED -> MetricTone.Route
        RunStatus.NEVER_RUN -> MetricTone.Neutral
    }

internal fun RunStatus.displayLabel(): String =
    when (this) {
        RunStatus.SUCCESS -> "Success"
        RunStatus.WARNING -> "Warning"
        RunStatus.FAILED -> "Failed"
        RunStatus.CANCELLED -> "Cancelled"
        RunStatus.RUNNING -> "Running"
        RunStatus.QUEUED -> "Queued"
        RunStatus.NEVER_RUN -> "Never run"
    }

internal fun BackupLog.finalByteSummary(): String {
    val sent = raw.lineSequence().firstOrNull { it.startsWith("sent ") } ?: return "-"
    val rawSent = sent.substringBefore(" received").removePrefix("sent ").trim()
    val bytes = rawSent
        .removeSuffix("bytes")
        .trim()
        .replace(",", "")
        .toLongOrNull()
    return bytes?.let { formatBytesUi(it) } ?: rawSent.ifBlank { "-" }
}

internal fun logTimeSummary(log: BackupLog): String =
    log.finishedAt?.let { "${log.finishedLabel()}: ${formatRunTimestamp(it)}" }
        ?: "Started: ${formatRunTimestamp(log.startedAt)}"

internal fun BackupRunTrigger.label(): String =
    when (this) {
        BackupRunTrigger.MANUAL -> "Manual"
        BackupRunTrigger.AUTOMATIC -> "Automatic"
    }

internal fun BackupEndReason.label(): String =
    when (this) {
        BackupEndReason.USER_CANCELLED -> "User cancel"
        BackupEndReason.FORCE_STOPPED -> "Force stop"
        BackupEndReason.NO_NETWORK -> "No network"
        BackupEndReason.CONSTRAINTS_NOT_MET -> "Constraints"
        BackupEndReason.CRASH -> "Crash"
        BackupEndReason.ERROR -> "Error"
    }

internal fun BackupLog.finishedLabel(): String =
    when (status) {
        RunStatus.CANCELLED -> "Cancelled"
        RunStatus.FAILED -> "Finished"
        RunStatus.SUCCESS, RunStatus.WARNING -> "Finished"
        else -> "Finished"
    }

internal fun unsuccessfulLogLastOutput(log: BackupLog): String? {
    if (log.status == RunStatus.SUCCESS || log.raw.isBlank()) return null
    val detail = log.endReasonDetail?.trim()
    val summary = log.summary.trim()
    return log.raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it != summary && it != detail }
        .lastOrNull()
}
