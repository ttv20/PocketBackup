@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ttv20.rsyncbackup.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.backup.BinaryPaths
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.backup.RsyncCommandBuilder
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.ConstraintSettings
import com.ttv20.rsyncbackup.model.ExportCodec
import com.ttv20.rsyncbackup.model.GlobalSettings
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.ProfileValidator
import com.ttv20.rsyncbackup.model.RemoteSafetySettings
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.ServerRecord
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.TailscaleStateMetadata
import com.ttv20.rsyncbackup.model.ThemePreference
import com.ttv20.rsyncbackup.model.toExportDocument
import com.ttv20.rsyncbackup.permissions.PermissionIntents
import com.ttv20.rsyncbackup.permissions.PermissionStateReader
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.ssh.ScannedHostKey
import com.ttv20.rsyncbackup.ssh.SshHostKeyScanner
import com.ttv20.rsyncbackup.ssh.SshKeyManager
import com.ttv20.rsyncbackup.ssh.SshPasswordSetupClient
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

private enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Outlined.Dashboard),
    Profiles("Profiles", Icons.Outlined.Folder),
    Servers("Servers", Icons.Outlined.Storage),
    Logs("Logs", Icons.Outlined.Article),
    SshKeys("SSH keys", Icons.Outlined.Key),
    Tailscale("Tailscale", Icons.Outlined.Cloud),
    Settings("Settings", Icons.Outlined.Settings),
}

private val MainScreens = listOf(Screen.Dashboard, Screen.Profiles, Screen.Servers, Screen.Logs)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RsyncBackupApp(
    repository: AppRepository,
    secretStore: SecretStore,
    requestedScreenName: String? = null,
    requestedScreenRequestId: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by repository.state.collectAsState()
    val initialPermissions = remember(context) { PermissionStateReader(context).read() }
    var permissions by remember(context) { mutableStateOf(initialPermissions) }
    val refreshPermissions = {
        permissions = PermissionStateReader(context).read()
    }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestedScreen = validScreenName(requestedScreenName)
    var permissionOnboardingActive by rememberSaveable {
        mutableStateOf(requestedScreen == null && !initialPermissions.allRequiredGranted)
    }
    var selectedScreen by rememberSaveable {
        mutableStateOf(
            requestedScreen ?: if (initialPermissions.allRequiredGranted) {
                Screen.Dashboard.name
            } else {
                Screen.Settings.name
            },
        )
    }
    var lastMainScreen by rememberSaveable { mutableStateOf(Screen.Dashboard.name) }
    val selectScreen: (Screen) -> Unit = { target ->
        val current = runCatching { Screen.valueOf(selectedScreen) }.getOrDefault(Screen.Dashboard)
        if (current in MainScreens) {
            lastMainScreen = current.name
        }
        selectedScreen = target.name
    }
    LaunchedEffect(requestedScreenName, requestedScreenRequestId) {
        validScreenName(requestedScreenName)?.let {
            val current = runCatching { Screen.valueOf(selectedScreen) }.getOrDefault(Screen.Dashboard)
            if (current in MainScreens) {
                lastMainScreen = current.name
            }
            selectedScreen = it
            permissionOnboardingActive = false
        }
    }
    LaunchedEffect(permissions.allRequiredGranted, permissionOnboardingActive) {
        if (permissionOnboardingActive && permissions.allRequiredGranted) {
            selectedScreen = Screen.Dashboard.name
            permissionOnboardingActive = false
        }
    }
    val screen = Screen.valueOf(selectedScreen)
    val backTarget = when (screen) {
        Screen.Settings -> runCatching { Screen.valueOf(lastMainScreen) }.getOrDefault(Screen.Dashboard)
        Screen.SshKeys, Screen.Tailscale -> Screen.Settings
        else -> null
    }

    RsyncBackupTheme(themePreference = state.settings.themePreference) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        MainScreens.forEach { item ->
                            NavigationRailItem(
                                selected = item == screen,
                                onClick = { selectScreen(item) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                            )
                        }
                    }
                    AppScaffold(
                        screen = screen,
                        state = state,
                        permissions = permissions,
                        repository = repository,
                        secretStore = secretStore,
                        compactNav = false,
                        onSelect = selectScreen,
                        onBack = backTarget?.let { target -> { selectedScreen = target.name } },
                        onRefreshPermissions = refreshPermissions,
                    )
                }
            } else {
                AppScaffold(
                    screen = screen,
                    state = state,
                    permissions = permissions,
                    repository = repository,
                    secretStore = secretStore,
                    compactNav = true,
                    onSelect = selectScreen,
                    onBack = backTarget?.let { target -> { selectedScreen = target.name } },
                    onRefreshPermissions = refreshPermissions,
                )
            }
        }
    }
}

private fun validScreenName(name: String?): String? =
    name?.let { value ->
        if (value.equals("Permissions", ignoreCase = true)) return@let Screen.Settings.name
        if (value.equals("Run", ignoreCase = true)) return@let Screen.Dashboard.name
        Screen.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true)
        }?.name
    }

private fun Uri.toSharedStoragePath(): String? =
    runCatching { DocumentsContract.getTreeDocumentId(this) }
        .getOrNull()
        ?.let(::sharedStoragePathFromTreeDocumentId)

internal fun sharedStoragePathFromTreeDocumentId(treeDocumentId: String): String? {
    val parts = treeDocumentId.split(":", limit = 2)
    val volume = parts.firstOrNull()?.lowercase() ?: return null
    val relativePath = parts.getOrNull(1).orEmpty().trim('/')
    val root = when (volume) {
        "primary" -> "/storage/emulated/0"
        "home" -> "/storage/emulated/0/Documents"
        else -> return null
    }
    return if (relativePath.isBlank()) root else "$root/$relativePath"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    screen: Screen,
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    repository: AppRepository,
    secretStore: SecretStore,
    compactNav: Boolean,
    onSelect: (Screen) -> Unit,
    onBack: (() -> Unit)?,
    onRefreshPermissions: () -> Unit,
) {
    var detailScreenActive by rememberSaveable { mutableStateOf(false) }
    var detailBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(screen) {
        detailScreenActive = false
        detailBackHandler = null
    }
    val activeBack = detailBackHandler ?: onBack
    BackHandler(enabled = activeBack != null) {
        activeBack?.invoke()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    activeBack?.let { back ->
                        IconButton(onClick = back) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Text("PocketSync")
                },
                actions = {
                    if (screen != Screen.Settings) {
                        IconButton(onClick = { onSelect(Screen.Settings) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (compactNav && screen in MainScreens && !detailScreenActive) {
                PhoneBottomNavigation(selected = screen, onSelect = onSelect)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (screen) {
                Screen.Dashboard -> DashboardScreen(state, onRun = { BackupService.start(it.context, it.profileId) })
                Screen.Profiles -> ProfilesScreen(
                    state,
                    repository,
                    onDetailActiveChange = { active, back ->
                        detailScreenActive = active
                        detailBackHandler = back
                    },
                )
                Screen.Servers -> ServersScreen(
                    state,
                    repository,
                    secretStore,
                    onDetailActiveChange = { active, back ->
                        detailScreenActive = active
                        detailBackHandler = back
                    },
                )
                Screen.SshKeys -> SshKeysScreen(state, repository, secretStore)
                Screen.Tailscale -> TailscaleScreen(state, repository, secretStore)
                Screen.Logs -> LogsScreen(state, repository)
                Screen.Settings -> SettingsScreen(state, permissions, repository, onRefreshPermissions, onSelect)
            }
        }
    }
}

private data class RunRequest(val context: Context, val profileId: String)

private data class QueueProgressSummary(
    val message: String,
    val metrics: List<Pair<String, String>>,
    val fileLine: String?,
)

private fun AppState.queueJobCount(): Int =
    queue.queuedProfileIds.size + if (queue.runningProfileId != null) 1 else 0

private fun jobCountLabel(count: Int): String =
    "$count ${if (count == 1) "job" else "jobs"}"

@Composable
private fun PhoneBottomNavigation(selected: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        MainScreens.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun DashboardScreen(state: AppState, onRun: (RunRequest) -> Unit) {
    val context = LocalContext.current
    val readyCount = state.profiles.count { profile ->
        ProfileValidator.validate(profile, state).none { it.severity == Severity.ERROR }
    }
    val warningCount = state.profiles.count { profile ->
        ProfileValidator.validate(profile, state).any { it.severity == Severity.WARNING }
    }
    val queueJobCount = state.queueJobCount()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CompactMetricStrip(
                metrics = listOf(
                    MetricSpec("Up to date", readyCount.toString(), Icons.Outlined.CheckCircle, MetricTone.Success),
                    MetricSpec("Warnings", warningCount.toString(), Icons.Outlined.Warning, MetricTone.Warning),
                    MetricSpec("Queue", queueJobCount.toString(), Icons.Outlined.Sync, MetricTone.Route),
                    MetricSpec("Servers", state.servers.size.toString(), Icons.Outlined.Storage, MetricTone.Neutral),
                ),
            )
        }
        item {
            SectionHeader("Dashboard", "Profiles, queue, and recent backup state")
        }
        items(state.profiles) { profile ->
            val issues = ProfileValidator.validate(profile, state)
            val liveProgress = state.runProgress.takeIf { it.profileId == profile.id }
            val isRunningProfile = state.queue.runningProfileId == profile.id
            val server = state.servers.firstOrNull { it.id == profile.serverId }
            ProfileListRow(
                profile = profile,
                server = server,
                issues = issues,
                showRunStatus = true,
                liveProgress = liveProgress,
                trailing = {
                    Button(
                        onClick = {
                            if (isRunningProfile) BackupService.cancel(context) else onRun(RunRequest(context, profile.id))
                        },
                        enabled = isRunningProfile || issues.none { it.severity == Severity.ERROR },
                        modifier = Modifier.testTag("dashboard-run-profile-${profile.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            if (isRunningProfile) Icons.Outlined.Error else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRunningProfile) "Stop" else "Run")
                    }
                },
            )
        }
        item {
            QueueSection(state)
        }
    }
}

@Composable
private fun QueueSection(state: AppState) {
    SectionCard {
        val jobCount = state.queueJobCount()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(jobCountLabel(jobCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.queue.runningProfileId == null && state.queue.queuedProfileIds.isEmpty()) {
            Text("No backup jobs waiting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.queue.runningProfileId?.let { runningProfileId ->
                val runningProfile = state.profiles.firstOrNull { it.id == runningProfileId }
                val progress = state.runProgress.takeIf {
                    it.profileId == runningProfileId && it.phase != RunProgressPhase.IDLE
                }
                val progressSummary = progress?.toQueueProgressSummary()
                QueueRow(
                    label = "Running",
                    name = progress?.profileName ?: runningProfile?.name ?: runningProfileId,
                    detail = progressSummary?.message ?: runningProfile?.status?.lastMessage ?: "In progress",
                    metrics = progressSummary?.metrics.orEmpty(),
                    fileLine = progressSummary?.fileLine,
                )
            }
            state.queue.queuedProfileIds.forEach { id ->
                QueueRow("Waiting", state.profiles.firstOrNull { it.id == id }?.name ?: id, "Queued")
            }
        }
    }
}

private fun RunProgressState.toQueueProgressSummary(): QueueProgressSummary =
    QueueProgressSummary(
        message = message ?: phase.notificationLabel(),
        metrics = listOfNotNull(
            filesTransferred?.let { transferred ->
                "Files" to (filesDiscovered?.let { discovered -> "$transferred/$discovered" } ?: transferred.toString())
            },
            bytesTransferredRaw?.let { "Transferred" to formatBytesUi(it) }
                ?: bytesTransferred?.let { "Transferred" to it },
            speed?.let { "Speed" to it },
            averageBytesPerSecond?.let { "Avg speed" to "${formatBytesUi(it)}/s" }
                ?: recentAverageBytesPerSecond?.let { "Avg speed" to "${formatBytesUi(it)}/s" },
        ),
        fileLine = currentFile?.let { "Last: ${compactMiddleUi(it)}" },
    )

@Composable
private fun QueueRow(
    label: String,
    name: String,
    detail: String,
    metrics: List<Pair<String, String>> = emptyList(),
    fileLine: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusBadge(label, MetricTone.Route)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (metrics.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.forEach { (metricLabel, value) ->
                        ProgressMetric(metricLabel, value)
                    }
                }
            }
            fileLine?.let { line ->
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
}

private enum class MetricTone {
    Success,
    Warning,
    Destructive,
    Route,
    Neutral,
}

private data class MetricSpec(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tone: MetricTone,
)

@Composable
private fun CompactMetricStrip(metrics: List<MetricSpec>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        metrics.forEach { metric ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            metric.icon,
                            contentDescription = null,
                            tint = toneColor(metric.tone),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        metric.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileListRow(
    profile: BackupProfile,
    server: ServerRecord?,
    issues: List<com.ttv20.rsyncbackup.model.ValidationIssue>,
    modifier: Modifier = Modifier,
    showRunStatus: Boolean = false,
    liveProgress: RunProgressState? = null,
    trailing: @Composable () -> Unit,
) {
    SectionCard(
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            EntityIcon(Icons.Outlined.Folder, if (issues.any { it.severity == Severity.ERROR }) MetricTone.Warning else MetricTone.Route)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(profile.sourcePath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProfileRouteLine(profile, server)
                LastNextLine(profile)
                if (showRunStatus) {
                    DashboardRunStatusLine(profile, liveProgress)
                }
                conciseIssueText(issues)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = toneColor(if (issues.any { issue -> issue.severity == Severity.ERROR }) MetricTone.Destructive else MetricTone.Warning),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
    }
}

@Composable
private fun ServerListRow(
    server: ServerRecord,
    trusted: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    SectionCard(
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            EntityIcon(Icons.Outlined.Storage, if (trusted) MetricTone.Success else MetricTone.Warning)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(server.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${server.user}@${server.lanHost}:${server.port}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RouteSummaryLine("LAN", server.lanHost, MetricTone.Route)
                server.tailscaleHost?.let { RouteSummaryLine("Tailscale", it, MetricTone.Route) }
                RouteSummaryLine("Fingerprint", if (trusted) "Trusted" else "Needs fingerprint", if (trusted) MetricTone.Success else MetricTone.Warning)
            }
            Spacer(Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
    }
}

@Composable
private fun EntityIcon(icon: ImageVector, tone: MetricTone) {
    Surface(
        color = toneContainerColor(tone),
        contentColor = toneOnContainerColor(tone),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
                .size(22.dp),
        )
    }
}

@Composable
private fun ProfileRouteLine(profile: BackupProfile, server: ServerRecord?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(
            listOfNotNull(server?.name ?: "Missing server", routeModeLabel(profile.targetMode)).joinToString(" - "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LastNextLine(profile: BackupProfile) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Last: ${profile.status.lastSuccessAt ?: profile.status.lastRunAt ?: "Never"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("Next: ${profile.status.nextRunAt ?: scheduleLabel(profile.schedule)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DashboardRunStatusLine(profile: BackupProfile, liveProgress: RunProgressState?) {
    val live = liveProgress?.takeIf { it.phase != RunProgressPhase.IDLE }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusBadge(
            label = live?.let { phaseLabel(it.phase) } ?: profile.status.lastStatus.name.lowercase(),
            tone = live?.let { MetricTone.Route } ?: profile.status.lastStatus.tone(),
        )
    }
}

@Composable
private fun RouteSummaryLine(label: String, value: String, tone: MetricTone) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RouteChip(label, tone)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusBadge(label: String, tone: MetricTone) {
    Surface(
        color = toneContainerColor(tone),
        contentColor = toneOnContainerColor(tone),
        shape = RoundedCornerShape(50),
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
private fun RouteChip(label: String, tone: MetricTone = MetricTone.Route) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = toneColor(tone),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun AddRow(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
private fun EmptyActionRow(title: String, action: String, icon: ImageVector, onClick: () -> Unit) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onClick) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(action)
        }
    }
}

@Composable
private fun EditorHeader(
    title: String,
    detail: String,
    onBack: () -> Unit,
    backLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saveButtonTag: String,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    secondaryActionIcon: ImageVector? = null,
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
            SectionHeader(title, detail)
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
                    Text("Save")
                }
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    OutlinedButton(onClick = onSecondaryAction) {
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
private fun UnsavedChangesDialog(
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
private fun toneColor(tone: MetricTone) = when (tone) {
    MetricTone.Success -> MaterialTheme.colorScheme.primary
    MetricTone.Warning -> MaterialTheme.colorScheme.tertiary
    MetricTone.Destructive -> MaterialTheme.colorScheme.error
    MetricTone.Route -> MaterialTheme.colorScheme.primary
    MetricTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun toneContainerColor(tone: MetricTone) = when (tone) {
    MetricTone.Success -> MaterialTheme.colorScheme.primaryContainer
    MetricTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
    MetricTone.Destructive -> MaterialTheme.colorScheme.errorContainer
    MetricTone.Route -> MaterialTheme.colorScheme.secondaryContainer
    MetricTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun toneOnContainerColor(tone: MetricTone) = when (tone) {
    MetricTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
    MetricTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
    MetricTone.Destructive -> MaterialTheme.colorScheme.onErrorContainer
    MetricTone.Route -> MaterialTheme.colorScheme.onSecondaryContainer
    MetricTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun conciseIssueText(
    issues: List<com.ttv20.rsyncbackup.model.ValidationIssue>,
): String? = issues.firstOrNull()?.message

private fun routeModeLabel(targetMode: TargetMode): String =
    when (targetMode) {
        TargetMode.LAN_ONLY -> "LAN only"
        TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> "LAN first"
        TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> "Tailscale first"
        TargetMode.TAILSCALE_ONLY -> "Tailscale only"
    }

private fun scheduleLabel(schedule: BackupSchedule): String =
    when (schedule.type) {
        ScheduleType.DISABLED -> "Disabled"
        ScheduleType.EXACT_DAILY -> "Daily, ${schedule.timeLocal}"
        ScheduleType.BEST_EFFORT_DAILY -> "Best effort, ${schedule.timeLocal}"
    }

@Composable
private fun ProfilesScreen(
    state: AppState,
    repository: AppRepository,
    onDetailActiveChange: (Boolean, (() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val scheduler = remember(context) { BackupScheduler(context) }
    var compactEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editorProfile by remember { mutableStateOf<BackupProfile?>(null) }
    var editorIsDraft by rememberSaveable { mutableStateOf(false) }
    var editorBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeEditor = {
        editorProfile = null
        editorIsDraft = false
        editorBackHandler = null
        onDetailActiveChange(false, null)
        compactEditorOpen = false
    }
    val addProfile: () -> Unit = {
        state.servers.firstOrNull()?.let { server ->
            onDetailActiveChange(true, closeEditor)
            editorIsDraft = true
            editorProfile = BackupProfile(
                id = UUID.randomUUID().toString(),
                name = "New profile",
                serverId = server.id,
                remotePath = server.defaultRemotePath,
                targetMode = if (server.tailscaleHost.isNullOrBlank()) {
                    TargetMode.LAN_ONLY
                } else {
                    TargetMode.LAN_FIRST_TAILSCALE_FALLBACK
                },
                excludes = state.profiles.firstOrNull()?.excludes.orEmpty(),
            )
            compactEditorOpen = true
        }
    }
    val addServerFromProfile: () -> ServerRecord = {
        val server = defaultServer("New server", state.servers.size + 1)
        repository.upsertServer(server)
        server
    }
    SideEffect {
        onDetailActiveChange(compactEditorOpen, if (compactEditorOpen) editorBackHandler ?: closeEditor else null)
    }
    DisposableEffect(Unit) {
        onDispose { onDetailActiveChange(false, null) }
    }
    val editingProfile = editorProfile
    if (compactEditorOpen && editingProfile != null) {
        val isDraft = editorIsDraft
        ProfileEditor(
            state = state,
            profile = editingProfile,
            onSave = {
                repository.upsertProfile(it)
                scheduler.schedule(it)
                closeEditor()
            },
            onDelete = {
                if (!isDraft) {
                    scheduler.cancel(editingProfile.id)
                    repository.removeProfile(editingProfile.id)
                }
                closeEditor()
            },
            onAddServer = addServerFromProfile,
            onBack = closeEditor,
            onBackHandlerChange = { editorBackHandler = it },
            isDraft = isDraft,
            deleteLabel = if (isDraft) "Cancel" else "Delete",
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionHeader("Profiles", "Backup profiles")
                }
                items(state.profiles, key = { it.id }) { profile ->
                    val server = state.servers.firstOrNull { it.id == profile.serverId }
                    val issues = ProfileValidator.validate(profile, state)
                    ProfileListRow(
                        profile = profile,
                        server = server,
                        issues = issues,
                        trailing = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { BackupService.start(context, profile.id) },
                                    enabled = issues.none { it.severity == Severity.ERROR },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Run")
                                }
                                OutlinedButton(
                                    onClick = {
                                        editorIsDraft = false
                                        editorProfile = profile
                                        onDetailActiveChange(true, closeEditor)
                                        compactEditorOpen = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit")
                                }
                            }
                        },
                    )
                }
                if (state.profiles.isEmpty()) {
                    item {
                        EmptyActionRow("No profiles yet", "Add profile", Icons.Outlined.Add, addProfile)
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = addProfile,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add profile") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(16.dp)
                    .testTag("profiles-add-button"),
            )
        }
    }
}

@Composable
private fun ProfileEditor(
    state: AppState,
    profile: BackupProfile,
    onSave: (BackupProfile) -> Unit,
    onDelete: () -> Unit,
    onAddServer: () -> ServerRecord,
    onBack: (() -> Unit)? = null,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    isDraft: Boolean,
    deleteLabel: String = "Delete",
    modifier: Modifier = Modifier,
) {
    var editing by remember(profile.id, profile) { mutableStateOf(profile) }
    var sourcePickerError by remember(profile.id) { mutableStateOf<String?>(null) }
    var pendingSaveWarnings by remember(profile.id) {
        mutableStateOf<List<com.ttv20.rsyncbackup.model.ValidationIssue>>(emptyList())
    }
    var showUnsavedPrompt by rememberSaveable(profile.id) { mutableStateOf(false) }
    val issues = ProfileValidator.validate(editing, state)
    val canSave = issues.none { it.severity == Severity.ERROR }
    val hasUnsavedChanges = isDraft || editing != profile
    val saveProfile = {
        val sanitized = editing.copy(remoteSafety = RemoteSafetySettings())
        val warnings = ProfileValidator.saveWarnings(sanitized, state)
        if (warnings.isEmpty()) {
            pendingSaveWarnings = emptyList()
            onSave(sanitized)
        } else {
            pendingSaveWarnings = warnings
        }
    }
    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val path = uri.toSharedStoragePath()
        if (path == null) {
            sourcePickerError = "Selected folder is not a primary shared-storage path; enter a raw path."
        } else {
            editing = editing.copy(sourcePath = path)
            sourcePickerError = null
        }
    }
    val requestBackState = rememberUpdatedState<() -> Unit> {
        if (hasUnsavedChanges) {
            showUnsavedPrompt = true
        } else {
            onBack?.invoke()
            Unit
        }
    }
    DisposableEffect(Unit) {
        val handler = { requestBackState.value.invoke() }
        onBackHandlerChange(handler)
        onDispose { onBackHandlerChange(null) }
    }

    if (showUnsavedPrompt) {
        UnsavedChangesDialog(
            entityName = "profile",
            saveEnabled = canSave,
            onSave = {
                showUnsavedPrompt = false
                saveProfile()
            },
            onDiscard = {
                showUnsavedPrompt = false
                onBack?.invoke()
            },
            onDismiss = { showUnsavedPrompt = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        EditorHeader(
            title = if (isDraft) "New Profile" else "Profile Edit",
            detail = editing.name,
            onBack = { requestBackState.value.invoke() },
            backLabel = "Back",
            onSave = saveProfile,
            saveEnabled = canSave,
            saveButtonTag = "profile-save-button",
            onSecondaryAction = onDelete,
            secondaryActionLabel = deleteLabel,
            secondaryActionIcon = Icons.Outlined.Delete,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("profile-editor-scroll")
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IssueList(issues)
            if (pendingSaveWarnings.isNotEmpty()) {
                SectionCard {
                    Text("Save warning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IssueList(pendingSaveWarnings)
                    Button(
                        onClick = {
                            val sanitized = editing.copy(remoteSafety = RemoteSafetySettings())
                            pendingSaveWarnings = emptyList()
                            onSave(sanitized)
                        },
                        modifier = Modifier.testTag("profile-save-anyway-button"),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save anyway")
                    }
                }
            }
            SectionCard {
                Text("Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(editing.name, { editing = editing.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        editing.sourcePath,
                        {
                            editing = editing.copy(sourcePath = it)
                            sourcePickerError = null
                        },
                        label = { Text("Source path") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile-source-path-field"),
                    )
                    OutlinedButton(onClick = { sourcePicker.launch(null) }) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick")
                    }
                }
                sourcePickerError?.let { ErrorText(it) }
            }
            SectionCard {
                Text("Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Selector("Server") {
                    state.servers.forEach { server ->
                        FilterChip(
                            selected = editing.serverId == server.id,
                            onClick = { editing = editing.copy(serverId = server.id, remotePath = server.defaultRemotePath) },
                            label = { Text(server.name) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val server = onAddServer()
                            editing = editing.copy(serverId = server.id, remotePath = server.defaultRemotePath)
                        },
                        label = { Text("Add server") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.testTag("profile-add-server-button"),
                    )
                }
                OutlinedTextField(
                    editing.remotePath,
                    { editing = editing.copy(remotePath = it) },
                    label = { Text("Remote path") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-remote-path-field"),
                )
                TargetModeSelector(editing.targetMode) { editing = editing.copy(targetMode = it) }
            }
            SectionCard {
                Text("Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ScheduleEditor(editing.schedule) { editing = editing.copy(schedule = it) }
            }
            ConstraintEditor(editing.constraints) { editing = editing.copy(constraints = it) }
            SectionCard {
                Text("Safety", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                WarningRow("Delete remote files not present locally", "Deletes server files that are not present in the source.", editing.deleteEnabled) {
                    editing = editing.copy(deleteEnabled = it)
                }
            }
            SectionCard {
                Text("Advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = editing.excludes,
                    onValueChange = { editing = editing.copy(excludes = it) },
                    label = { Text("Excludes") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editing.advancedArgs,
                    onValueChange = { editing = editing.copy(advancedArgs = it) },
                    label = { Text("Advanced rsync args") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CommandPreview(state, editing)
        }
    }
}

@Composable
private fun ServersScreen(
    state: AppState,
    repository: AppRepository,
    secretStore: SecretStore,
    onDetailActiveChange: (Boolean, (() -> Unit)?) -> Unit,
) {
    var compactEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editorServer by remember { mutableStateOf<ServerRecord?>(null) }
    var editorIsDraft by rememberSaveable { mutableStateOf(false) }
    var editorBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeEditor = {
        editorServer = null
        editorIsDraft = false
        editorBackHandler = null
        onDetailActiveChange(false, null)
        compactEditorOpen = false
    }
    val addServer = {
        onDetailActiveChange(true, closeEditor)
        editorIsDraft = true
        editorServer = defaultServer("New server", state.servers.size + 1)
        compactEditorOpen = true
    }
    SideEffect {
        onDetailActiveChange(compactEditorOpen, if (compactEditorOpen) editorBackHandler ?: closeEditor else null)
    }
    DisposableEffect(Unit) {
        onDispose { onDetailActiveChange(false, null) }
    }
    val editingServer = editorServer
    if (compactEditorOpen && editingServer != null) {
        val isDraft = editorIsDraft
        ServerEditor(
            state = state,
            server = editingServer,
            repository = repository,
            secretStore = secretStore,
            onSave = {
                repository.upsertServer(it)
                closeEditor()
            },
            onBack = closeEditor,
            onBackHandlerChange = { editorBackHandler = it },
            isDraft = isDraft,
            cancelLabel = if (isDraft) "Cancel" else "Back",
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionHeader("Servers", "Backup servers")
                }
                items(state.servers, key = { it.id }) { server ->
                    val trusted = state.trustedHostFingerprints.any {
                        it.serverId == server.id || it.serverId == server.fingerprintGroupId
                    }
                    ServerListRow(
                        server = server,
                        trusted = trusted,
                        trailing = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                StatusBadge(if (trusted) "Reachable" else "Needs fingerprint", if (trusted) MetricTone.Success else MetricTone.Warning)
                                FilledTonalButton(
                                    onClick = {
                                        editorIsDraft = false
                                        editorServer = server
                                        onDetailActiveChange(true, closeEditor)
                                        compactEditorOpen = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit")
                                }
                            }
                        },
                    )
                }
                if (state.servers.isEmpty()) {
                    item {
                        EmptyActionRow("No servers yet", "Add server", Icons.Outlined.Add, addServer)
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = addServer,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add server") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(16.dp)
                    .testTag("servers-add-button"),
            )
        }
    }
}

private fun defaultServer(baseName: String, sequence: Int): ServerRecord =
    ServerRecord(
        id = UUID.randomUUID().toString(),
        name = if (sequence <= 1) baseName else "$baseName $sequence",
        user = "ttv20",
        lanHost = "192.168.3.200",
        defaultRemotePath = "/mnt/backup/phone",
    )

@Composable
private fun ServerEditor(
    state: AppState,
    server: ServerRecord,
    repository: AppRepository,
    secretStore: SecretStore,
    onSave: (ServerRecord) -> Unit,
    onBack: (() -> Unit)? = null,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    isDraft: Boolean,
    cancelLabel: String = "Back",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember(server.id, server) { mutableStateOf(server) }
    var pendingHostKeys by remember(server.id) { mutableStateOf<List<ScannedHostKey>>(emptyList()) }
    var scanTarget by remember(server.id) { mutableStateOf<String?>(null) }
    var scanError by remember(server.id) { mutableStateOf<String?>(null) }
    var setupPassword by remember(server.id) { mutableStateOf("") }
    var setupTarget by remember(server.id) { mutableStateOf<String?>(null) }
    var setupMessage by remember(server.id) { mutableStateOf<String?>(null) }
    var setupError by remember(server.id) { mutableStateOf<String?>(null) }
    var showUnsavedPrompt by rememberSaveable(server.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val hasUnsavedChanges = isDraft || editing != server
    val requestBackState = rememberUpdatedState<() -> Unit> {
        if (hasUnsavedChanges) {
            showUnsavedPrompt = true
        } else {
            onBack?.invoke()
            Unit
        }
    }
    val trustedEntries = state.trustedHostFingerprints.filter {
        it.serverId == editing.id || it.serverId == editing.fingerprintGroupId
    }
    val setupPrerequisiteMessage = remember(
        setupPassword,
        state.sshKeySettings.publicKey,
        trustedEntries,
    ) {
        when {
            setupPassword.isBlank() -> "Enter the one-time SSH password to enable setup."
            state.sshKeySettings.publicKey == null -> "Generate or store an SSH public key before setup."
            trustedEntries.isEmpty() -> "Scan and trust this server host key before setup."
            else -> null
        }
    }
    LaunchedEffect(pendingHostKeys, scanError, setupMessage, setupError) {
        if (pendingHostKeys.isNotEmpty() || scanError != null || setupMessage != null || setupError != null) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    DisposableEffect(Unit) {
        val handler = { requestBackState.value.invoke() }
        onBackHandlerChange(handler)
        onDispose { onBackHandlerChange(null) }
    }

    if (showUnsavedPrompt) {
        UnsavedChangesDialog(
            entityName = "server",
            saveEnabled = true,
            onSave = {
                showUnsavedPrompt = false
                onSave(editing)
            },
            onDiscard = {
                showUnsavedPrompt = false
                onBack?.invoke()
            },
            onDismiss = { showUnsavedPrompt = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        EditorHeader(
            title = if (isDraft) "New Server" else "Server Edit",
            detail = editing.name,
            onBack = { requestBackState.value.invoke() },
            backLabel = cancelLabel,
            onSave = { onSave(editing) },
            saveEnabled = true,
            saveButtonTag = "server-save-button",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("server-editor-scroll")
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
            Text("Identity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(editing.name, { editing = editing.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                editing.user,
                { editing = editing.copy(user = it) },
                label = { Text("User") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server-user-field"),
            )
        }
        SectionCard {
            Text("Addresses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                editing.lanHost,
                { editing = editing.copy(lanHost = it) },
                label = { Text("Primary LAN host") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server-lan-host-field"),
            )
            OutlinedTextField(editing.tailscaleHost.orEmpty(), { editing = editing.copy(tailscaleHost = it.ifBlank { null }) }, label = { Text("Fallback Tailscale host") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = editing.port.toString(),
                onValueChange = { value -> editing = editing.copy(port = value.toIntOrNull()?.coerceIn(1, 65535) ?: editing.port) },
                label = { Text("Port") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server-port-field"),
            )
            OutlinedTextField(
                editing.defaultRemotePath,
                { editing = editing.copy(defaultRemotePath = it) },
                label = { Text("Default remote path") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server-default-remote-path-field"),
            )
        }
        SectionCard {
            Text("Trusted fingerprint", style = MaterialTheme.typography.titleMedium)
            Text("LAN and Tailscale addresses share fingerprint group ${editing.fingerprintGroupId}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    enabled = scanTarget == null && editing.lanHost.isNotBlank(),
                    modifier = Modifier.testTag("server-scan-lan-button"),
                    onClick = {
                        scanTarget = "LAN"
                        scanError = null
                        pendingHostKeys = emptyList()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    SshHostKeyScanner(context).scanAll(editing.lanHost, editing.port)
                                }
                            }.onSuccess {
                                pendingHostKeys = it
                            }.onFailure {
                                scanError = it.message
                            }
                            scanTarget = null
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (scanTarget == "LAN") "Scanning" else "Scan LAN")
                }
                FilledTonalButton(
                    enabled = scanTarget == null && !editing.tailscaleHost.isNullOrBlank(),
                    onClick = {
                        val host = editing.tailscaleHost ?: return@FilledTonalButton
                        scanTarget = "Tailscale"
                        scanError = null
                        pendingHostKeys = emptyList()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    require(state.tailscale.isConfigured && state.tailscale.stateSecretAlias != null) {
                                        "Configure Tailscale before scanning a Tailscale host."
                                    }
                                    TailscaleManager(context, secretStore).withRestoredState(state.tailscale.stateSecretAlias) { stateDir ->
                                        SshHostKeyScanner(context).scanAllOverTailscale(
                                            hostname = host,
                                            port = editing.port,
                                            user = editing.user,
                                            tailscaleStateDir = stateDir,
                                            tailscaleNodeName = state.tailscale.nodeName,
                                        )
                                    }
                                }
                            }.onSuccess {
                                pendingHostKeys = it
                            }.onFailure {
                                scanError = it.message
                            }
                            scanTarget = null
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (scanTarget == "Tailscale") "Scanning" else "Scan Tailscale")
                }
            }
            if (pendingHostKeys.isNotEmpty()) {
                SelectableBlock(
                    pendingHostKeys.joinToString("\n\n") { scanned ->
                        "${scanned.hostname}:${scanned.port}\n${scanned.algorithm}\n${scanned.fingerprint}"
                    },
                )
                Button(
                    modifier = Modifier.testTag("server-trust-scanned-key-button"),
                    onClick = {
                        val trusted = pendingHostKeys.map { scanned ->
                            com.ttv20.rsyncbackup.model.TrustedHostFingerprint(
                                id = UUID.randomUUID().toString(),
                                serverId = editing.fingerprintGroupId,
                                hostnames = listOf(scanned.hostname),
                                port = scanned.port,
                                algorithm = scanned.algorithm,
                                fingerprint = scanned.fingerprint,
                                publicKey = scanned.publicKey,
                                confirmedAt = Instant.now().toString(),
                            )
                        }
                        repository.update { appState ->
                            appState.copy(
                                servers = appState.servers.filterNot { it.id == editing.id } + editing,
                                trustedHostFingerprints = appState.trustedHostFingerprints
                                    .filterNot { existing ->
                                        pendingHostKeys.any { scanned ->
                                            existing.serverId == editing.fingerprintGroupId &&
                                                existing.hostnames.contains(scanned.hostname) &&
                                                existing.port == scanned.port &&
                                                existing.algorithm == scanned.algorithm
                                        }
                                    } + trusted,
                            )
                        }
                        pendingHostKeys = emptyList()
                        scanError = null
                    },
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Trust scanned key")
                }
            }
            if (trustedEntries.isNotEmpty()) {
                Text("Trusted host keys", style = MaterialTheme.typography.labelLarge)
                trustedEntries.forEach { entry ->
                    SelectableBlock("${entry.hostnames.joinToString()}:${entry.port}\n${entry.algorithm}\n${entry.fingerprint}")
                }
            }
            scanError?.let { ErrorText(it) }
        }
        SectionCard {
            Text("One-time password setup", style = MaterialTheme.typography.titleMedium)
            Text("Installs the configured public key into ~/.ssh/authorized_keys and discards the password after the attempt.")
            OutlinedTextField(
                value = setupPassword,
                onValueChange = { setupPassword = it },
                label = { Text("SSH password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server-setup-password-field"),
            )
            setupPrerequisiteMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = setupTarget == null && setupPassword.isNotBlank() && editing.lanHost.isNotBlank(),
                    modifier = Modifier.testTag("server-install-over-lan-button"),
                    onClick = {
                        val publicKey = state.sshKeySettings.publicKey
                        if (publicKey == null) {
                            setupError = "Generate or store an SSH public key before setup."
                            return@Button
                        }
                        if (trustedEntries.isEmpty()) {
                            setupError = "Scan and trust this server host key before setup."
                            return@Button
                        }
                        val password = setupPassword
                        setupTarget = "LAN"
                        setupMessage = null
                        setupError = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    SshPasswordSetupClient().installPublicKey(
                                        server = editing,
                                        trustedHostFingerprints = state.trustedHostFingerprints,
                                        publicKey = publicKey,
                                        password = password,
                                        workDir = context.cacheDir,
                                        hostname = editing.lanHost,
                                    )
                                }
                            }.onSuccess { result ->
                                if (result.isSuccess) {
                                    setupPassword = ""
                                    setupMessage = "Public key installed over LAN"
                                } else {
                                    setupError = result.output.ifBlank { "Password setup failed with exit ${result.exitStatus}" }
                                }
                            }.onFailure {
                                setupError = it.message
                            }
                            setupTarget = null
                        }
                    },
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (setupTarget == "LAN") "Installing" else "Install over LAN")
                }
                OutlinedButton(
                    enabled = setupTarget == null && setupPassword.isNotBlank() && !editing.tailscaleHost.isNullOrBlank(),
                    onClick = {
                        val publicKey = state.sshKeySettings.publicKey
                        if (publicKey == null) {
                            setupError = "Generate or store an SSH public key before setup."
                            return@OutlinedButton
                        }
                        if (trustedEntries.isEmpty()) {
                            setupError = "Scan and trust this server host key before setup."
                            return@OutlinedButton
                        }
                        if (!state.tailscale.isConfigured || state.tailscale.stateSecretAlias == null) {
                            setupError = "Configure Tailscale before installing over Tailscale."
                            return@OutlinedButton
                        }
                        val host = editing.tailscaleHost ?: return@OutlinedButton
                        val password = setupPassword
                        setupTarget = "Tailscale"
                        setupMessage = null
                        setupError = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    TailscaleManager(context, secretStore).withRestoredState(state.tailscale.stateSecretAlias) { stateDir ->
                                        val nativeInstall = NativeBinaryManager(context).ensureInstalled()
                                        require(nativeInstall.isComplete) {
                                            "Missing native binaries: ${nativeInstall.missing.joinToString()}"
                                        }
                                        SshPasswordSetupClient().installPublicKeyWithNativeSsh(
                                            server = editing,
                                            trustedHostFingerprints = state.trustedHostFingerprints,
                                            publicKey = publicKey,
                                            password = password,
                                            workDir = context.cacheDir,
                                            filesDir = context.filesDir,
                                            tsnetHelperPath = nativeInstall.paths.tsnetHelper,
                                            tailscaleStateDir = stateDir,
                                            tailscaleNodeName = state.tailscale.nodeName,
                                            hostname = host,
                                        )
                                    }
                                }
                            }.onSuccess { result ->
                                if (result.isSuccess) {
                                    setupPassword = ""
                                    setupMessage = "Public key installed over Tailscale"
                                } else {
                                    setupError = result.output.ifBlank { "Password setup failed with exit ${result.exitStatus}" }
                                }
                            }.onFailure {
                                setupError = it.message
                            }
                            setupTarget = null
                        }
                    },
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (setupTarget == "Tailscale") "Installing" else "Install over Tailscale")
                }
            }
            setupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            setupError?.let { ErrorText(it) }
        }
    }
}
}


@Composable
private fun SshKeysScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    var customKey by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteWarning by rememberSaveable { mutableStateOf(false) }
    val hasConfiguredSshKey = state.sshKeySettings.publicKey != null ||
        state.sshKeySettings.privateKeySecretAlias != null ||
        state.sshKeySettings.passphraseSecretAlias != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ssh-keys-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("SSH key management", state.sshKeySettings.keyType)
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Generated key", style = MaterialTheme.typography.titleMedium)
                    Text(state.sshKeySettings.generatedAt ?: "No generated key")
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            runCatching { SshKeyManager(secretStore).generateEd25519() }
                                .onSuccess { key ->
                                    repository.update { appState ->
                                        appState.copy(
                                            sshKeySettings = GlobalSshKeySettings(
                                                publicKey = key.publicKey,
                                                privateKeySecretAlias = key.privateKeyAlias,
                                                generatedAt = key.generatedAt,
                                            ),
                                        )
                                    }
                                    error = null
                                }
                                .onFailure { error = it.message }
                        },
                        modifier = Modifier.testTag("ssh-generate-key-button"),
                    ) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate")
                    }
                    if (hasConfiguredSshKey) {
                        OutlinedButton(
                            onClick = { showDeleteWarning = true },
                            modifier = Modifier.testTag("ssh-delete-key-button"),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete key")
                        }
                    }
                }
            }
            if (showDeleteWarning) {
                AlertDialog(
                    onDismissRequest = { showDeleteWarning = false },
                    icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
                    title = { Text("Delete SSH key?") },
                    text = {
                        Text("Backups cannot authenticate until you generate or store another SSH key. This removes the private key and passphrase from secure storage.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                runCatching {
                                    SshKeyManager(secretStore).deleteConfiguredKey(state.sshKeySettings)
                                    repository.update { appState ->
                                        appState.copy(sshKeySettings = GlobalSshKeySettings())
                                    }
                                }.onSuccess {
                                    error = null
                                    showDeleteWarning = false
                                }.onFailure {
                                    error = it.message
                                }
                            },
                            modifier = Modifier.testTag("ssh-confirm-delete-key-button"),
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteWarning = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
            if (state.sshKeySettings.publicKey != null) {
                CopyableBlock(
                    text = state.sshKeySettings.publicKey,
                    copyContentDescription = "Copy SSH public key",
                    copyButtonTag = "ssh-public-key-copy-button",
                )
            }
        }
        SectionCard {
            Text("Custom private key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(customKey, { customKey = it }, label = { Text("Private key") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val keyAlias = "custom-ssh-private-key"
                    val passphraseAlias = "custom-ssh-passphrase"
                    SshKeyManager(secretStore).storeCustomPrivateKey(keyAlias, customKey)
                    if (passphrase.isNotBlank()) secretStore.put(passphraseAlias, passphrase.toByteArray())
                    repository.update { appState ->
                        appState.copy(
                            sshKeySettings = appState.sshKeySettings.copy(
                                privateKeySecretAlias = keyAlias,
                                customPrivateKeyLabel = "Custom key",
                                passphraseSecretAlias = passphraseAlias.takeIf { passphrase.isNotBlank() },
                            ),
                        )
                    }
                    customKey = ""
                    passphrase = ""
                },
                enabled = customKey.isNotBlank(),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Store")
            }
        }
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun TailscaleScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nodeName by rememberSaveable(state.tailscale.nodeName) { mutableStateOf(state.tailscale.nodeName) }
    var authKey by rememberSaveable { mutableStateOf("") }
    val defaultTestServer = state.servers.firstOrNull { !it.tailscaleHost.isNullOrBlank() }
    var testHost by rememberSaveable(defaultTestServer?.tailscaleHost) {
        mutableStateOf(defaultTestServer?.tailscaleHost.orEmpty())
    }
    var testPort by rememberSaveable(defaultTestServer?.port) {
        mutableStateOf((defaultTestServer?.port ?: 22).toString())
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tailscale-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Tailscale setup/status/test/reset", if (state.tailscale.isConfigured) "Configured" else "Not configured")
        SectionCard {
            OutlinedTextField(
                nodeName,
                { nodeName = it },
                label = { Text("Node name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tailscale-node-name-field"),
            )
            OutlinedTextField(
                authKey,
                { authKey = it },
                label = { Text("Auth key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tailscale-auth-key-field"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !busy && nodeName.isNotBlank() && authKey.isNotBlank(),
                    modifier = Modifier.testTag("tailscale-authenticate-button"),
                    onClick = {
                        busy = true
                        message = "Authenticating"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                TailscaleManager(context, secretStore).authenticate(
                                    nodeName = nodeName.trim(),
                                    authKey = authKey.trim(),
                                )
                            }
                            authKey = ""
                            val now = Instant.now().toString()
                            repository.update { appState ->
                                appState.copy(
                                    tailscale = if (result.success) {
                                        TailscaleStateMetadata(
                                            isConfigured = true,
                                            nodeName = nodeName.trim(),
                                            stateSecretAlias = result.stateSecretAlias,
                                            lastLoginAt = now,
                                            lastReachabilityTestAt = appState.tailscale.lastReachabilityTestAt,
                                            lastError = null,
                                            keyExpiryAdviceAcknowledged = appState.tailscale.keyExpiryAdviceAcknowledged,
                                        )
                                    } else {
                                        appState.tailscale.copy(
                                            nodeName = nodeName.trim(),
                                            lastError = result.output.ifBlank { "Tailscale auth failed" },
                                        )
                                    },
                                )
                            }
                            message = result.output.ifBlank { if (result.success) "Authenticated" else "Authentication failed" }
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Authenticate")
                }
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.testTag("tailscale-reset-button"),
                    onClick = {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                TailscaleManager(context, secretStore).reset(state.tailscale.stateSecretAlias)
                            }
                            repository.update { appState ->
                                appState.copy(tailscale = TailscaleStateMetadata(nodeName = nodeName.trim()))
                            }
                            message = "Reset"
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }
        SectionCard {
            OutlinedTextField(
                testHost,
                { testHost = it },
                label = { Text("Test host") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tailscale-test-host-field"),
            )
            OutlinedTextField(
                testPort,
                { value -> testPort = value.filter { it.isDigit() } },
                label = { Text("Port") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tailscale-test-port-field"),
            )
            Button(
                enabled = !busy && state.tailscale.isConfigured && testHost.isNotBlank() && testPort.toIntOrNull() != null,
                modifier = Modifier.testTag("tailscale-test-button"),
                onClick = {
                    busy = true
                    message = "Testing"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            TailscaleManager(context, secretStore).testReachability(
                                nodeName = state.tailscale.nodeName,
                                stateSecretAlias = state.tailscale.stateSecretAlias,
                                host = testHost.trim(),
                                port = testPort.toInt(),
                            )
                        }
                        val now = Instant.now().toString()
                        repository.update { appState ->
                            appState.copy(
                                tailscale = appState.tailscale.copy(
                                    lastReachabilityTestAt = if (result.success) now else appState.tailscale.lastReachabilityTestAt,
                                    lastError = if (result.success) null else result.output.ifBlank { "Tailscale test failed" },
                                ),
                            )
                        }
                        message = result.output.ifBlank { if (result.success) "Connected" else "Test failed" }
                        busy = false
                    }
                },
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test")
            }
        }
        SectionCard {
            ToggleRow("Key expiry advice acknowledged", state.tailscale.keyExpiryAdviceAcknowledged) { checked ->
                repository.update { appState ->
                    appState.copy(tailscale = appState.tailscale.copy(keyExpiryAdviceAcknowledged = checked))
                }
            }
            Text("Last login: ${state.tailscale.lastLoginAt ?: "none"}")
            Text("Last test: ${state.tailscale.lastReachabilityTestAt ?: "none"}")
            message?.let { Text(it) }
            state.tailscale.lastError?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun PermissionSettingsSection(
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    onRefreshPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        onRefreshPermissions()
    }
    SectionCard {
        Text("Permission setup/status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(if (permissions.allRequiredGranted) "All required permissions approved" else "Approve every required item")
        PermissionRow("All files access", permissions.allFilesAccess) {
            context.startActivity(PermissionIntents.allFilesAccess(context))
        }
        PermissionRow("Battery optimization exemption", permissions.batteryOptimizationExempt) {
            context.startActivity(PermissionIntents.batteryOptimization(context))
        }
        PermissionRow("Exact alarm access", permissions.exactAlarmAccess) {
            context.startActivity(PermissionIntents.exactAlarm(context))
        }
        PermissionRow("Wi-Fi/SSID access", permissions.wifiStateAccess) {
            context.startActivity(PermissionIntents.appDetails(context))
        }
        PermissionRow("Notifications", permissions.notifications) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onRefreshPermissions()
            }
        }
        OutlinedButton(onClick = onRefreshPermissions) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

private fun formatBytesUi(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) "$bytes ${units[unitIndex]}" else "%.1f %s".format(Locale.US, value, units[unitIndex])
}

private fun compactMiddleUi(value: String, maxLength: Int = 48): String {
    if (value.length <= maxLength) return value
    val keepStart = (maxLength * 0.45f).toInt().coerceAtLeast(12)
    val keepEnd = (maxLength - keepStart - 3).coerceAtLeast(12)
    return value.take(keepStart).trimEnd('/') + "..." + value.takeLast(keepEnd).trimStart('/')
}

@Composable
private fun LogsScreen(state: AppState, repository: AppRepository) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SectionHeader("Logs", "Last ${state.settings.logRetentionLimit}")
                }
                OutlinedButton(
                    onClick = { repository.clearLogs() },
                    enabled = state.logs.isNotEmpty(),
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
                            StatusBadge(log.status.name.lowercase(), log.status.tone())
                        }
                        Text(
                            log.finishedAt ?: "Running since ${log.startedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
private fun CompactLogBlock(log: BackupLog) {
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
            ProgressMetric(log.finishedLabel(), log.finishedAt ?: "running", Modifier.weight(1.5f))
            ProgressMetric("Bytes", log.finalByteSummary(), Modifier.weight(1f))
        }
    }
}

private fun RunStatus.tone(): MetricTone =
    when (this) {
        RunStatus.SUCCESS -> MetricTone.Success
        RunStatus.WARNING -> MetricTone.Warning
        RunStatus.FAILED, RunStatus.CANCELLED -> MetricTone.Destructive
        RunStatus.RUNNING, RunStatus.QUEUED -> MetricTone.Route
        RunStatus.NEVER_RUN -> MetricTone.Neutral
    }

private fun BackupLog.finalByteSummary(): String {
    val sent = raw.lineSequence().firstOrNull { it.startsWith("sent ") } ?: return "-"
    return sent.substringBefore(" received").removePrefix("sent ").trim().ifBlank { "-" }
}

private fun BackupRunTrigger.label(): String =
    when (this) {
        BackupRunTrigger.MANUAL -> "Manual"
        BackupRunTrigger.AUTOMATIC -> "Automatic"
    }

private fun BackupEndReason.label(): String =
    when (this) {
        BackupEndReason.USER_CANCELLED -> "User cancel"
        BackupEndReason.FORCE_STOPPED -> "Force stop"
        BackupEndReason.NO_NETWORK -> "No network"
        BackupEndReason.CONSTRAINTS_NOT_MET -> "Constraints"
        BackupEndReason.CRASH -> "Crash"
        BackupEndReason.ERROR -> "Error"
    }

private fun BackupLog.finishedLabel(): String =
    when (status) {
        RunStatus.CANCELLED -> "Cancelled"
        RunStatus.FAILED -> "Finished"
        RunStatus.SUCCESS, RunStatus.WARNING -> "Finished"
        else -> "Finished"
    }

private fun unsuccessfulLogLastOutput(log: BackupLog): String? {
    if (log.status == RunStatus.SUCCESS || log.raw.isBlank()) return null
    val detail = log.endReasonDetail?.trim()
    val summary = log.summary.trim()
    return log.raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it != summary && it != detail }
        .lastOrNull()
}

@Composable
private fun SettingsScreen(
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    repository: AppRepository,
    onRefreshPermissions: () -> Unit,
    onSelectScreen: (Screen) -> Unit,
) {
    var settings by remember(state.settings) { mutableStateOf(state.settings) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val exportText = remember(state) { ExportCodec.encode(state.toExportDocument()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Settings and import/export", state.settings.phoneHostname)
        SectionCard {
            Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SettingsToolRow("SSH keys", "Generate, copy, import, or delete the app key", Icons.Outlined.Key) {
                onSelectScreen(Screen.SshKeys)
            }
            SettingsToolRow("Tailscale", "Authenticate, test reachability, and reset state", Icons.Outlined.Cloud) {
                onSelectScreen(Screen.Tailscale)
            }
        }
        PermissionSettingsSection(permissions, onRefreshPermissions)
        SectionCard {
            ThemePreferenceSelector(settings.themePreference) { preference ->
                val updated = settings.copy(themePreference = preference)
                settings = updated
                repository.update { it.copy(settings = updated) }
            }
            OutlinedTextField(settings.phoneHostname, { settings = settings.copy(phoneHostname = it) }, label = { Text("Phone hostname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.selectedSsid.orEmpty(), { settings = settings.copy(selectedSsid = it.ifBlank { null }) }, label = { Text("Selected SSID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.logRetentionLimit.toString(), { settings = settings.copy(logRetentionLimit = it.toIntOrNull() ?: settings.logRetentionLimit) }, label = { Text("Log retention") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { repository.update { it.copy(settings = settings) } }) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
        SectionCard {
            Text("Export", style = MaterialTheme.typography.titleMedium)
            CopyableBlock(
                text = exportText,
                copyContentDescription = "Copy export JSON",
                copyButtonTag = "settings-export-copy-button",
            )
        }
        SectionCard {
            Text("Import", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(importText, { importText = it }, label = { Text("Configuration JSON") }, minLines = 6, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                runCatching { ExportCodec.decode(importText) }
                    .onSuccess {
                        repository.importConfiguration(it)
                        importText = ""
                        importError = null
                    }
                    .onFailure { importError = it.message }
            }) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import")
            }
            importError?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun ThemePreferenceSelector(
    selected: ThemePreference,
    onChange: (ThemePreference) -> Unit,
) {
    Selector("Theme") {
        ThemePreference.entries.forEach { preference ->
            FilterChip(
                selected = selected == preference,
                onClick = { onChange(preference) },
                label = {
                    Text(
                        when (preference) {
                            ThemePreference.SYSTEM -> "System"
                            ThemePreference.LIGHT -> "Light"
                            ThemePreference.DARK -> "Dark"
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun SettingsToolRow(label: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        EntityIcon(icon, MetricTone.Route)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommandPreview(state: AppState, profile: BackupProfile) {
    val server = state.servers.firstOrNull { it.id == profile.serverId } ?: return
    val route = when (profile.targetMode) {
        TargetMode.TAILSCALE_FIRST_LAN_FALLBACK, TargetMode.TAILSCALE_ONLY -> Route.TAILSCALE
        else -> Route.LAN
    }
    val preview = runCatching {
        RsyncCommandBuilder.build(
            profile = profile,
            server = server,
            route = route,
            binaryPaths = BinaryPaths("rsync", "ssh", "tsnet-nc"),
            sshKeyPath = "files/ssh/id_ed25519",
            knownHostsPath = "files/ssh/known_hosts",
            excludesPath = "files/run/${profile.id}/excludes",
            tailscaleStateDir = "files/tailscale-state",
            tailscaleNodeName = state.tailscale.nodeName,
        ).preview
    }.getOrElse { it.message ?: "Invalid command" }
    SectionCard {
        Text("Command preview", style = MaterialTheme.typography.titleMedium)
        SelectableBlock(preview)
    }
}

@Composable
private fun EntityList(
    title: String,
    items: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    addButtonTag: String,
    modifier: Modifier = Modifier,
) {
    val rows = items
    val listState = rememberLazyListState()
    LaunchedEffect(rows.map { it.first }, selectedId) {
        val selectedIndex = rows.indexOfFirst { it.first == selectedId }
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag(addButtonTag),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add")
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows, key = { it.first }) { (id, label) ->
                    FilterChip(
                        selected = selectedId == id,
                        onClick = { onSelect(id) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetModeSelector(targetMode: TargetMode, onChange: (TargetMode) -> Unit) {
    Selector("Target mode") {
        TargetMode.entries.forEach { mode ->
            FilterChip(
                selected = targetMode == mode,
                onClick = { onChange(mode) },
                label = { Text(mode.name.lowercase().replace('_', ' ')) },
                modifier = Modifier.testTag("target-mode-${mode.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun ScheduleEditor(schedule: BackupSchedule, onChange: (BackupSchedule) -> Unit) {
    Selector("Schedule") {
        FilterChip(
            selected = schedule.type == ScheduleType.DISABLED,
            onClick = { onChange(schedule.copy(type = ScheduleType.DISABLED)) },
            label = { Text("disabled") },
        )
        FilterChip(
            selected = schedule.type != ScheduleType.DISABLED,
            onClick = { onChange(schedule.copy(type = ScheduleType.EXACT_DAILY)) },
            label = { Text("daily") },
        )
    }
    OutlinedTextField(
        value = schedule.timeLocal,
        onValueChange = { onChange(schedule.copy(timeLocal = it)) },
        label = { Text("Local time") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConstraintEditor(constraints: ConstraintSettings, onChange: (ConstraintSettings) -> Unit) {
    SectionCard {
        Text("Constraints", style = MaterialTheme.typography.titleMedium)
        ToggleRow("Wi-Fi only", constraints.wifiOnly) { onChange(constraints.copy(wifiOnly = it)) }
        ToggleRow("Unmetered only", constraints.unmeteredOnly) { onChange(constraints.copy(unmeteredOnly = it)) }
        ToggleRow("Charging only", constraints.chargingOnly) { onChange(constraints.copy(chargingOnly = it)) }
        ToggleRow(
            label = "Battery not low",
            checked = constraints.batteryNotLow,
            switchTag = "profile-constraint-battery-not-low-switch",
        ) {
            onChange(constraints.copy(batteryNotLow = it))
        }
        ToggleRow("Selected SSID only", constraints.selectedSsidOnly) { onChange(constraints.copy(selectedSsidOnly = it)) }
        ToggleRow("Manual override allowed", constraints.manualOverrideAllowed) { onChange(constraints.copy(manualOverrideAllowed = it)) }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    switchTag: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = switchTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}

@Composable
private fun WarningRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusIcon(if (granted) RunStatus.SUCCESS else RunStatus.FAILED)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onOpen) {
            Text(if (granted) "Open" else "Grant")
        }
    }
}

@Composable
private fun IssueList(issues: List<com.ttv20.rsyncbackup.model.ValidationIssue>) {
    if (issues.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        issues.forEach { issue ->
            AssistChip(
                onClick = {},
                label = { Text(issue.message) },
                leadingIcon = {
                    Icon(
                        imageVector = if (issue.severity == Severity.ERROR) Icons.Outlined.Error else Icons.Outlined.Warning,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun Selector(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProgressMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun phaseLabel(phase: RunProgressPhase): String =
    phase.name.lowercase().replace('_', ' ')

private fun RunProgressPhase.notificationLabel(): String =
    when (this) {
        RunProgressPhase.IDLE -> "Waiting"
        RunProgressPhase.PREPARING -> "Preparing backup"
        RunProgressPhase.RUNNING_RSYNC -> "Running rsync"
        RunProgressPhase.UPLOADING_STATUS -> "Uploading backup status"
        RunProgressPhase.CANCELLING -> "Cancelling backup"
        RunProgressPhase.FORCE_STOPPING -> "Force stopping backup"
        RunProgressPhase.COMPLETED -> "Backup completed"
        RunProgressPhase.FAILED -> "Backup failed"
        RunProgressPhase.CANCELLED -> "Backup cancelled"
    }

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = if (selected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = colors,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(if (selected) 12.dp else 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectableBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CopyableBlock(
    text: String,
    copyContentDescription: String,
    copyButtonTag: String,
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.testTag(copyButtonTag),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = copyContentDescription)
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
        }
        SelectableBlock(text)
    }
}

@Composable
private fun StatusIcon(status: RunStatus) {
    Icon(
        imageVector = when (status) {
            RunStatus.SUCCESS -> Icons.Outlined.CheckCircle
            RunStatus.WARNING -> Icons.Outlined.Warning
            RunStatus.FAILED, RunStatus.CANCELLED -> Icons.Outlined.Error
            else -> Icons.Outlined.Sync
        },
        tint = when (status) {
            RunStatus.SUCCESS -> MaterialTheme.colorScheme.primary
            RunStatus.WARNING -> MaterialTheme.colorScheme.tertiary
            RunStatus.FAILED, RunStatus.CANCELLED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        contentDescription = status.name,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
