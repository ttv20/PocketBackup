@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.model.TargetMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class MetricTone {
    Success,
    Warning,
    Destructive,
    Route,
    Neutral,
}

@Composable
internal fun ProfileListRow(
    profile: BackupProfile,
    target: TargetRecord?,
    issues: List<com.ttv20.rsyncbackup.model.ValidationIssue>,
    modifier: Modifier = Modifier,
    showRunStatus: Boolean = false,
    liveProgress: RunProgressState? = null,
    isRunning: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
    trailingSupporting: (@Composable () -> Unit)? = null,
) {
    SectionCard(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        selected = isRunning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                EntityIcon(
                    icon = if (isRunning) Icons.Outlined.Sync else Icons.Outlined.Folder,
                    tone = if (issues.any { it.severity == Severity.ERROR }) MetricTone.Warning else MetricTone.Route,
                    animated = isRunning,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProfileMetaLine(Icons.Outlined.Folder, profile.sourcePath)
                    ProfileRouteLine(profile, target)
                    LastNextLine(profile)
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    trailing()
                    trailingSupporting?.invoke()
                }
            }
            if (showRunStatus) {
                LiveRunProgressDetails(liveProgress)
            }
            conciseIssueText(issues)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = toneColor(if (issues.any { issue -> issue.severity == Severity.ERROR }) MetricTone.Destructive else MetricTone.Warning),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ProfileStatusBadge(profile: BackupProfile, liveProgress: RunProgressState?) {
    val live = liveProgress?.takeIf { it.phase != RunProgressPhase.IDLE }
    StatusBadge(
        label = live?.let { phaseLabel(it.phase) } ?: profile.status.lastStatus.displayLabel(),
        tone = live?.let { MetricTone.Route } ?: profile.status.lastStatus.tone(),
        animated = live?.phase?.hasActiveMotion() == true,
    )
}

@Composable
internal fun RunStopButton(
    isRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (isRunning) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            if (isRunning) Icons.Outlined.Error else Icons.Outlined.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (isRunning) "Stop" else "Run")
    }
}

@Composable
internal fun TargetListRow(
    target: TargetRecord,
    trusted: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    SectionCard(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            EntityIcon(Icons.Outlined.Storage, if (trusted) MetricTone.Success else MetricTone.Warning)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(target.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(targetConnectionSummary(target), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RouteSummaryLine("Server address", target.lanHost.ifBlank { "Not set" }, MetricTone.Route)
                target.tailscaleHost?.let { RouteSummaryLine("Tailscale device", it, MetricTone.Route) }
                RouteSummaryLine("Fingerprint", if (trusted) "Trusted" else "Needs fingerprint", if (trusted) MetricTone.Success else MetricTone.Warning)
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
    }
}

internal fun targetConnectionSummary(target: TargetRecord): String {
    val host = target.lanHost.ifBlank { target.tailscaleHost.orEmpty() }.ifBlank { "No address" }
    val user = target.user.ifBlank { "user" }
    return "$user@$host:${target.port}"
}

@Composable
internal fun EntityIcon(icon: ImageVector, tone: MetricTone, animated: Boolean = false) {
    val pulseScale = activePulseScale(animated)
    val rotation = activeRotationDegrees(animated)
    Surface(
        color = toneContainerColor(tone),
        contentColor = toneOnContainerColor(tone),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.scale(pulseScale),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(9.dp)
                .size(24.dp)
                .rotate(rotation),
        )
    }
}

@Composable
internal fun ProfileRouteLine(profile: BackupProfile, target: TargetRecord?) {
    ProfileMetaLine(
        icon = Icons.Outlined.Storage,
        text = listOfNotNull(target?.name ?: "Missing target", routeModeLabel(profile.targetMode)).joinToString(" - "),
    )
}

@Composable
internal fun LastNextLine(profile: BackupProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        ProfileMetaLine(lastRunIcon(profile), lastRunLabel(profile))
        ProfileMetaLine(Icons.Outlined.Schedule, nextRunLabel(profile))
    }
}

@Composable
internal fun ProfileMetaLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LiveRunProgressDetails(liveProgress: RunProgressState?) {
    val live = liveProgress?.takeIf { it.phase != RunProgressPhase.IDLE }
    if (live == null) return
    val summary = live.toRunProgressSummary()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        if (summary.spinnerOnly) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    summary.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            val statusLabel = phaseLabel(live.phase)
            summary.message
                .takeUnless { it.equals(statusLabel, ignoreCase = true) }
                ?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            summary.transferPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
        summary.metrics.takeIf { it.isNotEmpty() }?.let { metrics ->
            ProgressMetricGrid(metrics)
        }
        summary.fileLine?.let { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun lastRunIcon(profile: BackupProfile): ImageVector =
    if (profile.status.lastSuccessAt != null) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule

internal val RunTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val RunDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

internal fun lastRunLabel(profile: BackupProfile): String =
    when {
        profile.status.lastSuccessAt != null -> "Last success: ${formatRunTimestamp(profile.status.lastSuccessAt)}"
        profile.status.lastRunAt != null -> "Last run: ${formatRunTimestamp(profile.status.lastRunAt)}"
        else -> "Last run: Never"
    }

internal fun nextRunLabel(profile: BackupProfile): String =
    profile.status.nextRunAt?.let { "Next: ${formatRunTimestamp(it)}" }
        ?: when (profile.schedule.type) {
            ScheduleType.DISABLED -> "No schedule"
            else -> "Next: ${scheduleLabel(profile.schedule)}"
        }

internal fun formatRunTimestamp(value: String): String {
    val zone = ZoneId.systemDefault()
    return runCatching {
        val dateTime = Instant.parse(value).atZone(zone)
        val today = LocalDate.now(zone)
        when (dateTime.toLocalDate()) {
            today -> "Today, ${RunTimeFormatter.format(dateTime)}"
            today.minusDays(1) -> "Yesterday, ${RunTimeFormatter.format(dateTime)}"
            else -> RunDateTimeFormatter.format(dateTime)
        }
    }.getOrElse {
        value.substringBefore('.').replace('T', ' ')
    }
}

@Composable
internal fun ProgressMetricGrid(metrics: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnCount = when {
            maxWidth >= 720.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            metrics.chunked(columnCount).forEach { rowMetrics ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowMetrics.forEach { (metricLabel, value) ->
                        ProgressMetric(metricLabel, value, Modifier.weight(1f))
                    }
                    repeat(columnCount - rowMetrics.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun RouteSummaryLine(label: String, value: String, tone: MetricTone) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RouteChip(label, tone)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun StatusBadge(label: String, tone: MetricTone, animated: Boolean = false) {
    val pulseScale = activePulseScale(animated)
    Surface(
        color = toneContainerColor(tone),
        contentColor = toneOnContainerColor(tone),
        shape = RoundedCornerShape(50),
        modifier = Modifier.scale(pulseScale),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun AnimatedStateBlock(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(180)) +
            slideInVertically(animationSpec = tween(180)) { height -> -height / 6 } +
            fadeIn(animationSpec = tween(140)),
        exit = shrinkVertically(animationSpec = tween(150)) +
            slideOutVertically(animationSpec = tween(150)) { height -> -height / 8 } +
            fadeOut(animationSpec = tween(110)),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
internal fun RouteChip(label: String, tone: MetricTone = MetricTone.Route) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = toneColor(tone),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
internal fun AddRow(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    actionButtonTag: String? = null,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EntityIcon(icon, MetricTone.Route)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAction,
                modifier = actionButtonTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(action)
            }
        }
    }
}

@Composable
internal fun EditorHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saveButtonTag: String,
    saveLabel: String = "Save",
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    secondaryActionIcon: ImageVector? = null,
    secondaryActionEnabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(title)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(backLabel)
                }
                Button(
                    onClick = onSave,
                    enabled = saveEnabled,
                    modifier = Modifier.testTag(saveButtonTag),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(saveLabel)
                }
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        enabled = secondaryActionEnabled,
                    ) {
                        secondaryActionIcon?.let {
                            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnsavedChangesDialog(
    entityName: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
        title = { Text("Save changes?") },
        text = { Text("Save changes to this $entityName before leaving?") },
        confirmButton = {
            Button(onClick = onSave, enabled = saveEnabled) {
                Text("Save")
            }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDiscard) {
                    Text("Discard")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
internal fun DeleteConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonTestTag: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = confirmButtonTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun toneColor(tone: MetricTone) = when (tone) {
    MetricTone.Success -> MaterialTheme.colorScheme.primary
    MetricTone.Warning -> MaterialTheme.colorScheme.tertiary
    MetricTone.Destructive -> MaterialTheme.colorScheme.error
    MetricTone.Route -> MaterialTheme.colorScheme.primary
    MetricTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun toneContainerColor(tone: MetricTone) = when (tone) {
    MetricTone.Success -> MaterialTheme.colorScheme.primaryContainer
    MetricTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
    MetricTone.Destructive -> MaterialTheme.colorScheme.errorContainer
    MetricTone.Route -> MaterialTheme.colorScheme.secondaryContainer
    MetricTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
internal fun toneOnContainerColor(tone: MetricTone) = when (tone) {
    MetricTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
    MetricTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
    MetricTone.Destructive -> MaterialTheme.colorScheme.onErrorContainer
    MetricTone.Route -> MaterialTheme.colorScheme.onSecondaryContainer
    MetricTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun conciseIssueText(
    issues: List<com.ttv20.rsyncbackup.model.ValidationIssue>,
): String? = issues.firstOrNull()?.message

internal fun routeModeLabel(targetMode: TargetMode): String =
    when (targetMode) {
        TargetMode.LAN_ONLY -> "Server address only"
        TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> "Server address first"
        TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> "Tailscale device first"
        TargetMode.TAILSCALE_ONLY -> "Tailscale device only"
    }

internal fun scheduleLabel(schedule: BackupSchedule): String =
    when (schedule.type) {
        ScheduleType.DISABLED -> "Disabled"
        ScheduleType.EXACT_DAILY -> "Daily, ${schedule.timeLocal}"
        ScheduleType.BEST_EFFORT_DAILY -> "Best effort, ${schedule.timeLocal}"
        ScheduleType.WEEKLY -> "Weekly, ${weeklyScheduleSummary(schedule.weeklyDays)}, ${schedule.timeLocal}"
    }

internal fun knownWifiSsidOptions(
    deviceSsids: List<String>,
    currentSelection: String?,
): List<String> =
    (deviceSsids + listOfNotNull(currentSelection))
        .mapNotNull { it.cleanSsidLabel() }
        .distinctBy { it.lowercase(Locale.US) }

internal fun String.cleanSsidLabel(): String? =
    trim()
        .trim('"')
        .takeIf { it.isNotBlank() }
