@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInBrowser
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ttv20.rsyncbackup.MainActivity
import com.ttv20.rsyncbackup.R
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.backup.BinaryPaths
import com.ttv20.rsyncbackup.backup.AndroidConstraintSnapshotReader
import com.ttv20.rsyncbackup.backup.AndroidWifiNetworkReader
import com.ttv20.rsyncbackup.backup.BackupConstraintEvaluator
import com.ttv20.rsyncbackup.backup.ConstraintSnapshot
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
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.TailscaleStateMetadata
import com.ttv20.rsyncbackup.model.ThemePreference
import com.ttv20.rsyncbackup.model.requiresLan
import com.ttv20.rsyncbackup.model.requiresTailscale
import com.ttv20.rsyncbackup.model.effectiveTailscaleNodeName
import com.ttv20.rsyncbackup.model.resolvedSshKeySettings
import com.ttv20.rsyncbackup.model.routeOrder
import com.ttv20.rsyncbackup.model.suggestedTailscaleNodeName
import com.ttv20.rsyncbackup.model.toExportDocument
import com.ttv20.rsyncbackup.model.withUpdatedSettings
import com.ttv20.rsyncbackup.permissions.PermissionIntents
import com.ttv20.rsyncbackup.permissions.PermissionStateReader
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.ssh.SshKeyManager
import com.ttv20.rsyncbackup.ssh.SshRemotePathBrowser
import com.ttv20.rsyncbackup.ssh.SshRemotePathBrowserSession
import com.ttv20.rsyncbackup.ssh.SshRemotePathListing
import com.ttv20.rsyncbackup.ssh.TargetConnectResult
import com.ttv20.rsyncbackup.ssh.TargetConnectionSetup
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import com.ttv20.rsyncbackup.tailscale.TailscalePeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Outlined.Dashboard),
    Profiles("Profiles", Icons.Outlined.Folder),
    Targets("Targets", Icons.Outlined.Storage),
    Logs("Logs", Icons.Outlined.Article),
    SshKeys("SSH Access", Icons.Outlined.Key),
    Tailscale("Tailscale", Icons.Outlined.Cloud),
    Settings("Settings", Icons.Outlined.Settings),
}

private val MainScreens = listOf(Screen.Dashboard, Screen.Profiles, Screen.Targets, Screen.Logs)
private const val MIN_PORT = 1
private const val MAX_PORT = 65535

private enum class OnboardingStep(val title: String) {
    Welcome("Welcome"),
    Permissions("Permissions"),
    Tailscale("Tailscale Connection"),
    NewTarget("New Target"),
    NewProfile("New Profile"),
    Review("Review And Dry Run"),
}

private val OnboardingSteps = OnboardingStep.entries.toList()

private enum class PendingOnboardingNavigation {
    Back,
    Skip,
}

private data class RemotePathBrowseRequest(
    val title: String,
    val startPath: String,
    val target: TargetRecord,
    val routes: List<Route>,
)

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
    val shouldOpenOnboarding = requestedScreen == null &&
        state.settings.onboardingCompletedAt == null &&
        state.settings.onboardingSkippedAt == null
    var onboardingActive by rememberSaveable { mutableStateOf(shouldOpenOnboarding) }
    var onboardingInitialStep by rememberSaveable {
        mutableStateOf(
            state.settings.onboardingLastStep
                ?: OnboardingStep.Welcome.name,
        )
    }
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
    LaunchedEffect(shouldOpenOnboarding, requestedScreenName, requestedScreenRequestId) {
        if (requestedScreen == null && shouldOpenOnboarding) {
            onboardingInitialStep = state.settings.onboardingLastStep ?: OnboardingStep.Welcome.name
            onboardingActive = true
        } else if (requestedScreen != null) {
            onboardingActive = false
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

    val exitOnboardingToDashboard: (Boolean) -> Unit = { completed ->
        val now = Instant.now().toString()
        repository.update { appState ->
            appState.copy(
                settings = appState.settings.copy(
                    onboardingCompletedAt = if (completed) now else appState.settings.onboardingCompletedAt,
                    onboardingSkippedAt = if (completed) appState.settings.onboardingSkippedAt else now,
                    onboardingLastStep = null,
                ),
            )
        }
        selectedScreen = Screen.Dashboard.name
        permissionOnboardingActive = false
        onboardingActive = false
    }

    val appLayoutDirection = when (stringResource(R.string.app_layout_direction).trim().lowercase(Locale.US)) {
        "rtl" -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
    RsyncBackupTheme(themePreference = state.settings.themePreference) {
        CompositionLocalProvider(LocalLayoutDirection provides appLayoutDirection) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxSize(),
            ) {
                val onboardingContent: (@Composable () -> Unit)? = if (onboardingActive) {
                    {
                        OnboardingFlow(
                            state = state,
                            permissions = permissions,
                            repository = repository,
                            secretStore = secretStore,
                            initialStepName = onboardingInitialStep,
                            onRefreshPermissions = refreshPermissions,
                            onExitToDashboard = exitOnboardingToDashboard,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    null
                }
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
                                onStartOnboarding = { initialStep ->
                                    onboardingInitialStep = initialStep.name
                                    onboardingActive = true
                                },
                                onboardingContent = onboardingContent,
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
                            onStartOnboarding = { initialStep ->
                                onboardingInitialStep = initialStep.name
                                onboardingActive = true
                            },
                            onboardingContent = onboardingContent,
                        )
                    }
                }
            }
        }
    }
}

private fun validScreenName(name: String?): String? =
    name?.let { value ->
        if (value.equals("Permissions", ignoreCase = true)) return@let Screen.Settings.name
        if (value.equals("Run", ignoreCase = true)) return@let Screen.Dashboard.name
        if (value.equals("Servers", ignoreCase = true)) return@let Screen.Targets.name
        if (value.equals("SSH keys", ignoreCase = true)) return@let Screen.SshKeys.name
        Screen.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true)
        }?.name
    }

private fun sanitizePortText(value: String): String = value.filter { it.isDigit() }

private fun portFromText(value: String): Int? =
    value.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }

private fun normalizedHostUi(value: String): String =
    value.trim().trimEnd('.').lowercase(Locale.US)

@Composable
private fun PortTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = portFromText(value) == null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizePortText(it)) },
        label = { Text("Port") },
        isError = isError,
        supportingText = if (isError) {
            { Text("Enter a port from $MIN_PORT to $MAX_PORT") }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun TailscaleHostPicker(
    state: AppState,
    secretStore: SecretStore,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier.fillMaxWidth(),
    loadButtonTag: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val canLoadPeers = state.tailscale.isConfigured && state.tailscale.stateSecretAlias != null
    val listNodeName = effectiveTailscaleNodeName(state)
    var peers by remember(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf<List<TailscalePeer>>(emptyList()) }
    var loading by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf(false) }
    var loadAttempted by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf(false) }
    var loadError by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf<String?>(null) }
    var dropdownExpanded by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf(false) }
    val filteredPeers = remember(peers, value) {
        val query = value.trim()
        if (query.isBlank()) {
            peers
        } else {
            peers.filter { it.matchesHostQuery(query) }
        }
    }
    val hasExactPeerMatch = peers.any { normalizedHostUi(it.host) == normalizedHostUi(value) }
    val showCustomChoice = value.isNotBlank() && !hasExactPeerMatch

    fun loadPeers() {
        if (!canLoadPeers || loading) return
        loading = true
        loadAttempted = true
        loadError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TailscaleManager(context, secretStore).listPeers(
                    nodeName = listNodeName,
                    stateSecretAlias = state.tailscale.stateSecretAlias,
                )
            }
            if (result.success) {
                peers = result.peers
                loadError = null
            } else {
                loadError = result.output.ifBlank { "Could not load Tailscale devices" }
            }
            loading = false
        }
    }

    LaunchedEffect(canLoadPeers, state.tailscale.stateSecretAlias, listNodeName) {
        if (canLoadPeers) {
            if (dropdownExpanded) loadPeers()
        } else {
            peers = emptyList()
            loading = false
            loadAttempted = false
            loadError = null
            dropdownExpanded = false
        }
    }

    LaunchedEffect(dropdownExpanded, filteredPeers.size, loading, loadError) {
        if (dropdownExpanded) {
            delay(120)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                dropdownExpanded = true
            },
            label = { Text(label) },
            trailingIcon = {
                IconButton(
                    enabled = canLoadPeers,
                    modifier = loadButtonTag?.let { Modifier.testTag(it) } ?: Modifier,
                    onClick = {
                        dropdownExpanded = !dropdownExpanded
                        if (canLoadPeers && !loadAttempted) loadPeers()
                    },
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = "Show Tailscale devices")
                }
            },
            modifier = fieldModifier.onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    dropdownExpanded = true
                    if (canLoadPeers && !loadAttempted) loadPeers()
                }
            },
            singleLine = true,
        )
        if (dropdownExpanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "load") {
                        TailscaleHostMenuRow(
                            leadingIcon = Icons.Outlined.Sync,
                            title = when {
                                loading -> "Loading peers"
                                peers.isEmpty() -> "Load peers"
                                else -> "Refresh peers"
                            },
                            subtitle = if (canLoadPeers) "Search Tailscale devices or enter a custom host" else "Enter a host manually",
                            enabled = canLoadPeers && !loading,
                            onClick = { loadPeers() },
                        )
                    }
                    items(filteredPeers, key = { it.host }) { peer ->
                        TailscaleHostMenuRow(
                            leadingIcon = Icons.Outlined.Cloud,
                            title = peer.primaryLabel(),
                            subtitle = peer.secondaryLabel(),
                            selected = normalizedHostUi(value) == normalizedHostUi(peer.host),
                            onClick = {
                                onValueChange(peer.host)
                                dropdownExpanded = false
                            },
                        )
                    }
                    if (showCustomChoice) {
                        item(key = "custom") {
                            TailscaleHostMenuRow(
                                leadingIcon = Icons.Outlined.Edit,
                                title = "Use custom host",
                                subtitle = value.trim(),
                                onClick = { dropdownExpanded = false },
                            )
                        }
                    }
                    if (!loading && loadAttempted && peers.isNotEmpty() && filteredPeers.isEmpty() && !showCustomChoice) {
                        item(key = "empty-filter") {
                            TailscaleHostMenuRow(
                                title = "No matching Tailscale devices",
                                subtitle = "Keep typing to use a custom host",
                                enabled = false,
                            )
                        }
                    }
                    if (!loading && loadAttempted && peers.isEmpty()) {
                        item(key = "empty-peers") {
                            TailscaleHostMenuRow(
                                title = "No Tailscale devices found",
                                subtitle = "Enter a host manually or refresh peers",
                                enabled = false,
                            )
                        }
                    }
                }
            }
        }
        when {
            !canLoadPeers -> Text(
                "Tailscale is not connected; enter a host manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loadError != null -> Text(
                friendlyTailscaleError(loadError ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = toneColor(MetricTone.Destructive),
            )
            loadAttempted && !loading && peers.isEmpty() -> Text(
                "No Tailscale devices found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TailscaleHostMenuRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun TailscalePeer.primaryLabel(): String =
    hostName?.takeIf { it.isNotBlank() } ?: dnsName?.takeIf { it.isNotBlank() } ?: host

private fun TailscalePeer.secondaryLabel(): String {
    val status = if (online) "online" else "offline"
    val hostPart = host.takeIf { it != primaryLabel() }
    val ipPart = tailscaleIps.firstOrNull()
    return listOfNotNull(hostPart, ipPart, os?.takeIf { it.isNotBlank() }, status).joinToString(" - ")
}

private fun TailscalePeer.matchesHostQuery(query: String): Boolean {
    val normalizedQuery = normalizedHostUi(query)
    return listOf(host, hostName, dnsName, os)
        .filterNotNull()
        .any { normalizedHostUi(it).contains(normalizedQuery) } ||
        tailscaleIps.any { it.contains(query.trim(), ignoreCase = true) }
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

@Composable
private fun OnboardingFlow(
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    repository: AppRepository,
    secretStore: SecretStore,
    initialStepName: String,
    onRefreshPermissions: () -> Unit,
    onExitToDashboard: (completed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheduler = remember(context) { BackupScheduler(context) }
    val initialStep = remember(initialStepName, state.settings.onboardingLastStep) {
        initialStepName
            .takeIf { it.isNotBlank() }
            ?.let { saved -> OnboardingStep.entries.firstOrNull { it.name == saved } }
            ?: state.settings.onboardingLastStep
            ?.let { saved -> OnboardingStep.entries.firstOrNull { it.name == saved } }
            ?: OnboardingStep.Welcome
    }
    var currentStep by rememberSaveable(initialStepName) { mutableStateOf(initialStep.name) }
    var pendingNavigation by remember { mutableStateOf<PendingOnboardingNavigation?>(null) }
    val initialTarget = remember(state.targets) {
        state.targets.firstOrNull() ?: defaultTarget("New target", state.targets.size + 1)
    }
    var targetDraft by remember(initialTarget.id) { mutableStateOf(initialTarget) }
    var savedTargetId by rememberSaveable(initialTarget.id) {
        mutableStateOf<String?>(initialTarget.id.takeIf { state.targets.any { target -> target.id == initialTarget.id } })
    }
    var profileDraft by remember {
        mutableStateOf(defaultOnboardingProfile(state, initialTarget, repository.defaultExcludes))
    }
    var savedProfileId by rememberSaveable(profileDraft.id) {
        mutableStateOf<String?>(profileDraft.id.takeIf { state.profiles.any { profile -> profile.id == profileDraft.id } })
    }
    var dryRunResult by remember { mutableStateOf<DryRunResult?>(null) }
    var childBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val step = OnboardingStep.valueOf(currentStep)
    val stepIndex = OnboardingSteps.indexOf(step).coerceAtLeast(0)

    LaunchedEffect(currentStep) {
        childBackHandler = null
        repository.update { appState ->
            appState.copy(settings = appState.settings.copy(onboardingLastStep = currentStep))
        }
    }
    LaunchedEffect(savedTargetId) {
        val selectedTarget = state.targets.firstOrNull { it.id == savedTargetId } ?: targetDraft
        profileDraft = profileDraft.copy(
            targetId = selectedTarget.id,
            remotePath = profileDraft.remotePath,
            targetMode = defaultTargetModeFor(selectedTarget, profileDraft.targetMode),
        )
    }

    fun saveTargetDraft() {
        repository.upsertTarget(targetDraft)
        savedTargetId = targetDraft.id
    }

    fun saveProfileDraft() {
        val reviewed = profileDraft.copy(remoteSafetyReviewedAt = Instant.now().toString())
        repository.upsertProfile(reviewed)
        scheduler.schedule(reviewed)
        profileDraft = reviewed
        savedProfileId = reviewed.id
    }

    fun hasUnsavedDraft(): Boolean =
        when (step) {
            OnboardingStep.NewTarget -> state.targets.firstOrNull { it.id == targetDraft.id } != targetDraft
            OnboardingStep.NewProfile -> state.profiles.firstOrNull { it.id == profileDraft.id } != profileDraft
            else -> false
        }

    fun goTo(targetStep: OnboardingStep) {
        currentStep = targetStep.name
    }

    fun runPendingNavigation(action: PendingOnboardingNavigation) {
        when (action) {
            PendingOnboardingNavigation.Back -> {
                val previous = OnboardingSteps.getOrNull(stepIndex - 1)
                if (previous != null) goTo(previous)
            }
            PendingOnboardingNavigation.Skip -> onExitToDashboard(false)
        }
    }

    fun requestNavigation(action: PendingOnboardingNavigation) {
        if (hasUnsavedDraft()) {
            pendingNavigation = action
        } else {
            runPendingNavigation(action)
        }
    }

    pendingNavigation?.let { action ->
        UnsavedChangesDialog(
            entityName = if (step == OnboardingStep.NewTarget) "target" else "profile",
            saveEnabled = true,
            onSave = {
                if (step == OnboardingStep.NewTarget) saveTargetDraft() else saveProfileDraft()
                pendingNavigation = null
                runPendingNavigation(action)
            },
            onDiscard = {
                pendingNavigation = null
                runPendingNavigation(action)
            },
            onDismiss = { pendingNavigation = null },
        )
    }

    Column(modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                IconButton(
                    onClick = { childBackHandler?.invoke() ?: requestNavigation(PendingOnboardingNavigation.Back) },
                    enabled = stepIndex > 0,
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Step ${stepIndex + 1} of ${OnboardingSteps.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { requestNavigation(PendingOnboardingNavigation.Skip) },
                    modifier = Modifier.testTag("onboarding-skip-button"),
                ) {
                    Text("Skip setup")
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("onboarding-${step.name.lowercase()}"),
        ) {
            when (step) {
                OnboardingStep.Welcome -> WelcomeStep(
                    onStart = { goTo(OnboardingStep.Permissions) },
                    onSkip = { onExitToDashboard(false) },
                )
                OnboardingStep.Permissions -> OnboardingPermissionsStep(
                    permissions = permissions,
                    onRefreshPermissions = onRefreshPermissions,
                    onContinue = { goTo(OnboardingStep.Tailscale) },
                )
                OnboardingStep.Tailscale -> OnboardingWrappedScreen(
                    onContinue = { goTo(OnboardingStep.NewTarget) },
                ) {
                    TailscaleScreen(state, repository, secretStore)
                }
                OnboardingStep.NewTarget -> OnboardingTargetStep(
                    state = state,
                    target = targetDraft,
                    repository = repository,
                    secretStore = secretStore,
                    onBack = { goTo(OnboardingStep.Tailscale) },
                    onBackHandlerChange = { childBackHandler = it },
                    onSave = { savedTarget ->
                        targetDraft = savedTarget
                        savedTargetId = savedTarget.id
                        goTo(OnboardingStep.NewProfile)
                    },
                )
                OnboardingStep.NewProfile -> OnboardingProfileStep(
                    state = state,
                    profile = profileDraft,
                    repository = repository,
                    secretStore = secretStore,
                    onBack = { goTo(OnboardingStep.NewTarget) },
                    onBackHandlerChange = { childBackHandler = it },
                    onSave = { savedProfile ->
                        profileDraft = savedProfile
                        savedProfileId = savedProfile.id
                        goTo(OnboardingStep.Review)
                    },
                )
                OnboardingStep.Review -> OnboardingReviewStep(
                    state = state,
                    permissions = permissions,
                    profileId = savedProfileId ?: profileDraft.id,
                    dryRunResult = dryRunResult,
                    onDryRun = {
                        dryRunResult = startDryRun(
                            savedProfileId ?: profileDraft.id,
                            state,
                            permissions,
                            AndroidConstraintSnapshotReader(context).read(),
                        )
                    },
                    onFinish = { onExitToDashboard(true) },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Welcome")
        SectionCard {
            Text("Set up the permissions, optional Tailscale connection, server target, and backup profile.")
            Text(
                "The app creates its SSH key automatically. You only need to connect to the server and choose where backups go.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, modifier = Modifier.testTag("onboarding-start-button")) {
                    Text("Start setup")
                }
                OutlinedButton(onClick = onSkip) {
                    Text("Skip")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionsStep(
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    onRefreshPermissions: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Grant the permissions needed for scheduled file backups. Network-name access is optional.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionSettingsSection(permissions, onRefreshPermissions)
        Button(onClick = onContinue, modifier = Modifier.testTag("onboarding-permissions-continue-button")) {
            Text("Continue")
        }
    }
}

@Composable
private fun OnboardingWrappedScreen(
    onContinue: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            content()
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(12.dp),
            ) {
                Button(onClick = onContinue, modifier = Modifier.testTag("onboarding-continue-button")) {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun OnboardingTargetStep(
    state: AppState,
    target: TargetRecord,
    repository: AppRepository,
    secretStore: SecretStore,
    onBack: () -> Unit,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    onSave: (TargetRecord) -> Unit,
) {
    TargetEditor(
        state = state,
        target = target,
        repository = repository,
        secretStore = secretStore,
        onSave = onSave,
        onBack = onBack,
        onBackHandlerChange = onBackHandlerChange,
        isDraft = state.targets.none { it.id == target.id },
        cancelLabel = "Back",
        showEditorHeader = false,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OnboardingProfileStep(
    state: AppState,
    profile: BackupProfile,
    repository: AppRepository,
    secretStore: SecretStore,
    onBack: () -> Unit,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    onSave: (BackupProfile) -> Unit,
) {
    val context = LocalContext.current
    val scheduler = remember(context) { BackupScheduler(context) }
    ProfileEditor(
        state = state,
        profile = profile,
        onSave = { savedProfile ->
            repository.upsertProfile(savedProfile)
            scheduler.schedule(savedProfile)
            onSave(savedProfile)
        },
        onDelete = null,
        onAddTarget = {
            val target = defaultTarget("New target", state.targets.size + 1)
            repository.upsertTarget(target)
            target
        },
        secretStore = secretStore,
        onBack = onBack,
        onBackHandlerChange = onBackHandlerChange,
        isDraft = state.profiles.none { it.id == profile.id },
        showEditorHeader = false,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OnboardingReviewStep(
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    profileId: String,
    dryRunResult: DryRunResult?,
    onDryRun: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val constraintSnapshot = remember(context, profileId) {
        AndroidConstraintSnapshotReader(context).read()
    }
    val profile = state.profiles.firstOrNull { it.id == profileId }
    val checklist = profile?.let {
        setupChecklistForProfile(it, state, permissions, constraintSnapshot)
    }.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader("Review And Dry Run")
        SectionCard {
            Text("Readiness checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (checklist.isEmpty()) {
                ChecklistRow("Profile saved", false)
            } else {
                checklist.forEach { item ->
                    ChecklistRow(item.label, item.complete, item.detail ?: item.nextAction)
                }
            }
        }
        dryRunResult?.let { result ->
            SectionCard {
                Text("Dry run result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(result.message)
                if (!result.passed) {
                    result.checklist.filterNot { it.complete }.forEach { item ->
                        ChecklistRow(item.label, false, item.nextAction)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDryRun, enabled = profile != null, modifier = Modifier.testTag("onboarding-dry-run-button")) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Dry run")
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.testTag("onboarding-finish-button")) {
                Text("Finish")
            }
        }
    }
}

@Composable
private fun SetupRepairCard(checklist: List<SetupChecklistItem>, onOpenStep: () -> Unit) {
    val missing = checklist.filterNot { it.complete }
    val next = missing.firstOrNull()
    SectionCard {
        Text("Setup checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        missing.take(3).forEach { item ->
            ChecklistRow(item.label, false, item.nextAction)
        }
        next?.let {
            FilledTonalButton(onClick = onOpenStep) {
                Text(it.nextAction)
            }
        }
    }
}

private data class SetupChecklistItem(
    val label: String,
    val complete: Boolean,
    val nextAction: String,
    val step: OnboardingStep,
    val detail: String? = null,
)

private data class DryRunResult(
    val passed: Boolean,
    val message: String,
    val checklist: List<SetupChecklistItem>,
)

private fun defaultOnboardingProfile(state: AppState, target: TargetRecord, defaultExcludes: String): BackupProfile {
    val selectedTarget = state.targets.firstOrNull { it.id == target.id } ?: state.targets.firstOrNull() ?: target
    val targetMode = defaultTargetModeFor(selectedTarget)
    state.profiles.firstOrNull()?.let { existing ->
        return existing.copy(
            targetId = selectedTarget.id,
            remotePath = "",
            targetMode = targetMode,
        )
    }
    return BackupProfile(
        id = UUID.randomUUID().toString(),
        name = "Phone backup",
        sourcePath = "/storage/emulated/0",
        targetId = selectedTarget.id,
        remotePath = "",
        targetMode = targetMode,
        excludes = defaultExcludes.trimEnd(),
    )
}

private fun startDryRun(
    profileId: String,
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    constraintSnapshot: ConstraintSnapshot,
): DryRunResult {
    val profile = state.profiles.firstOrNull { it.id == profileId }
        ?: return DryRunResult(false, "Save a profile before dry run", emptyList())
    val checklist = setupChecklistForProfile(profile, state, permissions, constraintSnapshot)
    val passed = checklist.all { it.complete } &&
        ProfileValidator.validate(profile, state).none { it.severity == Severity.ERROR }
    return if (passed) {
        DryRunResult(true, "Dry run engine not implemented yet", checklist)
    } else {
        DryRunResult(false, "Missing setup items", checklist)
    }
}

private fun setupChecklistForProfile(
    profile: BackupProfile,
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    constraintSnapshot: ConstraintSnapshot,
): List<SetupChecklistItem> {
    val target = state.targets.firstOrNull { it.id == profile.targetId }
    val trusted = target != null && state.trustedHostFingerprints.any {
        it.targetId == target.id || it.targetId == target.fingerprintGroupId
    }
    val constraintFailures = BackupConstraintEvaluator.failures(
        profile = profile,
        snapshot = constraintSnapshot,
    )
    return listOf(
        SetupChecklistItem("Permissions approved", permissions.allRequiredGranted, "Grant permissions", OnboardingStep.Permissions),
        SetupChecklistItem("Target fingerprint trusted", trusted, "Trust fingerprint", OnboardingStep.NewTarget),
        SetupChecklistItem(
            "Target connected",
            target?.publicKeyInstalledAt != null || target?.keyOnlyLoginVerifiedAt != null,
            "Connect target",
            OnboardingStep.NewTarget,
        ),
        SetupChecklistItem(
            "Tailscale configured if needed",
            !profile.targetMode.requiresTailscale() || state.tailscale.isConfigured,
            "Sign in to Tailscale",
            OnboardingStep.Tailscale,
        ),
        SetupChecklistItem("Remote target safety reviewed", profile.remoteSafetyReviewedAt != null, "Review profile", OnboardingStep.NewProfile),
        SetupChecklistItem(
            "Constraints currently satisfied",
            constraintFailures.isEmpty(),
            "Review profile",
            OnboardingStep.NewProfile,
            constraintFailures.firstOrNull()?.message,
        ),
    )
}

private fun firstMissingSetupStep(checklist: List<SetupChecklistItem>): OnboardingStep =
    checklist.firstOrNull { !it.complete }?.step ?: OnboardingStep.Review

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
    onStartOnboarding: (OnboardingStep) -> Unit,
    onboardingContent: (@Composable () -> Unit)? = null,
) {
    var detailScreenActive by rememberSaveable { mutableStateOf(false) }
    var detailBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(screen) {
        detailScreenActive = false
        detailBackHandler = null
    }
    val activeBack = if (onboardingContent == null) detailBackHandler ?: onBack else null
    BackHandler(enabled = activeBack != null) {
        activeBack?.invoke()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
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
                    Text(stringResource(R.string.app_name))
                },
                actions = {
                    if (onboardingContent == null && screen != Screen.Settings) {
                        IconButton(onClick = { onSelect(Screen.Settings) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (onboardingContent == null && compactNav && screen in MainScreens && !detailScreenActive) {
                PhoneBottomNavigation(selected = screen, onSelect = onSelect)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (onboardingContent != null) {
                onboardingContent()
            } else {
                when (screen) {
                    Screen.Dashboard -> DashboardScreen(
                        state,
                        permissions,
                        onRun = { BackupService.start(it.context, it.profileId) },
                        onStartOnboarding = onStartOnboarding,
                    )
                    Screen.Profiles -> ProfilesScreen(
                        state,
                        repository,
                        secretStore,
                        onOpenDashboard = { onSelect(Screen.Dashboard) },
                        onDetailActiveChange = { active, back ->
                            detailScreenActive = active
                            detailBackHandler = back
                        },
                    )
                    Screen.Targets -> TargetsScreen(
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
                    Screen.Settings -> SettingsScreen(
                        state,
                        permissions,
                        repository,
                        onRefreshPermissions,
                        onSelect,
                        onStartOnboarding,
                    )
                }
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

private fun defaultTargetModeFor(target: TargetRecord, preferred: TargetMode? = null): TargetMode {
    if (preferred != null && preferred.unavailableReason(target) == null) return preferred
    return when {
        target.lanHost.isNotBlank() && !target.tailscaleHost.isNullOrBlank() -> TargetMode.LAN_FIRST_TAILSCALE_FALLBACK
        target.lanHost.isNotBlank() -> TargetMode.LAN_ONLY
        !target.tailscaleHost.isNullOrBlank() -> TargetMode.TAILSCALE_ONLY
        else -> preferred ?: TargetMode.LAN_ONLY
    }
}

private fun TargetMode.unavailableReason(target: TargetRecord): String? {
    val needsLan = requiresLan() && target.lanHost.isBlank()
    val needsTailscale = requiresTailscale() && target.tailscaleHost.isNullOrBlank()
    return when {
        needsLan && needsTailscale -> "This mode needs a server address and Tailscale device."
        needsLan -> "This mode needs a server address."
        needsTailscale -> "This mode needs a Tailscale device."
        else -> null
    }
}

private fun unavailableTargetModeMessage(target: TargetRecord): String? {
    val missing = listOfNotNull(
        "Server-address modes are disabled because this target has no server address.".takeIf { target.lanHost.isBlank() },
        "Tailscale modes are disabled because this target has no Tailscale device.".takeIf { target.tailscaleHost.isNullOrBlank() },
    )
    return missing.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

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
private fun DashboardScreen(
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader("Dashboard")
        }
        items(state.profiles) { profile ->
            val issues = ProfileValidator.validate(profile, state)
            val checklist = setupChecklistForProfile(profile, state, permissions, constraintSnapshot)
            val liveProgress = state.runProgress.takeIf { it.profileId == profile.id }
            val isRunningProfile = state.queue.runningProfileId == profile.id
            val target = state.targets.firstOrNull { it.id == profile.targetId }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileListRow(
                    profile = profile,
                    target = target,
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
                if (checklist.any { !it.complete }) {
                    SetupRepairCard(
                        checklist = checklist,
                        onOpenStep = { onStartOnboarding(firstMissingSetupStep(checklist)) },
                    )
                }
            }
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

@Composable
private fun ProfileListRow(
    profile: BackupProfile,
    target: TargetRecord?,
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
                ProfileRouteLine(profile, target)
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
private fun TargetListRow(
    target: TargetRecord,
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
                Text(target.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(targetConnectionSummary(target), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RouteSummaryLine("Server address", target.lanHost.ifBlank { "Not set" }, MetricTone.Route)
                target.tailscaleHost?.let { RouteSummaryLine("Tailscale device", it, MetricTone.Route) }
                RouteSummaryLine("Fingerprint", if (trusted) "Trusted" else "Needs fingerprint", if (trusted) MetricTone.Success else MetricTone.Warning)
            }
            Spacer(Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
    }
}

private fun targetConnectionSummary(target: TargetRecord): String {
    val host = target.lanHost.ifBlank { target.tailscaleHost.orEmpty() }.ifBlank { "No address" }
    val user = target.user.ifBlank { "user" }
    return "$user@$host:${target.port}"
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
private fun ProfileRouteLine(profile: BackupProfile, target: TargetRecord?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(
            listOfNotNull(target?.name ?: "Missing target", routeModeLabel(profile.targetMode)).joinToString(" - "),
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
            label = live?.let { phaseLabel(it.phase) } ?: profile.status.lastStatus.displayLabel(),
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
    onBack: () -> Unit,
    backLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saveButtonTag: String,
    saveLabel: String = "Save",
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
        TargetMode.LAN_ONLY -> "Server address only"
        TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> "Server address first"
        TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> "Tailscale device first"
        TargetMode.TAILSCALE_ONLY -> "Tailscale device only"
    }

private fun scheduleLabel(schedule: BackupSchedule): String =
    when (schedule.type) {
        ScheduleType.DISABLED -> "Disabled"
        ScheduleType.EXACT_DAILY -> "Daily, ${schedule.timeLocal}"
        ScheduleType.BEST_EFFORT_DAILY -> "Best effort, ${schedule.timeLocal}"
    }

private fun knownWifiSsidOptions(
    deviceSsids: List<String>,
    currentSelection: String?,
): List<String> =
    (deviceSsids + listOfNotNull(currentSelection))
        .mapNotNull { it.cleanSsidLabel() }
        .distinctBy { it.lowercase(Locale.US) }

private fun String.cleanSsidLabel(): String? =
    trim()
        .trim('"')
        .takeIf { it.isNotBlank() }

@Composable
private fun ProfilesScreen(
    state: AppState,
    repository: AppRepository,
    secretStore: SecretStore,
    onOpenDashboard: () -> Unit,
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
    val addTargetFromProfile: () -> TargetRecord = {
        val target = defaultTarget("New target", state.targets.size + 1)
        repository.upsertTarget(target)
        target
    }
    val addProfile: () -> Unit = {
        val target = state.targets.firstOrNull() ?: addTargetFromProfile()
        onDetailActiveChange(true, closeEditor)
        editorIsDraft = true
        editorProfile = BackupProfile(
            id = UUID.randomUUID().toString(),
            name = "New profile",
            targetId = target.id,
            remotePath = "",
            targetMode = defaultTargetModeFor(target),
            excludes = state.profiles.firstOrNull()?.excludes ?: repository.defaultExcludes.trimEnd(),
        )
        compactEditorOpen = true
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
            onAddTarget = addTargetFromProfile,
            secretStore = secretStore,
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
                    SectionHeader("Profiles")
                }
                items(state.profiles, key = { it.id }) { profile ->
                    val target = state.targets.firstOrNull { it.id == profile.targetId }
                    val issues = ProfileValidator.validate(profile, state)
                    val isRunningProfile = state.queue.runningProfileId == profile.id
                    ProfileListRow(
                        profile = profile,
                        target = target,
                        issues = issues,
                        trailing = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        if (isRunningProfile) {
                                            BackupService.cancel(context)
                                        } else {
                                            BackupService.start(context, profile.id)
                                            onOpenDashboard()
                                        }
                                    },
                                    enabled = isRunningProfile || issues.none { it.severity == Severity.ERROR },
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
    onDelete: (() -> Unit)?,
    onAddTarget: () -> TargetRecord,
    secretStore: SecretStore,
    onBack: (() -> Unit)? = null,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    isDraft: Boolean,
    deleteLabel: String = "Delete",
    showEditorHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editing by remember(profile.id, profile) { mutableStateOf(profile) }
    var sourcePickerError by remember(profile.id) { mutableStateOf<String?>(null) }
    var pendingSaveWarnings by remember(profile.id) {
        mutableStateOf<List<com.ttv20.rsyncbackup.model.ValidationIssue>>(emptyList())
    }
    var showUnsavedPrompt by rememberSaveable(profile.id) { mutableStateOf(false) }
    var browserRequest by remember(profile.id) { mutableStateOf<RemotePathBrowseRequest?>(null) }
    val selectedTarget = state.targets.firstOrNull { it.id == editing.targetId }
    val issues = ProfileValidator.validate(editing, state)
    val knownWifiSsids = remember(context, editing.constraints.selectedSsid) {
        knownWifiSsidOptions(
            deviceSsids = AndroidWifiNetworkReader(context).knownSsids(),
            currentSelection = editing.constraints.selectedSsid,
        )
    }
    val canSave = issues.none { it.severity == Severity.ERROR }
    val hasUnsavedChanges = isDraft || editing != profile
    val saveProfile = {
        val sanitized = editing.copy(
            remoteSafety = RemoteSafetySettings(),
            remoteSafetyReviewedAt = Instant.now().toString(),
        )
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
        if (browserRequest != null) {
            browserRequest = null
        } else if (hasUnsavedChanges) {
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

    browserRequest?.let { request ->
        RemotePathBrowserScreen(
            title = request.title,
            state = state,
            target = request.target,
            routes = request.routes,
            startPath = request.startPath,
            secretStore = secretStore,
            onPathSelected = { selectedPath ->
                editing = editing.copy(remotePath = selectedPath)
                browserRequest = null
            },
            onBack = { browserRequest = null },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        if (showEditorHeader) {
            EditorHeader(
                title = if (isDraft) "New Profile" else "Profile Edit",
                onBack = { requestBackState.value.invoke() },
                backLabel = "Back",
                onSave = saveProfile,
                saveEnabled = canSave,
                saveButtonTag = "profile-save-button",
                onSecondaryAction = onDelete,
                secondaryActionLabel = deleteLabel.takeIf { onDelete != null },
                secondaryActionIcon = Icons.Outlined.Delete.takeIf { onDelete != null },
            )
        }
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
                            val sanitized = editing.copy(
                                remoteSafety = RemoteSafetySettings(),
                                remoteSafetyReviewedAt = Instant.now().toString(),
                            )
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
                Text(
                    "Choose the folder on this phone that should be backed up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Text(
                    "Choose the server target and the remote folder where this backup will be stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Selector {
                    state.targets.forEach { target ->
                        FilterChip(
                            selected = editing.targetId == target.id,
                            onClick = {
                                editing = editing.copy(
                                    targetId = target.id,
                                    remotePath = "",
                                    targetMode = defaultTargetModeFor(target, editing.targetMode),
                                )
                            },
                            label = { Text(target.name) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val target = onAddTarget()
                            editing = editing.copy(
                                targetId = target.id,
                                remotePath = "",
                                targetMode = defaultTargetModeFor(target, editing.targetMode),
                            )
                        },
                        label = { Text("Add target") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.testTag("profile-add-target-button"),
                    )
                }
                RemotePathPickerField(
                    value = editing.remotePath,
                    onValueChange = { editing = editing.copy(remotePath = it) },
                    label = { Text("Remote path") },
                    target = selectedTarget,
                    routes = editing.targetMode.routeOrder(),
                    fieldTag = "profile-remote-path-field",
                    browseButtonTag = "profile-remote-path-browse-button",
                    onBrowse = {
                        val target = selectedTarget ?: return@RemotePathPickerField
                        browserRequest = RemotePathBrowseRequest(
                            title = "Remote path",
                            startPath = editing.remotePath.ifBlank { "~" },
                            target = target,
                            routes = editing.targetMode.routeOrder(),
                        )
                    },
                )
            }
            SectionCard {
                Text("Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Leave disabled for manual backups, or set a daily time for automatic runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScheduleEditor(editing.schedule) { editing = editing.copy(schedule = it) }
            }
            SectionCard {
                Text("Constraints", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                PrimaryConstraintEditor(editing.constraints) {
                    editing = editing.copy(constraints = it)
                }
            }
            AdvancedSection {
                TargetModeSelector(
                    targetMode = editing.targetMode,
                    target = selectedTarget,
                ) {
                    editing = editing.copy(targetMode = it)
                }
                AdvancedConstraintEditor(
                    constraints = editing.constraints,
                    knownWifiSsids = knownWifiSsids,
                ) {
                    editing = editing.copy(constraints = it)
                }
                WarningRow("Delete remote files not present locally", "Deletes target files that are not present in the source.", editing.deleteEnabled) {
                    editing = editing.copy(deleteEnabled = it)
                }
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
                CommandPreview(state, editing)
            }
            if (!showEditorHeader) {
                Button(
                    onClick = saveProfile,
                    enabled = canSave,
                    modifier = Modifier.testTag("profile-save-button"),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save profile")
                }
            }
        }
    }
}

@Composable
private fun TargetsScreen(
    state: AppState,
    repository: AppRepository,
    secretStore: SecretStore,
    onDetailActiveChange: (Boolean, (() -> Unit)?) -> Unit,
) {
    var compactEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<TargetRecord?>(null) }
    var editorIsDraft by rememberSaveable { mutableStateOf(false) }
    var editorBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeEditor = {
        editorTarget = null
        editorIsDraft = false
        editorBackHandler = null
        onDetailActiveChange(false, null)
        compactEditorOpen = false
    }
    val addTarget = {
        onDetailActiveChange(true, closeEditor)
        editorIsDraft = true
        editorTarget = defaultTarget("New target", state.targets.size + 1)
        compactEditorOpen = true
    }
    SideEffect {
        onDetailActiveChange(compactEditorOpen, if (compactEditorOpen) editorBackHandler ?: closeEditor else null)
    }
    DisposableEffect(Unit) {
        onDispose { onDetailActiveChange(false, null) }
    }
    val editingTarget = editorTarget
    if (compactEditorOpen && editingTarget != null) {
        val isDraft = editorIsDraft
        TargetEditor(
            state = state,
            target = editingTarget,
            repository = repository,
            secretStore = secretStore,
            onSave = {
                repository.upsertTarget(it)
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
                    SectionHeader("Targets")
                }
                items(state.targets, key = { it.id }) { target ->
                    val trusted = state.trustedHostFingerprints.any {
                        it.targetId == target.id || it.targetId == target.fingerprintGroupId
                    }
                    TargetListRow(
                        target = target,
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
                                        editorTarget = target
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
                if (state.targets.isEmpty()) {
                    item {
                        EmptyActionRow("No targets yet", "Add target", Icons.Outlined.Add, addTarget)
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = addTarget,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add target") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(16.dp)
                    .testTag("targets-add-button"),
            )
        }
    }
}

private fun defaultTarget(baseName: String, sequence: Int): TargetRecord =
    TargetRecord(
        id = UUID.randomUUID().toString(),
        name = if (sequence <= 1) baseName else "$baseName $sequence",
        user = "",
        lanHost = "",
        defaultRemotePath = "",
    )

@Composable
private fun TargetEditor(
    state: AppState,
    target: TargetRecord,
    repository: AppRepository,
    secretStore: SecretStore,
    onSave: (TargetRecord) -> Unit,
    onBack: (() -> Unit)? = null,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    isDraft: Boolean,
    cancelLabel: String = "Back",
    showEditorHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember(target.id, target) { mutableStateOf(target) }
    var showUnsavedPrompt by rememberSaveable(target.id) { mutableStateOf(false) }
    var portText by rememberSaveable(target.id) { mutableStateOf(target.port.toString()) }
    val portIsValid = portFromText(portText) != null
    var connectBusy by rememberSaveable(target.id) { mutableStateOf(false) }
    var connectMessage by rememberSaveable(target.id) { mutableStateOf<String?>(null) }
    var connectError by rememberSaveable(target.id) { mutableStateOf<String?>(null) }
    var pendingPasswordSetup by remember(target.id) { mutableStateOf<TargetConnectResult.NeedsPassword?>(null) }
    var setupPassword by rememberSaveable(target.id) { mutableStateOf("") }
    var setupError by rememberSaveable(target.id) { mutableStateOf<String?>(null) }
    var customKey by rememberSaveable(target.id) { mutableStateOf("") }
    var customPassphrase by rememberSaveable(target.id) { mutableStateOf("") }
    var customKeyMessage by rememberSaveable(target.id) { mutableStateOf<String?>(null) }
    var customKeyError by rememberSaveable(target.id) { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val hasUnsavedChanges = isDraft || editing != target || portText != editing.port.toString()
    val selectedSshKeySettings = editing.resolvedSshKeySettings(state.sshKeySettings)
    val hasAddress = editing.lanHost.isNotBlank() || !editing.tailscaleHost.isNullOrBlank()
    val canConnect = !connectBusy &&
        portIsValid &&
        editing.user.isNotBlank() &&
        hasAddress &&
        selectedSshKeySettings.publicKey != null &&
        selectedSshKeySettings.privateKeySecretAlias != null
    val requestBackState = rememberUpdatedState<() -> Unit> {
        if (hasUnsavedChanges) {
            showUnsavedPrompt = true
        } else {
            onBack?.invoke()
            Unit
        }
    }
    LaunchedEffect(connectMessage, connectError, pendingPasswordSetup) {
        if (connectMessage != null || connectError != null || pendingPasswordSetup != null) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    DisposableEffect(Unit) {
        val handler = { requestBackState.value.invoke() }
        onBackHandlerChange(handler)
        onDispose { onBackHandlerChange(null) }
    }

    fun normalizedTargetForConnect(): TargetRecord {
        val port = portFromText(portText) ?: editing.port
        val primaryAddress = editing.lanHost.trim().ifBlank { editing.tailscaleHost?.trim().orEmpty() }
        return editing.copy(
            name = editing.name.trim().ifBlank { primaryAddress.ifBlank { "Backup target" } },
            user = editing.user.trim(),
            lanHost = editing.lanHost.trim(),
            tailscaleHost = editing.tailscaleHost?.trim()?.ifBlank { null },
            port = port,
        )
    }

    fun saveConnectedTarget(
        connectedTarget: TargetRecord,
        trustedHostFingerprints: List<com.ttv20.rsyncbackup.model.TrustedHostFingerprint>,
    ) {
        repository.update { appState ->
            appState.copy(
                targets = appState.targets.filterNot { it.id == connectedTarget.id } + connectedTarget,
                trustedHostFingerprints = trustedHostFingerprints,
            )
        }
        onSave(connectedTarget)
    }

    fun connectAndSave() {
        val targetToConnect = normalizedTargetForConnect()
        val keySettings = targetToConnect.resolvedSshKeySettings(state.sshKeySettings)
        editing = targetToConnect
        connectBusy = true
        connectMessage = "Connecting to the server..."
        connectError = null
        setupError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TargetConnectionSetup(context, secretStore).connect(
                    state = state,
                    target = targetToConnect,
                    sshKeySettings = keySettings,
                )
            }
            when (result) {
                is TargetConnectResult.Authorized -> {
                    connectMessage = result.message
                    saveConnectedTarget(result.target, result.trustedHostFingerprints)
                }
                is TargetConnectResult.NeedsPassword -> {
                    pendingPasswordSetup = result
                    connectMessage = null
                }
                is TargetConnectResult.Failed -> {
                    connectError = result.message
                    connectMessage = null
                }
            }
            connectBusy = false
        }
    }

    if (showUnsavedPrompt) {
        UnsavedChangesDialog(
            entityName = "target",
            saveEnabled = portIsValid,
            onSave = {
                showUnsavedPrompt = false
                connectAndSave()
            },
            onDiscard = {
                showUnsavedPrompt = false
                onBack?.invoke()
            },
            onDismiss = { showUnsavedPrompt = false },
        )
    }

    pendingPasswordSetup?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                if (!connectBusy) {
                    pendingPasswordSetup = null
                    setupPassword = ""
                    setupError = null
                }
            },
            title = { Text("Connect") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setupPassword,
                        onValueChange = { setupPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target-setup-password-field"),
                    )
                    Text(
                        "Server fingerprint:\n${pending.fingerprintText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    setupError?.let { ErrorText(it) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !connectBusy && setupPassword.isNotBlank(),
                    modifier = Modifier.testTag("target-install-over-lan-button"),
                    onClick = {
                        val publicKey = selectedSshKeySettings.publicKey
                        if (publicKey == null) {
                            setupError = "No SSH public key is configured."
                            return@Button
                        }
                        val password = setupPassword
                        connectBusy = true
                        setupError = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    TargetConnectionSetup(context, secretStore).installPublicKey(
                                        state = state,
                                        target = pending.target,
                                        route = pending.route,
                                        trustedHostFingerprints = pending.trustedHostFingerprints,
                                        publicKey = publicKey,
                                        password = password,
                                    )
                                }
                            }
                            result.onSuccess { installResult ->
                                if (installResult.isSuccess) {
                                    val updatedTarget = pending.target.copy(
                                        publicKeyInstalledAt = Instant.now().toString(),
                                        keyOnlyLoginVerifiedAt = Instant.now().toString(),
                                    )
                                    setupPassword = ""
                                    pendingPasswordSetup = null
                                    saveConnectedTarget(updatedTarget, pending.trustedHostFingerprints)
                                } else {
                                    setupError = installResult.output.ifBlank { "Password setup failed with exit ${installResult.exitStatus}" }
                                }
                            }.onFailure {
                                setupError = it.message ?: "Password setup failed"
                            }
                            connectBusy = false
                        }
                    },
                ) {
                    Text(if (connectBusy) "Connecting" else "Connect")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !connectBusy,
                    onClick = {
                        pendingPasswordSetup = null
                        setupPassword = ""
                        setupError = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        if (showEditorHeader) {
            EditorHeader(
                title = if (isDraft) "New Target" else "Target Edit",
                onBack = { requestBackState.value.invoke() },
                backLabel = cancelLabel,
                onSave = { connectAndSave() },
                saveEnabled = canConnect,
                saveButtonTag = "target-save-button",
                saveLabel = if (connectBusy) "Connecting" else "Connect",
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("target-editor-scroll")
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                Text("Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Use Server address for a public IP or DNS name, Tailscale device for a private Tailscale-only server, or both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    editing.user,
                    { editing = editing.copy(user = it) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target-user-field"),
                )
                OutlinedTextField(
                    editing.lanHost,
                    { editing = editing.copy(lanHost = it) },
                    label = { Text("Server address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target-lan-host-field"),
                )
                TailscaleHostPicker(
                    state = state,
                    secretStore = secretStore,
                    value = editing.tailscaleHost.orEmpty(),
                    onValueChange = { editing = editing.copy(tailscaleHost = it.ifBlank { null }) },
                    label = "Tailscale device",
                    modifier = Modifier.fillMaxWidth(),
                    fieldModifier = Modifier
                        .fillMaxWidth()
                        .testTag("target-tailscale-host-field"),
                )
                Text(
                    "Connect verifies the server and installs the app SSH key if password setup is needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedSshKeySettings.privateKeySecretAlias == null || selectedSshKeySettings.publicKey == null) {
                    FeedbackBanner("SSH key unavailable", "Open Settings and configure an SSH key before connecting.", MetricTone.Warning)
                }
                connectMessage?.let {
                    FeedbackBanner("Target setup", it, MetricTone.Route)
                }
                connectError?.let {
                    FeedbackBanner("Connection failed", it, MetricTone.Destructive)
                }
            }
            AdvancedSection {
                OutlinedTextField(editing.name, { editing = editing.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PortTextField(
                    value = portText,
                    onValueChange = { value ->
                        portText = value
                        portFromText(value)?.let { editing = editing.copy(port = it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target-port-field"),
                )
                Selector("SSH key") {
                    FilterChip(
                        selected = editing.sshKeySettings == null,
                        onClick = { editing = editing.copy(sshKeySettings = null) },
                        label = { Text("Use global key") },
                    )
                    FilterChip(
                        selected = editing.sshKeySettings != null,
                        onClick = { editing = editing.copy(sshKeySettings = editing.sshKeySettings ?: GlobalSshKeySettings(customPrivateKeyLabel = "Target key")) },
                        label = { Text("Use target key") },
                    )
                }
                if (editing.sshKeySettings != null) {
                    StatusBadge(
                        if (editing.sshKeySettings?.privateKeySecretAlias != null) "Target key configured" else "No target key imported",
                        if (editing.sshKeySettings?.privateKeySecretAlias != null) MetricTone.Success else MetricTone.Warning,
                    )
                    OutlinedTextField(
                        customKey,
                        { customKey = it },
                        label = { Text("Private key") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        customPassphrase,
                        { customPassphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = customKey.isNotBlank(),
                        onClick = {
                            customKeyMessage = null
                            customKeyError = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val nativeInstall = NativeBinaryManager(context).ensureInstalled()
                                        val sshKeygen = nativeInstall.requireTool("ssh-keygen")
                                        val publicKey = SshKeyManager(secretStore).extractPublicKeyFromPrivateKey(
                                            sshKeygenPath = sshKeygen,
                                            filesDir = context.filesDir,
                                            workDir = context.cacheDir,
                                            privateKey = customKey,
                                            passphrase = customPassphrase,
                                        )
                                        val keyAlias = "target-${editing.id}-ssh-private-key"
                                        val passphraseAlias = "target-${editing.id}-ssh-passphrase"
                                        SshKeyManager(secretStore).storeCustomPrivateKey(keyAlias, customKey)
                                        if (customPassphrase.isNotBlank()) {
                                            secretStore.put(passphraseAlias, customPassphrase.toByteArray())
                                        }
                                        GlobalSshKeySettings(
                                            publicKey = publicKey,
                                            privateKeySecretAlias = keyAlias,
                                            customPrivateKeyLabel = "Target key",
                                            passphraseSecretAlias = passphraseAlias.takeIf { customPassphrase.isNotBlank() },
                                            generatedAt = Instant.now().toString(),
                                        )
                                    }
                                }.onSuccess { keySettings ->
                                    editing = editing.copy(sshKeySettings = keySettings)
                                    customKey = ""
                                    customPassphrase = ""
                                    customKeyMessage = "Target SSH key imported"
                                }.onFailure {
                                    customKeyError = it.message ?: "Target SSH key import failed"
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import target key")
                    }
                    customKeyMessage?.let { FeedbackBanner("SSH key updated", it, MetricTone.Success) }
                    customKeyError?.let { FeedbackBanner("SSH key import failed", it, MetricTone.Destructive) }
                }
            }
            if (!showEditorHeader) {
                Button(
                    onClick = { connectAndSave() },
                    enabled = canConnect,
                    modifier = Modifier.testTag("target-save-button"),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (connectBusy) "Connecting" else "Connect")
                }
            }
        }
    }
}


@Composable
private fun SshKeysScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customKey by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var successMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteWarning by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val publicKey = state.sshKeySettings.publicKey
    val hasConfiguredSshKey = publicKey != null ||
        state.sshKeySettings.privateKeySecretAlias != null ||
        state.sshKeySettings.passphraseSecretAlias != null
    val hasStoredPrivateKey = state.sshKeySettings.privateKeySecretAlias != null
    val isCustomKey = state.sshKeySettings.customPrivateKeyLabel != null
    val keyStatus = when {
        !hasConfiguredSshKey -> "No SSH key yet"
        isCustomKey -> "Custom key stored"
        else -> "SSH key ready"
    }
    val passphraseStatus = if (state.sshKeySettings.passphraseSecretAlias != null) {
        "Passphrase stored"
    } else {
        "No passphrase stored"
    }
    val keyDetail = state.sshKeySettings.generatedAt?.let { "Generated ${formatTimestampUi(it)}" }
        ?: state.sshKeySettings.customPrivateKeyLabel
        ?: state.sshKeySettings.keyType
    val generateKey: () -> Unit = {
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
                successMessage = "App SSH key generated. Copy the public key or install it on your target."
            }
            .onFailure {
                successMessage = null
                error = it.message
            }
        Unit
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ssh-keys-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("SSH Access")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EntityIcon(Icons.Outlined.Key, if (hasConfiguredSshKey) MetricTone.Success else MetricTone.Warning)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        keyStatus,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        passphraseStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        keyDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(
                    label = if (hasConfiguredSshKey) "Ready" else "Needs setup",
                    tone = if (hasConfiguredSshKey) MetricTone.Success else MetricTone.Warning,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (publicKey == null) {
                    Button(
                        onClick = generateKey,
                        modifier = Modifier.testTag("ssh-generate-key-button"),
                    ) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate app key")
                    }
                } else {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(publicKey)) },
                        modifier = Modifier.testTag("ssh-public-key-copy-button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy public key")
                    }
                }
                if (hasConfiguredSshKey) {
                    OutlinedButton(
                        onClick = { showDeleteWarning = true },
                        modifier = Modifier.testTag("ssh-delete-key-button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete key")
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
                                    successMessage = "SSH key deleted. Generate or store another key before running backups."
                                    showDeleteWarning = false
                                }.onFailure {
                                    successMessage = null
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
            successMessage?.let {
                FeedbackBanner("SSH access updated", it, MetricTone.Success)
            }
            error?.let {
                FeedbackBanner("SSH action failed", it, MetricTone.Destructive)
            }
        }
        SectionCard {
            Text("Use existing key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(customKey, { customKey = it }, label = { Text("Private key") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val keyAlias = "custom-ssh-private-key"
                    val passphraseAlias = "custom-ssh-passphrase"
                    val privateKey = customKey
                    val privateKeyPassphrase = passphrase
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val nativeInstall = NativeBinaryManager(context).ensureInstalled()
                                val sshKeygen = nativeInstall.requireTool("ssh-keygen")
                                val publicKey = SshKeyManager(secretStore).extractPublicKeyFromPrivateKey(
                                    sshKeygenPath = sshKeygen,
                                    filesDir = context.filesDir,
                                    workDir = context.cacheDir,
                                    privateKey = privateKey,
                                    passphrase = privateKeyPassphrase,
                                )
                                SshKeyManager(secretStore).storeCustomPrivateKey(keyAlias, privateKey)
                                if (privateKeyPassphrase.isNotBlank()) {
                                    secretStore.put(passphraseAlias, privateKeyPassphrase.toByteArray())
                                }
                                publicKey
                            }
                        }.onSuccess { publicKey ->
                            repository.update { appState ->
                                appState.copy(
                                    sshKeySettings = GlobalSshKeySettings(
                                        publicKey = publicKey,
                                        privateKeySecretAlias = keyAlias,
                                        customPrivateKeyLabel = "Custom key",
                                        passphraseSecretAlias = passphraseAlias.takeIf { privateKeyPassphrase.isNotBlank() },
                                        generatedAt = Instant.now().toString(),
                                    ),
                                )
                            }
                            customKey = ""
                            passphrase = ""
                            error = null
                            successMessage = "Custom private key stored. Backups can use it for SSH authentication."
                        }.onFailure {
                            successMessage = null
                            error = it.message
                        }
                    }
                },
                enabled = customKey.isNotBlank(),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Store existing key")
            }
        }
        SectionCard {
            Text("Key details", style = MaterialTheme.typography.titleMedium)
            StatusBadge(if (hasStoredPrivateKey) "Private key stored" else "No private key stored", if (hasStoredPrivateKey) MetricTone.Success else MetricTone.Warning)
            Text(if (isCustomKey) "Custom key stored" else "Generated key", style = MaterialTheme.typography.bodySmall)
            publicKey?.let { value ->
                Text("Public key preview", style = MaterialTheme.typography.labelLarge)
                SelectableBlock(value)
            }
        }
    }
}

@Composable
private fun TailscaleScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultNodeName = effectiveTailscaleNodeName(state)
    var nodeName by rememberSaveable(defaultNodeName) { mutableStateOf(defaultNodeName) }
    var authKey by rememberSaveable { mutableStateOf("") }
    val defaultTestTarget = state.targets.firstOrNull { !it.tailscaleHost.isNullOrBlank() }
    var testHost by rememberSaveable(defaultTestTarget?.tailscaleHost) {
        mutableStateOf(defaultTestTarget?.tailscaleHost.orEmpty())
    }
    var testPort by rememberSaveable(defaultTestTarget?.port) {
        mutableStateOf((defaultTestTarget?.port ?: 22).toString())
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var showSignOutWarning by rememberSaveable { mutableStateOf(false) }
    val tailscaleLastError = state.tailscale.lastError
    val connectionStatus = when {
        tailscaleLastError != null -> "Last route test failed"
        state.tailscale.isConfigured -> "Connected as ${state.tailscale.nodeName}"
        else -> "Not connected"
    }
    val connectionTone = when {
        tailscaleLastError != null -> MetricTone.Destructive
        state.tailscale.isConfigured -> MetricTone.Success
        else -> MetricTone.Warning
    }
    if (showSignOutWarning) {
        AlertDialog(
            onDismissRequest = { showSignOutWarning = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text("Sign out of Tailscale?") },
            text = { Text("Tailscale backups and Tailscale device browsing will stop until you sign in again.") },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("tailscale-reset-button"),
                    onClick = {
                        showSignOutWarning = false
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                TailscaleManager(context, secretStore).reset(state.tailscale.stateSecretAlias)
                            }
                            repository.update { appState ->
                                appState.copy(
                                    tailscale = TailscaleStateMetadata(
                                        nodeName = suggestedTailscaleNodeName(appState.settings.phoneHostname),
                                    ),
                                )
                            }
                            message = "Signed out of Tailscale"
                            busy = false
                        }
                    },
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutWarning = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tailscale-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Tailscale Connection")
        Text(
            "Optional. Use Tailscale when the server has no public address or you prefer a private mesh connection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EntityIcon(Icons.Outlined.Cloud, connectionTone)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(connectionStatus, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Node name: ${state.tailscale.nodeName.ifBlank { nodeName }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                tailscaleLastError != null -> FeedbackBanner(
                    title = "Tailscale connection failed",
                    detail = friendlyTailscaleError(tailscaleLastError),
                    tone = MetricTone.Destructive,
                )
                state.tailscale.isConfigured -> FeedbackBanner(
                    title = "Tailscale is connected",
                    detail = "Node ${state.tailscale.nodeName} is ready for route tests and Tailscale backups.",
                    tone = MetricTone.Success,
                )
                else -> FeedbackBanner(
                    title = "Tailscale is not connected",
                    detail = "Sign in if you want to back up to a Tailscale device.",
                    tone = MetricTone.Warning,
                )
            }
            AdvancedSection("Advanced sign-in") {
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
                Button(
                    enabled = !busy && nodeName.isNotBlank() && authKey.isNotBlank(),
                    modifier = Modifier.testTag("tailscale-authenticate-button"),
                    onClick = {
                        busy = true
                        message = "Authenticating"
                        val requestedNodeName = nodeName.trim().ifBlank { defaultNodeName }
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                TailscaleManager(context, secretStore).authenticate(
                                    nodeName = requestedNodeName,
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
                                            nodeName = requestedNodeName,
                                            stateSecretAlias = result.stateSecretAlias,
                                            lastLoginAt = now,
                                            lastReachabilityTestAt = appState.tailscale.lastReachabilityTestAt,
                                            lastError = null,
                                            keyExpiryAdviceAcknowledged = appState.tailscale.keyExpiryAdviceAcknowledged,
                                        )
                                    } else {
                                        appState.tailscale.copy(
                                            nodeName = requestedNodeName,
                                            lastError = result.output.ifBlank { "Tailscale auth failed" },
                                        )
                                    },
                                )
                            }
                            message = if (result.success) {
                                "Connected as $requestedNodeName"
                            } else {
                                "Connection failed"
                            }
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect with auth key")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy && nodeName.isNotBlank(),
                    modifier = Modifier.testTag("tailscale-browser-login-button"),
                    onClick = {
                        busy = true
                        message = "Waiting for Tailscale login in browser"
                        val requestedNodeName = nodeName.trim().ifBlank { defaultNodeName }
                        val browserOpened = AtomicBoolean(false)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                TailscaleManager(context, secretStore).authenticateWithBrowser(
                                    nodeName = requestedNodeName,
                                ) { authUrl ->
                                    if (browserOpened.compareAndSet(false, true)) {
                                        scope.launch {
                                            val opened = openUrlInUserBrowser(context, authUrl)
                                            message = if (opened) {
                                                "Complete Tailscale login in your browser"
                                            } else {
                                                "Could not open a browser for Tailscale login"
                                            }
                                        }
                                    }
                                }
                            }
                            val now = Instant.now().toString()
                            repository.update { appState ->
                                appState.copy(
                                    tailscale = if (result.success) {
                                        TailscaleStateMetadata(
                                            isConfigured = true,
                                            nodeName = requestedNodeName,
                                            stateSecretAlias = result.stateSecretAlias,
                                            lastLoginAt = now,
                                            lastReachabilityTestAt = appState.tailscale.lastReachabilityTestAt,
                                            lastError = null,
                                            keyExpiryAdviceAcknowledged = appState.tailscale.keyExpiryAdviceAcknowledged,
                                        )
                                    } else {
                                        appState.tailscale.copy(
                                            nodeName = requestedNodeName,
                                            lastError = browserLoginFailureMessage(result.output, browserOpened.get()),
                                        )
                                    },
                                )
                            }
                            message = if (result.success) {
                                "Connected as $requestedNodeName"
                            } else {
                                "Browser login failed"
                            }
                            busy = false
                            if (result.success && browserOpened.get()) {
                                returnToAppAfterBrowserLogin(context)
                            }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with browser")
                }
                OutlinedButton(
                    enabled = !busy && state.tailscale.isConfigured,
                    onClick = { showSignOutWarning = true },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
            message?.let {
                FeedbackBanner(
                    title = "Latest Tailscale action",
                    detail = it,
                    tone = when {
                        busy -> MetricTone.Route
                        it.contains("failed", ignoreCase = true) -> MetricTone.Destructive
                        it.contains("signed out", ignoreCase = true) -> MetricTone.Warning
                        else -> MetricTone.Success
                    },
                )
            }
        }
        AdvancedSection("Route test") {
            TailscaleHostPicker(
                state = state,
                secretStore = secretStore,
                value = testHost,
                onValueChange = { testHost = it },
                label = "Tailscale device",
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier
                    .fillMaxWidth()
                    .testTag("tailscale-test-host-field"),
                loadButtonTag = "tailscale-load-peers-button",
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
                        message = if (result.success) "Route test succeeded" else "Route test failed"
                        busy = false
                    }
                },
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test route")
            }
        }
        AdvancedSection("Tailscale details") {
            ToggleRow("Key expiry advice acknowledged", state.tailscale.keyExpiryAdviceAcknowledged) { checked ->
                repository.update { appState ->
                    appState.copy(tailscale = appState.tailscale.copy(keyExpiryAdviceAcknowledged = checked))
                }
            }
            Text("Last login: ${state.tailscale.lastLoginAt ?: "none"}")
            Text("Last route test: ${state.tailscale.lastReachabilityTestAt ?: "none"}")
            message?.let {
                FeedbackBanner(
                    title = "Latest Tailscale action",
                    detail = it,
                    tone = when {
                        busy -> MetricTone.Route
                        it.contains("failed", ignoreCase = true) -> MetricTone.Destructive
                        it.contains("signed out", ignoreCase = true) -> MetricTone.Warning
                        else -> MetricTone.Success
                    },
                )
            }
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
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        onRefreshPermissions()
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
            context.startActivity(PermissionIntents.allFilesAccess(context))
        }
        PermissionRow(
            label = "Battery optimization exemption",
            detail = "Keeps scheduled backups from being stopped in the background.",
            granted = permissions.batteryOptimizationExempt,
        ) {
            context.startActivity(PermissionIntents.batteryOptimization(context))
        }
        PermissionRow(
            label = "Exact alarm access",
            detail = "Lets scheduled backups start at the configured time.",
            granted = permissions.exactAlarmAccess,
        ) {
            context.startActivity(PermissionIntents.exactAlarm(context))
        }
        PermissionRow(
            label = "Notifications",
            detail = "Shows backup progress, completion, and failures.",
            granted = permissions.notifications,
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onRefreshPermissions()
            }
        }
        Text("Optional", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        PermissionRow(
            label = "Wi-Fi/SSID access",
            detail = "Only needed if you want backups to run only on Wi-Fi or only on a specific network.",
            granted = permissions.wifiStateAccess,
        ) {
            wifiPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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

private fun formatTimestampUi(value: String): String {
    val compact = value
        .substringBefore('.')
        .removeSuffix("Z")
        .replace('T', ' ')
    return compact.takeIf { it.length >= 16 }?.take(16) ?: value
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
                    SectionHeader("Logs")
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
                            StatusBadge(log.status.displayLabel(), log.status.tone())
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

private fun RunStatus.displayLabel(): String =
    when (this) {
        RunStatus.SUCCESS -> "Success"
        RunStatus.WARNING -> "Warning"
        RunStatus.FAILED -> "Failed"
        RunStatus.CANCELLED -> "Cancelled"
        RunStatus.RUNNING -> "Running"
        RunStatus.QUEUED -> "Queued"
        RunStatus.NEVER_RUN -> "Never run"
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
    onStartOnboarding: (OnboardingStep) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var settings by remember(state.settings) { mutableStateOf(state.settings) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val exportText = remember(state) { ExportCodec.encode(state.toExportDocument()) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(exportText.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open export file")
        }.onSuccess {
            exportMessage = "Configuration saved"
        }.onFailure {
            exportMessage = it.message ?: "Configuration save failed"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Settings and import/export")
        SectionCard {
            Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SettingsToolRow("SSH Access", "Generate, copy, import, or delete the app key", Icons.Outlined.Key) {
                onSelectScreen(Screen.SshKeys)
            }
            SettingsToolRow("Tailscale", "Connect, test routes, and reset state", Icons.Outlined.Cloud) {
                onSelectScreen(Screen.Tailscale)
            }
            SettingsToolRow(
                label = "Run setup guide",
                detail = "Open onboarding from the beginning",
                icon = Icons.Outlined.CheckCircle,
                testTag = "settings-run-setup-guide",
            ) {
                onStartOnboarding(OnboardingStep.Welcome)
            }
        }
        PermissionSettingsSection(permissions, onRefreshPermissions)
        SectionCard {
            ThemePreferenceSelector(settings.themePreference) { preference ->
                val updated = settings.copy(themePreference = preference)
                settings = updated
                repository.update { it.withUpdatedSettings(updated) }
            }
            OutlinedTextField(settings.phoneHostname, { settings = settings.copy(phoneHostname = it) }, label = { Text("Phone hostname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.logRetentionLimit.toString(), { settings = settings.copy(logRetentionLimit = it.toIntOrNull() ?: settings.logRetentionLimit) }, label = { Text("Log retention") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { repository.update { it.withUpdatedSettings(settings) } }) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
        SectionCard {
            Text("Export", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(exportText))
                        exportMessage = "Configuration copied"
                    },
                    modifier = Modifier.testTag("settings-export-copy-button"),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = { exportLauncher.launch("pocketbackup-config.json") },
                    modifier = Modifier.testTag("settings-export-save-button"),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
            exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun SettingsToolRow(
    label: String,
    detail: String,
    icon: ImageVector,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (testTag?.let { Modifier.testTag(it) } ?: Modifier)
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
    val target = state.targets.firstOrNull { it.id == profile.targetId } ?: return
    val route = when (profile.targetMode) {
        TargetMode.TAILSCALE_FIRST_LAN_FALLBACK, TargetMode.TAILSCALE_ONLY -> Route.TAILSCALE
        else -> Route.LAN
    }
    val preview = runCatching {
        RsyncCommandBuilder.build(
            profile = profile,
            target = target,
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
private fun TargetModeSelector(
    targetMode: TargetMode,
    target: TargetRecord? = null,
    onChange: (TargetMode) -> Unit,
) {
    Selector("Target mode") {
        TargetMode.entries.forEach { mode ->
            val unavailableReason = target?.let { mode.unavailableReason(it) }
            FilterChip(
                selected = targetMode == mode,
                onClick = { if (unavailableReason == null) onChange(mode) },
                enabled = unavailableReason == null,
                label = { Text(routeModeLabel(mode)) },
                modifier = Modifier.testTag("target-mode-${mode.name.lowercase()}"),
            )
        }
    }
    target?.let { selectedTarget ->
        unavailableTargetModeMessage(selectedTarget)?.let { message ->
            FeedbackBanner("Target mode unavailable", message, MetricTone.Warning)
        }
    }
}

@Composable
private fun RemotePathPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    target: TargetRecord?,
    routes: List<Route>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fieldTag: String? = null,
    browseButtonTag: String? = null,
    onBrowse: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier
                .weight(1f)
                .then(fieldTag?.let { Modifier.testTag(it) } ?: Modifier),
        )
        OutlinedButton(
            enabled = enabled && target != null && routes.isNotEmpty(),
            onClick = onBrowse,
            modifier = browseButtonTag?.let { Modifier.testTag(it) } ?: Modifier,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Browse")
        }
    }
}

@Composable
private fun RemotePathBrowserScreen(
    title: String,
    state: AppState,
    target: TargetRecord,
    routes: List<Route>,
    startPath: String,
    secretStore: SecretStore,
    onPathSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browser = remember(context, secretStore) { SshRemotePathBrowser(context, secretStore) }
    var draftPath by rememberSaveable(target.id, startPath) { mutableStateOf(startPath.trim().ifBlank { "~" }) }
    var listing by remember(target.id, startPath) { mutableStateOf<SshRemotePathListing?>(null) }
    var session by remember(target.id, startPath) { mutableStateOf<SshRemotePathBrowserSession?>(null) }
    val activeSession = rememberUpdatedState(session)
    var loading by rememberSaveable(target.id, startPath) { mutableStateOf(false) }
    var connecting by rememberSaveable(target.id, startPath) { mutableStateOf(true) }
    var error by rememberSaveable(target.id, startPath) { mutableStateOf<String?>(null) }
    var showHidden by rememberSaveable(target.id) { mutableStateOf(false) }
    var loadingPath by rememberSaveable(target.id, startPath) { mutableStateOf<String?>(null) }

    fun load(path: String, rowPath: String? = null) {
        val currentSession = session ?: return
        val requestedPath = path.trim().ifBlank { "~" }
        if (rowPath == null) {
            draftPath = requestedPath
        }
        loading = true
        loadingPath = rowPath
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    currentSession.listDirectories(requestedPath, showHidden)
                }
            }.onSuccess { result ->
                listing = result
                draftPath = result.resolvedPath
                error = null
            }.onFailure { failure ->
                error = failure.message ?: "Remote path browser failed"
                listing?.resolvedPath?.let { currentPath ->
                    draftPath = currentPath
                }
            }
            loadingPath = null
            loading = false
        }
    }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        onDispose {
            activeSession.value?.let { closingSession ->
                scope.launch(Dispatchers.IO) {
                    closingSession.close()
                }
            }
        }
    }

    LaunchedEffect(target.id, target.user, target.lanHost, target.tailscaleHost, target.port, routes, state.sshKeySettings, state.trustedHostFingerprints, state.tailscale) {
        val previousSession = session
        session = null
        previousSession?.let { closingSession ->
            withContext(Dispatchers.IO) { closingSession.close() }
        }
        connecting = true
        loading = true
        loadingPath = null
        error = null
        listing = null
        runCatching {
            withContext(Dispatchers.IO) {
                val openedSession = browser.openSession(state, target, routes)
                openedSession to openedSession.listDirectories(draftPath, showHidden)
            }
        }.onSuccess { (openedSession, firstListing) ->
            session = openedSession
            listing = firstListing
            draftPath = firstListing.resolvedPath
            error = null
        }.onFailure { failure ->
            error = failure.message ?: "Remote path browser failed"
        }
        connecting = false
        loading = false
    }

    LaunchedEffect(showHidden) {
        session?.let { currentSession ->
            loading = true
            loadingPath = null
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    currentSession.listDirectories(draftPath, showHidden)
                }
            }.onSuccess { result ->
                listing = result
                draftPath = result.resolvedPath
                error = null
            }.onFailure { failure ->
                error = failure.message ?: "Remote path browser failed"
                listing?.resolvedPath?.let { currentPath ->
                    draftPath = currentPath
                }
            }
            loading = false
        }
    }

    val selectedPath = listing?.resolvedPath ?: draftPath.trim().ifBlank { "~" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("remote-path-browser-screen"),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${target.user}@${target.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(
                        label = listing?.let { "via ${routeLabel(it.route)}" }
                            ?: when {
                                connecting -> "Connecting"
                                error != null -> "Failed"
                                else -> "Ready"
                            },
                        tone = if (listing != null || error == null) MetricTone.Route else MetricTone.Destructive,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = draftPath,
                        onValueChange = { draftPath = it },
                        label = { Text("Current path") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("remote-path-browser-current-path-field"),
                    )
                    OutlinedButton(
                        enabled = !loading && session != null,
                        onClick = { load(draftPath) },
                        modifier = Modifier.testTag("remote-path-browser-go-button"),
                    ) {
                        Text("Go")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ToggleRow(
                        label = "Show hidden folders",
                        checked = showHidden,
                        switchTag = "remote-path-browser-hidden-switch",
                    ) { showHidden = it }
                }
                listing?.let { result ->
                    Text(
                        "${result.resolvedPath} on ${result.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (connecting || (loading && listing == null)) {
                FeedbackBanner(
                    title = if (connecting) "Opening SSH session" else "Browsing server",
                    detail = if (connecting) {
                        "Connecting to ${target.name}"
                    } else {
                        "Loading $draftPath"
                    },
                    tone = MetricTone.Route,
                )
            } else if (loading && loadingPath == null) {
                RemotePathLoadingStrip("Refreshing folders")
            }
            error?.let {
                FeedbackBanner(
                    title = "Browse failed",
                    detail = conciseFeedbackMessage(it),
                    tone = MetricTone.Destructive,
                )
            }
            listing?.let { result ->
                RemotePathList(
                    listing = result,
                    loading = loading,
                    loadingPath = loadingPath,
                    onOpenPath = { path -> load(path, rowPath = path) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text("Cancel")
                }
                Button(
                    enabled = !loading && selectedPath.isNotBlank() && listing != null,
                    onClick = { onPathSelected(selectedPath) },
                    modifier = Modifier.testTag("remote-path-browser-use-button"),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use path")
                }
            }
        }
    }
}

@Composable
private fun RemotePathList(
    listing: SshRemotePathListing,
    loading: Boolean,
    loadingPath: String?,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("remote-path-browser-list"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listing.parentPath?.let { parentPath ->
            item(key = "parent") {
                RemotePathRow(
                    label = "Parent folder",
                    path = parentPath,
                    enabled = !loading,
                    loading = loadingPath == parentPath,
                    parent = true,
                    onClick = { onOpenPath(parentPath) },
                )
            }
        }
        items(listing.entries, key = { it.path }) { entry ->
            RemotePathRow(
                label = entry.name,
                path = entry.path,
                enabled = !loading,
                loading = loadingPath == entry.path,
                parent = false,
                onClick = { onOpenPath(entry.path) },
            )
        }
        if (listing.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No child folders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RemotePathRow(
    label: String,
    path: String,
    enabled: Boolean,
    loading: Boolean,
    parent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (parent) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                Text(
                    "Opening",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RemotePathLoadingStrip(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

private fun browseRoutesForTarget(target: TargetRecord): List<Route> =
    buildList {
        if (target.lanHost.isNotBlank()) add(Route.LAN)
        if (!target.tailscaleHost.isNullOrBlank()) add(Route.TAILSCALE)
    }

private fun routeLabel(route: Route): String =
    when (route) {
        Route.LAN -> "Server address"
        Route.TAILSCALE -> "Tailscale device"
    }

@Composable
private fun ScheduleEditor(schedule: BackupSchedule, onChange: (BackupSchedule) -> Unit) {
    Selector {
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
private fun PrimaryConstraintEditor(constraints: ConstraintSettings, onChange: (ConstraintSettings) -> Unit) {
    ToggleRow("Wi-Fi only", constraints.wifiOnly) { onChange(constraints.copy(wifiOnly = it)) }
    ToggleRow("Charging only", constraints.chargingOnly) { onChange(constraints.copy(chargingOnly = it)) }
}

@Composable
private fun AdvancedConstraintEditor(
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
private fun WifiSsidSelector(
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
private fun PermissionRow(label: String, detail: String, granted: Boolean, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusIcon(if (granted) RunStatus.SUCCESS else RunStatus.FAILED)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onOpen) {
            Text(if (granted) "Open" else "Grant")
        }
    }
}

@Composable
private fun ChecklistRow(label: String, complete: Boolean, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusIcon(if (complete) RunStatus.SUCCESS else RunStatus.FAILED)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusBadge(
            label = if (complete) "Done" else "Needs setup",
            tone = if (complete) MetricTone.Success else MetricTone.Warning,
        )
    }
}

@Composable
private fun FeedbackBanner(
    title: String,
    detail: String,
    tone: MetricTone,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = toneContainerColor(tone),
        contentColor = toneOnContainerColor(tone),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = when (tone) {
                    MetricTone.Success -> Icons.Outlined.CheckCircle
                    MetricTone.Destructive -> Icons.Outlined.Error
                    MetricTone.Warning -> Icons.Outlined.Warning
                    else -> Icons.Outlined.Sync
                },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
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
private fun Selector(title: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
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
private fun AdvancedSection(
    title: String = "Advanced settings",
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
        contentDescription = status.displayLabel(),
    )
}

@Composable
private fun ErrorText(text: String) {
    FeedbackBanner(
        title = "Action failed",
        detail = conciseFeedbackMessage(text),
        tone = MetricTone.Destructive,
    )
}

private fun conciseFeedbackMessage(text: String, maxLength: Int = 260): String {
    val meaningfulLine = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .lastOrNull()
        ?: text.trim()
    return meaningfulLine.take(maxLength).let { value ->
        if (meaningfulLine.length > maxLength) "$value..." else value
    }
}

private val UrlRedactionRegex = Regex("""https?://\S+""")
private val PreferredCustomTabsPackages = listOf(
    "com.android.chrome",
    "com.google.android.apps.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.chrome.canary",
    "com.brave.browser",
    "com.microsoft.emmx",
    "com.kiwibrowser.browser",
    "org.mozilla.firefox",
    "org.mozilla.fenix",
    "com.sec.android.app.sbrowser",
)
private val NonBrowserCustomTabsPackages = setOf(
    "fe.linksheet",
    "fe.linksheet.nightly",
)

private fun openUrlInUserBrowser(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    val customTabsPackage = customTabsBrowserPackage(context)
    val launchContext = context.findActivity() ?: context
    return try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        if (customTabsPackage != null) {
            customTabsIntent.intent.setPackage(customTabsPackage)
        }
        if (launchContext !is Activity) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(launchContext, uri)
        true
    } catch (error: ActivityNotFoundException) {
        openUrlWithViewIntent(context, uri, customTabsPackage)
    } catch (error: IllegalArgumentException) {
        openUrlWithViewIntent(context, uri, customTabsPackage)
    } catch (error: RuntimeException) {
        openUrlWithViewIntent(context, uri, customTabsPackage)
    }
}

private fun customTabsBrowserPackage(context: Context): String? =
    CustomTabsClient.getPackageName(context, PreferredCustomTabsPackages)
        ?: CustomTabsClient.getPackageName(context, null)
            ?.takeUnless { it in NonBrowserCustomTabsPackages || it.startsWith("fe.linksheet.") }

private fun openUrlWithViewIntent(context: Context, uri: Uri, packageName: String?): Boolean =
    runCatching {
        val launchContext = context.findActivity() ?: context
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (packageName != null) {
            intent.setPackage(packageName)
        }
        launchContext.startActivity(intent)
    }.isSuccess

private fun returnToAppAfterBrowserLogin(context: Context): Boolean =
    runCatching {
        val launchContext = context.findActivity() ?: context
        val intent = Intent(launchContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchContext.startActivity(intent)
    }.isSuccess

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun browserLoginFailureMessage(output: String, browserOpened: Boolean): String {
    val sanitizedOutput = redactUrls(output)
    return when {
        sanitizedOutput.contains("timed out", ignoreCase = true) ->
            "Tailscale browser login timed out before authorization completed."
        !browserOpened && sanitizedOutput.isBlank() ->
            "Tailscale did not provide a browser login URL."
        sanitizedOutput.isBlank() ->
            "Tailscale browser login failed before authorization completed."
        else -> conciseFeedbackMessage(sanitizedOutput)
    }
}

private fun redactUrls(text: String): String =
    UrlRedactionRegex.replace(text, "[redacted-url]")

private fun friendlyTailscaleError(error: String): String =
    when {
        error.contains("invalid key", ignoreCase = true) ->
            "The auth key was rejected. Generate a new Tailscale auth key and paste it here."
        error.contains("NeedsLogin", ignoreCase = true) ->
            "Tailscale still needs login. Paste a valid auth key and connect again."
        error.contains("failed", ignoreCase = true) ->
            conciseFeedbackMessage(error)
        else -> conciseFeedbackMessage(error)
    }
