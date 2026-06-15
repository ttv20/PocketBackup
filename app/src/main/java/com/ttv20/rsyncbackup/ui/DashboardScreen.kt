@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.backup.AndroidConstraintSnapshotReader
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ProfileValidator
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.model.transferProgressPercent
import com.ttv20.rsyncbackup.scheduling.ScheduleTriggerCalculator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
internal fun DashboardScreen(
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    onRun: (RunRequest) -> Unit,
    onStartOnboarding: (OnboardingStep) -> Unit,
) {
    val context = LocalContext.current
    val constraintSnapshot = remember(context, state.profiles) {
        AndroidConstraintSnapshotReader(context).read()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader("Dashboard")
        }
        if (state.profiles.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.Folder,
                    title = "No backup profiles",
                    body = "Create a profile to choose what should be backed up and where it should go.",
                    action = "Start setup",
                    onAction = { onStartOnboarding(OnboardingStep.Welcome) },
                )
            }
        } else {
            item {
                DashboardOverviewSection(state)
            }
            val permissionChecklistItem = setupPermissionChecklistItem(permissions)
            if (!permissionChecklistItem.complete) {
                item {
                    SetupRepairCard(
                        checklist = listOf(permissionChecklistItem),
                        onOpenStep = { onStartOnboarding(OnboardingStep.Permissions) },
                    )
                }
            }
            items(state.profiles, key = { it.id }) { profile ->
                val issues = ProfileValidator.validate(profile, state)
                val checklist = profileSetupChecklistForProfile(profile, state, constraintSnapshot)
                val isRunningProfile = state.queue.runningProfileId == profile.id
                val liveProgress = state.runProgress.takeIf { it.profileId == profile.id && isRunningProfile }
                val target = state.targets.firstOrNull { it.id == profile.targetId }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileListRow(
                        profile = profile,
                        target = target,
                        issues = issues,
                        showRunStatus = true,
                        liveProgress = liveProgress,
                        isRunning = isRunningProfile,
                        trailing = {
                            RunStopButton(
                                isRunning = isRunningProfile,
                                onClick = {
                                    if (isRunningProfile) BackupService.cancel(context) else onRun(RunRequest(context, profile.id))
                                },
                                enabled = isRunningProfile || issues.none { it.severity == Severity.ERROR },
                                modifier = Modifier.testTag("dashboard-run-profile-${profile.id}"),
                            )
                        },
                        trailingSupporting = {
                            ProfileStatusBadge(profile, liveProgress)
                        },
                    )
                    if (checklist.any { !it.complete }) {
                        SetupRepairCard(
                            checklist = checklist,
                            onOpenStep = { onStartOnboarding(firstMissingSetupStep(checklist)) },
                        )
                    }
                }
            }
            item {
                RecentActivitySection(state.logs)
            }
        }
    }
}

@Composable
internal fun DashboardOverviewSection(state: AppState) {
    val runningProfile = state.queue.runningProfileId
        ?.let { id -> state.profiles.firstOrNull { it.id == id } }
    val issueCount = state.profiles.count { profile ->
        ProfileValidator.validate(profile, state).any { it.severity == Severity.ERROR }
    }
    val latestLog = state.logs.firstOrNull()
    val tone = when {
        runningProfile != null -> MetricTone.Route
        issueCount > 0 -> MetricTone.Warning
        latestLog?.status in listOf(RunStatus.FAILED, RunStatus.CANCELLED) -> MetricTone.Destructive
        latestLog?.status == RunStatus.SUCCESS -> MetricTone.Success
        else -> MetricTone.Neutral
    }
    val title = when {
        runningProfile != null -> "Backup running"
        issueCount > 0 -> "Setup needs attention"
        latestLog?.status == RunStatus.SUCCESS -> "Backups are current"
        latestLog != null -> "Last backup needs review"
        else -> "Ready to back up"
    }
    val detail = when {
        runningProfile != null -> runningProfile.name
        issueCount > 0 -> "$issueCount ${if (issueCount == 1) "profile has" else "profiles have"} blocking setup issues"
        latestLog != null -> latestLog.summary.ifBlank { latestLog.status.displayLabel() }
        else -> "Run a profile to record the first backup result"
    }
    SectionCard {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            EntityIcon(
                icon = when (tone) {
                    MetricTone.Success -> Icons.Outlined.CheckCircle
                    MetricTone.Warning -> Icons.Outlined.Warning
                    MetricTone.Destructive -> Icons.Outlined.Error
                    else -> Icons.Outlined.Dashboard
                },
                tone = tone,
                animated = runningProfile != null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                label = runningProfile?.let { "Running" } ?: latestLog?.status?.displayLabel() ?: "Ready",
                tone = tone,
                animated = runningProfile != null,
            )
        }
        DashboardOverviewGrid(
            state = state,
            latestLog = latestLog,
        )
    }
}

@Composable
internal fun DashboardOverviewGrid(state: AppState, latestLog: BackupLog?) {
    val lastSuccess = state.profiles.mapNotNull { it.status.lastSuccessAt }.maxOrNull()
    val nextRun = dashboardNextRunAt(state.profiles)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardOverviewMetric(
                icon = Icons.Outlined.Folder,
                label = "Profiles",
                value = profileCountLabel(state.profiles.size),
                modifier = Modifier.weight(1f),
            )
            DashboardOverviewMetric(
                icon = Icons.Outlined.Storage,
                label = "Targets",
                value = targetCountLabel(state.targets.size),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardOverviewMetric(
                icon = Icons.Outlined.CheckCircle,
                label = "Last success",
                value = lastSuccess?.let { formatRunTimestamp(it) } ?: latestLog?.let { it.status.displayLabel() } ?: "None yet",
                modifier = Modifier.weight(1f),
            )
            DashboardOverviewMetric(
                icon = Icons.Outlined.Schedule,
                label = "Next run",
                value = nextRun?.let { formatRunTimestamp(it) } ?: "No schedule",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

internal fun dashboardNextRunAt(
    profiles: List<BackupProfile>,
    now: LocalDateTime? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val currentNow = now ?: LocalDateTime.now(zone)
    return profiles
        .asSequence()
        .filter { it.schedule.type != ScheduleType.DISABLED }
        .mapNotNull { profile ->
            ScheduleTriggerCalculator.nextTriggerMillis(profile.schedule, currentNow, zone)
                ?.let { Instant.ofEpochMilli(it).toString() }
        }
        .minOrNull()
}

@Composable
internal fun DashboardOverviewMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun RecentActivitySection(logs: List<BackupLog>) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Recent activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (logs.isNotEmpty()) {
                StatusBadge("${logs.size.coerceAtMost(3)} shown", MetricTone.Neutral)
            }
        }
        if (logs.isEmpty()) {
            Text(
                "No backup results recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logs.take(3).forEach { log ->
                DashboardActivityRow(log)
            }
        }
    }
}

@Composable
internal fun DashboardActivityRow(log: BackupLog) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = when (log.status) {
                RunStatus.SUCCESS -> Icons.Outlined.CheckCircle
                RunStatus.WARNING -> Icons.Outlined.Warning
                RunStatus.FAILED, RunStatus.CANCELLED -> Icons.Outlined.Error
                else -> Icons.Outlined.Sync
            },
            contentDescription = null,
            tint = toneColor(log.status.tone()),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.profileName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatRunTimestamp(log.finishedAt ?: log.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                log.summary.ifBlank { log.status.displayLabel() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun RunProgressState.toRunProgressSummary(): RunProgressSummary =
    if (phase == RunProgressPhase.DRY_RUN) {
        RunProgressSummary(
            message = message ?: phase.notificationLabel(),
            metrics = emptyList(),
            fileLine = null,
            transferPercent = null,
            spinnerOnly = true,
        )
    } else {
        RunProgressSummary(
            message = message ?: phase.notificationLabel(),
            metrics = listOfNotNull(
                filesTransferred?.let { transferred ->
                    "Files" to (filesDiscovered?.let { discovered -> "$transferred/$discovered" } ?: transferred.toString())
                },
                bytesTransferredRaw?.let { "Transferred" to formatBytesUi(it) }
                    ?: bytesTransferred?.let { "Transferred" to it },
                plannedTransferBytesRaw?.let { "Planned" to formatBytesUi(it) },
                speed?.let { "Speed" to it },
                averageBytesPerSecond?.let { "Avg speed" to "${formatBytesUi(it)}/s" }
                    ?: recentAverageBytesPerSecond?.let { "Avg speed" to "${formatBytesUi(it)}/s" },
            ),
            fileLine = currentFile?.let { "Last: ${compactMiddleUi(it)}" },
            transferPercent = transferProgressPercent(),
        )
    }
