@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ttv20.rsyncbackup.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
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
    SshKeys("SSH keys", Icons.Outlined.Key),
    Tailscale("Tailscale", Icons.Outlined.Cloud),
    Run("Run", Icons.Outlined.PlayArrow),
    Logs("Logs", Icons.Outlined.Article),
    Settings("Settings", Icons.Outlined.Settings),
}

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
    LaunchedEffect(requestedScreenName, requestedScreenRequestId) {
        validScreenName(requestedScreenName)?.let {
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

    RsyncBackupTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(Modifier.fillMaxHeight()) {
                        Screen.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == screen,
                                onClick = { selectedScreen = item.name },
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
                        onSelect = { selectedScreen = it.name },
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
                    onSelect = { selectedScreen = it.name },
                    onRefreshPermissions = refreshPermissions,
                )
            }
        }
    }
}

private fun validScreenName(name: String?): String? =
    name?.let { value ->
        if (value.equals("Permissions", ignoreCase = true)) return@let Screen.Settings.name
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
    onRefreshPermissions: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rsync Backup") },
                actions = {
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (compactNav) {
                CompactNavigation(selected = screen, onSelect = onSelect)
            }
            when (screen) {
                Screen.Dashboard -> DashboardScreen(state, onRun = { BackupService.start(it.context, it.profileId) })
                Screen.Profiles -> ProfilesScreen(state, repository)
                Screen.Servers -> ServersScreen(state, repository, secretStore)
                Screen.SshKeys -> SshKeysScreen(state, repository, secretStore)
                Screen.Tailscale -> TailscaleScreen(state, repository, secretStore)
                Screen.Run -> RunScreen(state)
                Screen.Logs -> LogsScreen(state, repository)
                Screen.Settings -> SettingsScreen(state, permissions, repository, onRefreshPermissions)
            }
        }
    }
}

private data class RunRequest(val context: Context, val profileId: String)

@Composable
private fun CompactNavigation(selected: Screen, onSelect: (Screen) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Screen.entries.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
                leadingIcon = { Icon(item.icon, contentDescription = null) },
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun DashboardScreen(state: AppState, onRun: (RunRequest) -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader("Dashboard", "Profiles, queue, and last results")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Profiles", state.profiles.size.toString(), Modifier.weight(1f))
                Metric("Servers", state.servers.size.toString(), Modifier.weight(1f))
                Metric("Logs", state.logs.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Running: ${state.queue.runningProfileId ?: "none"}")
                Text("Queued: ${state.queue.queuedProfileIds.joinToString().ifBlank { "none" }}")
            }
        }
        items(state.profiles) { profile ->
            val issues = ProfileValidator.validate(profile, state)
            val liveProgress = state.runProgress.takeIf { it.profileId == profile.id }
            val isRunningProfile = state.queue.runningProfileId == profile.id
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(profile.remotePath, style = MaterialTheme.typography.bodyMedium)
                        Text(profile.status.lastMessage ?: "No runs yet", style = MaterialTheme.typography.bodySmall)
                    }
                    ElevatedButton(
                        onClick = {
                            if (isRunningProfile) {
                                BackupService.cancel(context)
                            } else {
                                onRun(RunRequest(context, profile.id))
                            }
                        },
                        enabled = isRunningProfile || issues.none { it.severity == Severity.ERROR },
                    ) {
                        Icon(
                            if (isRunningProfile) Icons.Outlined.Error else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRunningProfile) "Stop" else "Run")
                    }
                }
                liveProgress?.let {
                    DashboardProfileProgress(it)
                }
                IssueList(issues)
            }
        }
    }
}

@Composable
private fun DashboardProfileProgress(progress: RunProgressState) {
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Text(progress.message ?: phaseLabel(progress.phase), fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressMetric("Phase", phaseLabel(progress.phase))
        ProgressMetric("Files", progress.filesDiscovered?.toString() ?: "-")
        ProgressMetric("Transferred", progress.filesTransferred?.toString() ?: "-")
        ProgressMetric("Bytes", progress.bytesTransferredRaw?.let { formatBytesUi(it) } ?: progress.bytesTransferred ?: "-")
        ProgressMetric("Speed", progress.speed ?: "-")
        ProgressMetric(
            "Avg speed",
            progress.averageBytesPerSecond?.let { "${formatBytesUi(it)}/s" }
                ?: progress.recentAverageBytesPerSecond?.let { "${formatBytesUi(it)}/s" }
                ?: "-",
        )
        ProgressMetric("Duration", progress.duration ?: "-")
    }
    lastNonFileOutputLine(progress)?.let { line ->
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun lastNonFileOutputLine(progress: RunProgressState): String? =
    progress.recentOutput
        .asReversed()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotBlank() &&
                line != progress.currentFile?.trim() &&
                !looksLikeRsyncFileOutput(line)
        }

private fun looksLikeRsyncFileOutput(line: String): Boolean =
    !line.contains(":") && !line.startsWith("sent ") && !line.startsWith("total size ")

@Composable
private fun ProfilesScreen(state: AppState, repository: AppRepository) {
    val context = LocalContext.current
    val scheduler = remember(context) { BackupScheduler(context) }
    var compactEditorOpen by rememberSaveable { mutableStateOf(false) }
    var draftProfile by remember { mutableStateOf<BackupProfile?>(null) }
    var selectedProfileId by rememberSaveable {
        mutableStateOf(state.profiles.firstOrNull()?.id)
    }
    LaunchedEffect(state.profiles.map { it.id }) {
        val profileIds = state.profiles.map { it.id }
        val currentSelection = selectedProfileId
        if (currentSelection == null || currentSelection !in profileIds) {
            selectedProfileId = state.profiles.firstOrNull()?.id
        }
    }
    val selected = state.profiles.firstOrNull { it.id == selectedProfileId } ?: state.profiles.firstOrNull()
    val addProfile: () -> Unit = {
        state.servers.firstOrNull()?.let { server ->
            draftProfile = BackupProfile(
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
            selectedProfileId = draftProfile?.id
            compactEditorOpen = true
        }
    }
    val addServerFromProfile: () -> ServerRecord = {
        val server = defaultServer("New server", state.servers.size + 1)
        repository.upsertServer(server)
        server
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stacked = maxWidth < 720.dp
        val editingProfile = draftProfile ?: selected
        val list: @Composable (Modifier) -> Unit = { modifier ->
            EntityList(
                title = "Profiles",
                items = state.profiles.map { it.id to it.name },
                selectedId = selected?.id,
                onSelect = {
                    draftProfile = null
                    selectedProfileId = it
                    compactEditorOpen = true
                },
                onAdd = addProfile,
                addButtonTag = "profiles-add-button",
                modifier = modifier,
            )
        }
        val editor: @Composable (Modifier) -> Unit = { modifier ->
            if (editingProfile != null) {
                val isDraft = draftProfile?.id == editingProfile.id
                ProfileEditor(
                    state = state,
                    profile = editingProfile,
                    onSave = {
                        repository.upsertProfile(it)
                        scheduler.schedule(it)
                        selectedProfileId = it.id
                        draftProfile = null
                        if (stacked) compactEditorOpen = false
                    },
                    onDelete = {
                        if (!isDraft) {
                            scheduler.cancel(editingProfile.id)
                            repository.removeProfile(editingProfile.id)
                            selectedProfileId = state.profiles.firstOrNull { it.id != editingProfile.id }?.id
                        }
                        draftProfile = null
                        if (stacked) compactEditorOpen = false
                    },
                    onAddServer = addServerFromProfile,
                    onBack = if (stacked) {
                        {
                            draftProfile = null
                            compactEditorOpen = false
                        }
                    } else {
                        null
                    },
                    deleteLabel = if (isDraft) "Cancel" else "Delete",
                    modifier = modifier,
                )
            }
        }
        if (stacked) {
            if (compactEditorOpen && editingProfile != null) {
                editor(Modifier.fillMaxSize())
            } else {
                list(Modifier.fillMaxSize())
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                list(
                    Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                )
                editor(Modifier.weight(1f))
            }
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
    deleteLabel: String = "Delete",
    modifier: Modifier = Modifier,
) {
    var editing by remember(profile.id, profile) { mutableStateOf(profile) }
    var sourcePickerError by remember(profile.id) { mutableStateOf<String?>(null) }
    var pendingSaveWarnings by remember(profile.id) {
        mutableStateOf<List<com.ttv20.rsyncbackup.model.ValidationIssue>>(emptyList())
    }
    val issues = ProfileValidator.validate(editing, state)
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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag("profile-editor-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader("Profile editor", editing.name)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let {
                OutlinedButton(onClick = it) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back")
                }
            }
            Button(
                onClick = saveProfile,
                enabled = issues.none { it.severity == Severity.ERROR },
                modifier = Modifier.testTag("profile-save-button"),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(deleteLabel)
            }
        }
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
        OutlinedTextField(editing.name, { editing = editing.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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
                Icon(Icons.Outlined.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pick")
            }
        }
        sourcePickerError?.let { ErrorText(it) }
        OutlinedTextField(
            editing.remotePath,
            { editing = editing.copy(remotePath = it) },
            label = { Text("Remote path") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile-remote-path-field"),
        )
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
        TargetModeSelector(editing.targetMode) { editing = editing.copy(targetMode = it) }
        ScheduleEditor(editing.schedule) { editing = editing.copy(schedule = it) }
        ConstraintEditor(editing.constraints) { editing = editing.copy(constraints = it) }
        ToggleRow("Delete remote files not present locally", editing.deleteEnabled) {
            editing = editing.copy(deleteEnabled = it)
        }
        OutlinedTextField(
            value = editing.excludes,
            onValueChange = { editing = editing.copy(excludes = it) },
            label = { Text("Excludes") },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = editing.advancedArgs,
            onValueChange = { editing = editing.copy(advancedArgs = it) },
            label = { Text("Advanced rsync args") },
            modifier = Modifier.fillMaxWidth(),
        )
        CommandPreview(state, editing)
    }
}

@Composable
private fun ServersScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    var compactEditorOpen by rememberSaveable { mutableStateOf(false) }
    var draftServer by remember { mutableStateOf<ServerRecord?>(null) }
    var selectedServerId by rememberSaveable {
        mutableStateOf(state.servers.firstOrNull()?.id)
    }
    LaunchedEffect(state.servers.map { it.id }) {
        val serverIds = state.servers.map { it.id }
        val currentSelection = selectedServerId
        if (currentSelection == null || currentSelection !in serverIds) {
            selectedServerId = state.servers.firstOrNull()?.id
        }
    }
    val selected = state.servers.firstOrNull { it.id == selectedServerId } ?: state.servers.firstOrNull()
    val addServer = {
        draftServer = defaultServer("New server", state.servers.size + 1)
        selectedServerId = draftServer?.id
        compactEditorOpen = true
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stacked = maxWidth < 720.dp
        val editingServer = draftServer ?: selected
        val list: @Composable (Modifier) -> Unit = { modifier ->
            EntityList(
                title = "Servers",
                items = state.servers.map { it.id to it.name },
                selectedId = selected?.id,
                onSelect = {
                    draftServer = null
                    selectedServerId = it
                    compactEditorOpen = true
                },
                onAdd = addServer,
                addButtonTag = "servers-add-button",
                modifier = modifier,
            )
        }
        val editor: @Composable (Modifier) -> Unit = { modifier ->
            if (editingServer != null) {
                val isDraft = draftServer?.id == editingServer.id
                ServerEditor(
                    state = state,
                    server = editingServer,
                    repository = repository,
                    secretStore = secretStore,
                    onSave = {
                        repository.upsertServer(it)
                        selectedServerId = it.id
                        draftServer = null
                        if (stacked) compactEditorOpen = false
                    },
                    onBack = if (stacked) {
                        {
                            draftServer = null
                            compactEditorOpen = false
                        }
                    } else {
                        null
                    },
                    cancelLabel = if (isDraft) "Cancel" else "Back",
                    modifier = modifier,
                )
            }
        }
        if (stacked) {
            if (compactEditorOpen && editingServer != null) {
                editor(Modifier.fillMaxSize())
            } else {
                list(Modifier.fillMaxSize())
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                list(
                    Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                )
                editor(Modifier.weight(1f))
            }
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
    val scrollState = rememberScrollState()
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
    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag("server-editor-scroll")
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader("Server setup/test", editing.name)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let {
                OutlinedButton(onClick = it) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(cancelLabel)
                }
            }
            Button(
                onClick = { onSave(editing) },
                modifier = Modifier.testTag("server-save-button"),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
        OutlinedTextField(editing.name, { editing = editing.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            editing.user,
            { editing = editing.copy(user = it) },
            label = { Text("User") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("server-user-field"),
        )
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
        SectionCard {
            Text("Trusted fingerprint", style = MaterialTheme.typography.titleMedium)
            Text("LAN and Tailscale addresses share fingerprint group ${editing.fingerprintGroupId}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
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
                OutlinedButton(
                    enabled = scanTarget == null && !editing.tailscaleHost.isNullOrBlank(),
                    onClick = {
                        val host = editing.tailscaleHost ?: return@OutlinedButton
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

@Composable
private fun SshKeysScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    var customKey by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
                Button(
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

@Composable
private fun RunScreen(state: AppState) {
    val context = LocalContext.current
    var selectedProfileId by rememberSaveable(state.profiles.map { it.id }.joinToString()) {
        mutableStateOf(state.profiles.firstOrNull()?.id)
    }
    val profile = state.profiles.firstOrNull { it.id == selectedProfileId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("run-screen-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Backup run screen", "Manual run, command preview, and queue state")
        RunProgressCard(state.runProgress)
        Selector("Profile") {
            state.profiles.forEach { item ->
                FilterChip(selected = item.id == selectedProfileId, onClick = { selectedProfileId = item.id }, label = { Text(item.name) })
            }
        }
        if (profile != null) {
            val issues = ProfileValidator.validate(profile, state)
            val runningProfileId = state.queue.runningProfileId
            val isRunning = runningProfileId != null
            val isSelectedProfileRunning = runningProfileId == profile.id
            IssueList(issues)
            CommandPreview(state, profile)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { BackupService.start(context, profile.id) },
                    enabled = issues.none { it.severity == Severity.ERROR },
                    modifier = Modifier.testTag("run-start-backup-button"),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start backup")
                }
                if (isRunning) {
                    OutlinedButton(
                        onClick = { BackupService.cancel(context) },
                        enabled = isSelectedProfileRunning,
                    ) {
                        Icon(Icons.Outlined.Error, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
                if (state.runProgress.phase == RunProgressPhase.CANCELLING || state.runProgress.phase == RunProgressPhase.FORCE_STOPPING) {
                    OutlinedButton(
                        onClick = { BackupService.forceStop(context) },
                        enabled = isSelectedProfileRunning,
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Force stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun RunProgressCard(progress: RunProgressState) {
    SectionCard {
        Text("Live progress", style = MaterialTheme.typography.titleMedium)
        Text(progress.message ?: phaseLabel(progress.phase), fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(progress.profileName, progress.updatedAt?.let { "updated $it" }).joinToString(" - ").ifBlank { "No active run" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        progress.currentFile?.let {
            Text("Current file", style = MaterialTheme.typography.labelLarge)
            SelectableBlock(it)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressMetric("Phase", phaseLabel(progress.phase))
            ProgressMetric("Files", progress.filesDiscovered?.toString() ?: "-")
            ProgressMetric("Transferred", progress.filesTransferred?.toString() ?: "-")
            ProgressMetric("Bytes", progress.bytesTransferredRaw?.let { formatBytesUi(it) } ?: progress.bytesTransferred ?: "-")
            ProgressMetric("Speed", progress.speed ?: "-")
            ProgressMetric(
                "Avg speed",
                progress.averageBytesPerSecond?.let { "${formatBytesUi(it)}/s" }
                    ?: progress.recentAverageBytesPerSecond?.let { "${formatBytesUi(it)}/s" }
                    ?: "-",
            )
            ProgressMetric("Duration", progress.duration ?: "-")
        }
        if (progress.finalStats.isNotEmpty()) {
            Text("Final stats", style = MaterialTheme.typography.labelLarge)
            SelectableBlock(progress.finalStats.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        }
        if (progress.recentOutput.isNotEmpty()) {
            Text("Recent output", style = MaterialTheme.typography.labelLarge)
            SelectableBlock(progress.recentOutput.joinToString("\n"))
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

@Composable
private fun LogsScreen(state: AppState, repository: AppRepository) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(log.status)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(log.profileName, fontWeight = FontWeight.SemiBold)
                        Text(log.summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${log.startedAt} - ${log.finishedAt ?: "running"}", style = MaterialTheme.typography.bodySmall)
                    }
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
    }
}

private fun unsuccessfulLogLastOutput(log: BackupLog): String? {
    if (log.status == RunStatus.SUCCESS || log.raw.isBlank()) return null
    val summary = log.summary.trim()
    return log.raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it != summary }
        .lastOrNull()
}

@Composable
private fun SettingsScreen(
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    repository: AppRepository,
    onRefreshPermissions: () -> Unit,
) {
    var settings by remember(state.settings) { mutableStateOf(state.settings) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val exportText = remember(state) { ExportCodec.encode(state.toExportDocument()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Settings and import/export", state.settings.phoneHostname)
        PermissionSettingsSection(permissions, onRefreshPermissions)
        SectionCard {
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
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
    OutlinedCard(modifier = modifier, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProgressMetric(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun phaseLabel(phase: RunProgressPhase): String =
    phase.name.lowercase().replace('_', ' ')

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SelectableBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
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
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.testTag(copyButtonTag),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
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
            else -> MaterialTheme.colorScheme.secondary
        },
        contentDescription = status.name,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
