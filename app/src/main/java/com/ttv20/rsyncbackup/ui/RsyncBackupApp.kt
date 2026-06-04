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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ttv20.rsyncbackup.R
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.backup.BinaryPaths
import com.ttv20.rsyncbackup.backup.AndroidConstraintSnapshotReader
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
    Targets("Targets", Icons.Outlined.Storage),
    Logs("Logs", Icons.Outlined.Article),
    SshKeys("SSH Access", Icons.Outlined.Key),
    Tailscale("Tailscale", Icons.Outlined.Cloud),
    Settings("Settings", Icons.Outlined.Settings),
}

private val MainScreens = listOf(Screen.Dashboard, Screen.Profiles, Screen.Targets, Screen.Logs)

private enum class OnboardingStep(val title: String) {
    Welcome("Welcome"),
    Permissions("Permissions"),
    SshAccess("SSH Access"),
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

    RsyncBackupTheme(themePreference = state.settings.themePreference) {
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
        mutableStateOf(defaultOnboardingProfile(state, initialTarget))
    }
    var savedProfileId by rememberSaveable(profileDraft.id) {
        mutableStateOf<String?>(profileDraft.id.takeIf { state.profiles.any { profile -> profile.id == profileDraft.id } })
    }
    var dryRunResult by remember { mutableStateOf<DryRunResult?>(null) }
    val step = OnboardingStep.valueOf(currentStep)
    val stepIndex = OnboardingSteps.indexOf(step).coerceAtLeast(0)

    LaunchedEffect(currentStep) {
        repository.update { appState ->
            appState.copy(settings = appState.settings.copy(onboardingLastStep = currentStep))
        }
    }
    LaunchedEffect(savedTargetId) {
        val selectedTarget = state.targets.firstOrNull { it.id == savedTargetId } ?: targetDraft
        profileDraft = profileDraft.copy(
            targetId = selectedTarget.id,
            remotePath = selectedTarget.defaultRemotePath,
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
                    onClick = { requestNavigation(PendingOnboardingNavigation.Back) },
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
                    onContinue = { goTo(OnboardingStep.SshAccess) },
                )
                OnboardingStep.SshAccess -> OnboardingWrappedScreen(
                    onContinue = { goTo(OnboardingStep.Tailscale) },
                ) {
                    SshKeysScreen(state, repository, secretStore)
                }
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
                    onTargetChange = { targetDraft = it },
                    onSave = {
                        saveTargetDraft()
                        goTo(OnboardingStep.NewProfile)
                    },
                )
                OnboardingStep.NewProfile -> OnboardingProfileStep(
                    state = state,
                    profile = profileDraft,
                    onProfileChange = { profileDraft = it },
                    onSave = {
                        saveProfileDraft()
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
            Text("Set up permissions, SSH access, a backup target, and a profile. Every step can be skipped.")
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
    onTargetChange: (TargetRecord) -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingHostKeys by remember(target.id) { mutableStateOf<List<ScannedHostKey>>(emptyList()) }
    var scanTarget by remember(target.id) { mutableStateOf<String?>(null) }
    var scanMessage by remember(target.id) { mutableStateOf<String?>(null) }
    var scanError by remember(target.id) { mutableStateOf<String?>(null) }
    var setupPassword by remember(target.id) { mutableStateOf("") }
    var setupTarget by remember(target.id) { mutableStateOf<String?>(null) }
    var setupMessage by remember(target.id) { mutableStateOf<String?>(null) }
    var setupError by remember(target.id) { mutableStateOf<String?>(null) }
    val trustedEntries = state.trustedHostFingerprints.filter {
        it.targetId == target.id || it.targetId == target.fingerprintGroupId
    }
    val setupPrerequisiteMessage = publicKeySetupPrerequisiteMessage(
        publicKeyInstalled = target.publicKeyInstalledAt != null,
        setupPassword = setupPassword,
        publicKey = state.sshKeySettings.publicKey,
        hasTrustedHostKey = trustedEntries.isNotEmpty(),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard {
            Text("Target details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(target.name, { onTargetChange(target.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(target.user, { onTargetChange(target.copy(user = it)) }, label = { Text("User") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(target.lanHost, { onTargetChange(target.copy(lanHost = it)) }, label = { Text("LAN host") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                target.port.toString(),
                { value -> onTargetChange(target.copy(port = value.toIntOrNull()?.coerceIn(1, 65535) ?: target.port)) },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                target.tailscaleHost.orEmpty(),
                { onTargetChange(target.copy(tailscaleHost = it.ifBlank { null })) },
                label = { Text("Optional Tailscale host") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                target.defaultRemotePath,
                { onTargetChange(target.copy(defaultRemotePath = it)) },
                label = { Text("Default remote path") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard {
            Text("Fingerprint and key install", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("LAN and Tailscale addresses share fingerprint group ${target.fingerprintGroupId}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    enabled = scanTarget == null && target.lanHost.isNotBlank(),
                    modifier = Modifier.testTag("onboarding-target-scan-lan-button"),
                    onClick = {
                        scanTarget = "LAN"
                        scanMessage = null
                        scanError = null
                        pendingHostKeys = emptyList()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    SshHostKeyScanner(context).scanAll(target.lanHost, target.port)
                                }
                            }.onSuccess {
                                if (it.isEmpty()) {
                                    scanError = "No SSH host keys were returned from ${target.lanHost}:${target.port}."
                                } else {
                                    pendingHostKeys = it
                                    scanMessage = "Host key found. Review the fingerprint, then trust it to continue."
                                }
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
                    enabled = scanTarget == null && !target.tailscaleHost.isNullOrBlank(),
                    modifier = Modifier.testTag("onboarding-target-scan-tailscale-button"),
                    onClick = {
                        val host = target.tailscaleHost ?: return@FilledTonalButton
                        scanTarget = "Tailscale"
                        scanMessage = null
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
                                            port = target.port,
                                            user = target.user,
                                            tailscaleStateDir = stateDir,
                                            tailscaleNodeName = state.tailscale.nodeName,
                                        )
                                    }
                                }
                            }.onSuccess {
                                if (it.isEmpty()) {
                                    scanError = "No SSH host keys were returned from $host:${target.port}."
                                } else {
                                    pendingHostKeys = it
                                    scanMessage = "Host key found over Tailscale. Review the fingerprint, then trust it to continue."
                                }
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
                    modifier = Modifier.testTag("onboarding-target-trust-scanned-key-button"),
                    onClick = {
                        val trusted = pendingHostKeys.map { scanned ->
                            com.ttv20.rsyncbackup.model.TrustedHostFingerprint(
                                id = UUID.randomUUID().toString(),
                                targetId = target.fingerprintGroupId,
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
                                targets = appState.targets.filterNot { it.id == target.id } + target,
                                trustedHostFingerprints = appState.trustedHostFingerprints
                                    .filterNot { existing ->
                                        pendingHostKeys.any { scanned ->
                                            existing.targetId == target.fingerprintGroupId &&
                                                existing.hostnames.contains(scanned.hostname) &&
                                                existing.port == scanned.port &&
                                                existing.algorithm == scanned.algorithm
                                        }
                                    } + trusted,
                            )
                        }
                        pendingHostKeys = emptyList()
                        scanMessage = "Host key trusted. This target now has a saved fingerprint."
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
            scanMessage?.let {
                FeedbackBanner("Fingerprint step updated", it, MetricTone.Success)
            }
            scanError?.let {
                FeedbackBanner("Host key scan failed", it, MetricTone.Destructive)
            }
        }
        SectionCard {
            Text("Install public key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = setupPassword,
                onValueChange = { setupPassword = it },
                label = { Text("SSH password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding-target-setup-password-field"),
            )
            setupPrerequisiteMessage?.let {
                FeedbackBanner("Public key setup needs attention", it, MetricTone.Warning)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = setupTarget == null && setupPassword.isNotBlank() && target.lanHost.isNotBlank(),
                    modifier = Modifier.testTag("onboarding-target-install-over-lan-button"),
                    onClick = {
                        val publicKey = state.sshKeySettings.publicKey
                        if (publicKey == null) {
                            setupError = "Generate or store an SSH public key before setup."
                            return@Button
                        }
                        if (trustedEntries.isEmpty()) {
                            setupError = "Scan and trust this target host key before setup."
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
                                        target = target,
                                        trustedHostFingerprints = state.trustedHostFingerprints,
                                        publicKey = publicKey,
                                        password = password,
                                        workDir = context.cacheDir,
                                        hostname = target.lanHost,
                                    )
                                }
                            }.onSuccess { result ->
                                if (result.isSuccess) {
                                    val updatedTarget = target.copy(publicKeyInstalledAt = Instant.now().toString())
                                    onTargetChange(updatedTarget)
                                    repository.upsertTarget(updatedTarget)
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
                    enabled = setupTarget == null && setupPassword.isNotBlank() && !target.tailscaleHost.isNullOrBlank(),
                    modifier = Modifier.testTag("onboarding-target-install-over-tailscale-button"),
                    onClick = {
                        val publicKey = state.sshKeySettings.publicKey
                        if (publicKey == null) {
                            setupError = "Generate or store an SSH public key before setup."
                            return@OutlinedButton
                        }
                        if (trustedEntries.isEmpty()) {
                            setupError = "Scan and trust this target host key before setup."
                            return@OutlinedButton
                        }
                        if (!state.tailscale.isConfigured || state.tailscale.stateSecretAlias == null) {
                            setupError = "Configure Tailscale before installing over Tailscale."
                            return@OutlinedButton
                        }
                        val host = target.tailscaleHost ?: return@OutlinedButton
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
                                            target = target,
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
                                    val updatedTarget = target.copy(publicKeyInstalledAt = Instant.now().toString())
                                    onTargetChange(updatedTarget)
                                    repository.upsertTarget(updatedTarget)
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
            setupMessage?.let {
                FeedbackBanner("Public key installed", it, MetricTone.Success)
            }
            setupError?.let {
                FeedbackBanner("Public key install failed", it, MetricTone.Destructive)
            }
        }
        Button(onClick = onSave, modifier = Modifier.testTag("onboarding-save-target-button")) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save target")
        }
    }
}

@Composable
private fun OnboardingProfileStep(
    state: AppState,
    profile: BackupProfile,
    onProfileChange: (BackupProfile) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard {
            Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(profile.name, { onProfileChange(profile.copy(name = it)) }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(profile.sourcePath, { onProfileChange(profile.copy(sourcePath = it)) }, label = { Text("Source path") }, modifier = Modifier.fillMaxWidth())
            Selector("Selected target") {
                state.targets.forEach { target ->
                    FilterChip(
                        selected = profile.targetId == target.id,
                        onClick = {
                            onProfileChange(
                                profile.copy(
                                    targetId = target.id,
                                    remotePath = target.defaultRemotePath,
                                    targetMode = defaultTargetModeFor(target, profile.targetMode),
                                ),
                            )
                        },
                        label = { Text(target.name) },
                    )
                }
            }
            OutlinedTextField(profile.remotePath, { onProfileChange(profile.copy(remotePath = it)) }, label = { Text("Remote path") }, modifier = Modifier.fillMaxWidth())
            TargetModeSelector(
                targetMode = profile.targetMode,
                target = state.targets.firstOrNull { it.id == profile.targetId },
            ) {
                onProfileChange(profile.copy(targetMode = it))
            }
        }
        SectionCard {
            Text("Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            WarningRow(
                title = "Delete remote files not present locally",
                detail = "Only enable this for a dedicated backup directory.",
                checked = profile.deleteEnabled,
            ) {
                onProfileChange(profile.copy(deleteEnabled = it))
            }
        }
        Button(onClick = onSave, modifier = Modifier.testTag("onboarding-save-profile-button")) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save profile")
        }
    }
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
    val constraintSnapshot = remember(context, state.settings.selectedSsid, profileId) {
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

private fun defaultOnboardingProfile(state: AppState, target: TargetRecord): BackupProfile {
    val selectedTarget = state.targets.firstOrNull { it.id == target.id } ?: state.targets.firstOrNull() ?: target
    val targetMode = defaultTargetModeFor(selectedTarget)
    state.profiles.firstOrNull()?.let { existing ->
        return existing.copy(
            targetId = selectedTarget.id,
            remotePath = selectedTarget.defaultRemotePath,
            targetMode = targetMode,
        )
    }
    return BackupProfile(
        id = UUID.randomUUID().toString(),
        name = "Phone backup",
        sourcePath = "/storage/emulated/0",
        targetId = selectedTarget.id,
        remotePath = selectedTarget.defaultRemotePath,
        targetMode = targetMode,
        excludes = state.profiles.firstOrNull()?.excludes.orEmpty(),
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
        selectedSsid = state.settings.selectedSsid,
    )
    return listOf(
        SetupChecklistItem("Permissions approved", permissions.allRequiredGranted, "Grant permissions", OnboardingStep.Permissions),
        SetupChecklistItem("SSH key exists", state.sshKeySettings.privateKeySecretAlias != null, "Set up SSH access", OnboardingStep.SshAccess),
        SetupChecklistItem("Target fingerprint trusted", trusted, "Trust fingerprint", OnboardingStep.NewTarget),
        SetupChecklistItem(
            "Public key installed on target",
            target?.publicKeyInstalledAt != null,
            "Install public key",
            OnboardingStep.NewTarget,
        ),
        SetupChecklistItem(
            "Tailscale configured if needed",
            !profile.targetMode.requiresTailscale() || state.tailscale.isConfigured,
            "Connect Tailscale",
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

private fun publicKeySetupPrerequisiteMessage(
    publicKeyInstalled: Boolean,
    setupPassword: String,
    publicKey: String?,
    hasTrustedHostKey: Boolean,
): String? {
    if (publicKeyInstalled) return null
    return when {
        setupPassword.isBlank() -> "Enter the one-time SSH password to install the public key."
        publicKey == null -> "Generate or store an SSH public key before setup."
        !hasTrustedHostKey -> "Scan and trust this target host key before setup."
        else -> null
    }
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

private fun suggestedTailscaleNodeName(phoneHostname: String): String {
    val hostname = phoneHostname
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9-]+"), "-")
        .trim('-')
        .ifBlank { "android-phone" }
    return "$hostname-rsync"
}

private fun effectiveTailscaleNodeName(state: AppState): String {
    val suggested = suggestedTailscaleNodeName(state.settings.phoneHostname)
    val stored = state.tailscale.nodeName.trim()
    return when {
        state.tailscale.isConfigured -> stored.ifBlank { suggested }
        stored.isBlank() || stored == "android-rsync" || stored == "android-phone-rsync" -> suggested
        else -> stored
    }
}

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
        needsLan && needsTailscale -> "This mode needs LAN and Tailscale hosts."
        needsLan -> "This mode needs a LAN host."
        needsTailscale -> "This mode needs a Tailscale host."
        else -> null
    }
}

private fun unavailableTargetModeMessage(target: TargetRecord): String? {
    val missing = listOfNotNull(
        "LAN modes are disabled because this target has no LAN host.".takeIf { target.lanHost.isBlank() },
        "Tailscale modes are disabled because this target has no Tailscale host.".takeIf { target.tailscaleHost.isNullOrBlank() },
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
    val readyCount = state.profiles.count { profile ->
        ProfileValidator.validate(profile, state).none { it.severity == Severity.ERROR }
    }
    val warningCount = state.profiles.count { profile ->
        ProfileValidator.validate(profile, state).any { it.severity == Severity.WARNING }
    }
    val constraintSnapshot = remember(context, state.settings.selectedSsid, state.profiles) {
        AndroidConstraintSnapshotReader(context).read()
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
                    MetricSpec("Targets", state.targets.size.toString(), Icons.Outlined.Storage, MetricTone.Neutral),
                ),
            )
        }
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
                Text("${target.user}@${target.lanHost}:${target.port}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RouteSummaryLine("LAN", target.lanHost, MetricTone.Route)
                target.tailscaleHost?.let { RouteSummaryLine("Tailscale", it, MetricTone.Route) }
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
    val addProfile: () -> Unit = {
        state.targets.firstOrNull()?.let { target ->
            onDetailActiveChange(true, closeEditor)
            editorIsDraft = true
            editorProfile = BackupProfile(
                id = UUID.randomUUID().toString(),
                name = "New profile",
                targetId = target.id,
                remotePath = target.defaultRemotePath,
                targetMode = defaultTargetModeFor(target),
                excludes = state.profiles.firstOrNull()?.excludes.orEmpty(),
            )
            compactEditorOpen = true
        }
    }
    val addTargetFromProfile: () -> TargetRecord = {
        val target = defaultTarget("New target", state.targets.size + 1)
        repository.upsertTarget(target)
        target
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
    onDelete: () -> Unit,
    onAddTarget: () -> TargetRecord,
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
    val selectedTarget = state.targets.firstOrNull { it.id == editing.targetId }
    val issues = ProfileValidator.validate(editing, state)
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
                Selector {
                    state.targets.forEach { target ->
                        FilterChip(
                            selected = editing.targetId == target.id,
                            onClick = {
                                editing = editing.copy(
                                    targetId = target.id,
                                    remotePath = target.defaultRemotePath,
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
                                remotePath = target.defaultRemotePath,
                                targetMode = defaultTargetModeFor(target, editing.targetMode),
                            )
                        },
                        label = { Text("Add target") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.testTag("profile-add-target-button"),
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
                TargetModeSelector(
                    targetMode = editing.targetMode,
                    target = selectedTarget,
                ) {
                    editing = editing.copy(targetMode = it)
                }
            }
            SectionCard {
                Text("Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ScheduleEditor(editing.schedule) { editing = editing.copy(schedule = it) }
            }
            ConstraintEditor(editing.constraints) { editing = editing.copy(constraints = it) }
            SectionCard {
                Text("Safety", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                WarningRow("Delete remote files not present locally", "Deletes target files that are not present in the source.", editing.deleteEnabled) {
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
        user = "ttv20",
        lanHost = "192.168.3.200",
        defaultRemotePath = "/mnt/backup/phone",
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember(target.id, target) { mutableStateOf(target) }
    var pendingHostKeys by remember(target.id) { mutableStateOf<List<ScannedHostKey>>(emptyList()) }
    var scanTarget by remember(target.id) { mutableStateOf<String?>(null) }
    var scanMessage by remember(target.id) { mutableStateOf<String?>(null) }
    var scanError by remember(target.id) { mutableStateOf<String?>(null) }
    var setupPassword by remember(target.id) { mutableStateOf("") }
    var setupTarget by remember(target.id) { mutableStateOf<String?>(null) }
    var setupMessage by remember(target.id) { mutableStateOf<String?>(null) }
    var setupError by remember(target.id) { mutableStateOf<String?>(null) }
    var showUnsavedPrompt by rememberSaveable(target.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val hasUnsavedChanges = isDraft || editing != target
    val requestBackState = rememberUpdatedState<() -> Unit> {
        if (hasUnsavedChanges) {
            showUnsavedPrompt = true
        } else {
            onBack?.invoke()
            Unit
        }
    }
    val trustedEntries = state.trustedHostFingerprints.filter {
        it.targetId == editing.id || it.targetId == editing.fingerprintGroupId
    }
    val setupPrerequisiteMessage = remember(
        editing.publicKeyInstalledAt,
        setupPassword,
        state.sshKeySettings.publicKey,
        trustedEntries,
    ) {
        publicKeySetupPrerequisiteMessage(
            publicKeyInstalled = editing.publicKeyInstalledAt != null,
            setupPassword = setupPassword,
            publicKey = state.sshKeySettings.publicKey,
            hasTrustedHostKey = trustedEntries.isNotEmpty(),
        )
    }
    LaunchedEffect(pendingHostKeys, scanMessage, scanError, setupMessage, setupError) {
        if (pendingHostKeys.isNotEmpty() || scanMessage != null || scanError != null || setupMessage != null || setupError != null) {
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
            entityName = "target",
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
            title = if (isDraft) "New Target" else "Target Edit",
            onBack = { requestBackState.value.invoke() },
            backLabel = cancelLabel,
            onSave = { onSave(editing) },
            saveEnabled = true,
            saveButtonTag = "target-save-button",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("target-editor-scroll")
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                Text("Target readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChecklistRow("App key exists", state.sshKeySettings.publicKey != null)
                ChecklistRow("Target fingerprint trusted", trustedEntries.isNotEmpty())
                ChecklistRow("Public key installed on target", editing.publicKeyInstalledAt != null)
            }
            SectionCard {
            Text("Identity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(editing.name, { editing = editing.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                editing.user,
                { editing = editing.copy(user = it) },
                label = { Text("User") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target-user-field"),
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
                    .testTag("target-lan-host-field"),
            )
            OutlinedTextField(editing.tailscaleHost.orEmpty(), { editing = editing.copy(tailscaleHost = it.ifBlank { null }) }, label = { Text("Fallback Tailscale host") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = editing.port.toString(),
                onValueChange = { value -> editing = editing.copy(port = value.toIntOrNull()?.coerceIn(1, 65535) ?: editing.port) },
                label = { Text("Port") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target-port-field"),
            )
            OutlinedTextField(
                editing.defaultRemotePath,
                { editing = editing.copy(defaultRemotePath = it) },
                label = { Text("Default remote path") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target-default-remote-path-field"),
            )
        }
        SectionCard {
            Text("Trusted fingerprint", style = MaterialTheme.typography.titleMedium)
            Text("LAN and Tailscale addresses share fingerprint group ${editing.fingerprintGroupId}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    enabled = scanTarget == null && editing.lanHost.isNotBlank(),
                    modifier = Modifier.testTag("target-scan-lan-button"),
                    onClick = {
                        scanTarget = "LAN"
                        scanMessage = null
                        scanError = null
                        pendingHostKeys = emptyList()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    SshHostKeyScanner(context).scanAll(editing.lanHost, editing.port)
                                }
                            }.onSuccess {
                                if (it.isEmpty()) {
                                    scanError = "No SSH host keys were returned from ${editing.lanHost}:${editing.port}."
                                } else {
                                    pendingHostKeys = it
                                    scanMessage = "Host key found. Review the fingerprint, then trust it to continue."
                                }
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
                        scanMessage = null
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
                                if (it.isEmpty()) {
                                    scanError = "No SSH host keys were returned from $host:${editing.port}."
                                } else {
                                    pendingHostKeys = it
                                    scanMessage = "Host key found over Tailscale. Review the fingerprint, then trust it to continue."
                                }
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
                    modifier = Modifier.testTag("target-trust-scanned-key-button"),
                    onClick = {
                        val trusted = pendingHostKeys.map { scanned ->
                            com.ttv20.rsyncbackup.model.TrustedHostFingerprint(
                                id = UUID.randomUUID().toString(),
                                targetId = editing.fingerprintGroupId,
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
                                targets = appState.targets.filterNot { it.id == editing.id } + editing,
                                trustedHostFingerprints = appState.trustedHostFingerprints
                                    .filterNot { existing ->
                                        pendingHostKeys.any { scanned ->
                                            existing.targetId == editing.fingerprintGroupId &&
                                                existing.hostnames.contains(scanned.hostname) &&
                                                existing.port == scanned.port &&
                                                existing.algorithm == scanned.algorithm
                                        }
                                    } + trusted,
                            )
                        }
                        pendingHostKeys = emptyList()
                        scanMessage = "Host key trusted. This target now has a saved fingerprint."
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
            scanMessage?.let {
                FeedbackBanner("Fingerprint step updated", it, MetricTone.Success)
            }
            scanError?.let {
                FeedbackBanner("Host key scan failed", it, MetricTone.Destructive)
            }
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
                    .testTag("target-setup-password-field"),
            )
            setupPrerequisiteMessage?.let {
                FeedbackBanner("Public key setup needs attention", it, MetricTone.Warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = setupTarget == null && setupPassword.isNotBlank() && editing.lanHost.isNotBlank(),
                    modifier = Modifier.testTag("target-install-over-lan-button"),
                    onClick = {
                        val publicKey = state.sshKeySettings.publicKey
                        if (publicKey == null) {
                            setupError = "Generate or store an SSH public key before setup."
                            return@Button
                        }
                        if (trustedEntries.isEmpty()) {
                            setupError = "Scan and trust this target host key before setup."
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
                                        target = editing,
                                        trustedHostFingerprints = state.trustedHostFingerprints,
                                        publicKey = publicKey,
                                        password = password,
                                        workDir = context.cacheDir,
                                        hostname = editing.lanHost,
                                    )
                                }
                            }.onSuccess { result ->
                                if (result.isSuccess) {
                                    val updatedTarget = editing.copy(publicKeyInstalledAt = Instant.now().toString())
                                    editing = updatedTarget
                                    repository.upsertTarget(updatedTarget)
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
                            setupError = "Scan and trust this target host key before setup."
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
                                            target = editing,
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
                                    val updatedTarget = editing.copy(publicKeyInstalledAt = Instant.now().toString())
                                    editing = updatedTarget
                                    repository.upsertTarget(updatedTarget)
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
            setupMessage?.let {
                FeedbackBanner("Public key installed", it, MetricTone.Success)
            }
            setupError?.let {
                FeedbackBanner("Public key install failed", it, MetricTone.Destructive)
            }
        }
    }
}
}


@Composable
private fun SshKeysScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
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
                    runCatching {
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
                    }.onSuccess {
                        customKey = ""
                        passphrase = ""
                        error = null
                        successMessage = "Custom private key stored. Backups can use it for SSH authentication."
                    }.onFailure {
                        successMessage = null
                        error = it.message
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tailscale-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Tailscale Connection")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EntityIcon(Icons.Outlined.Cloud, connectionTone)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(connectionStatus, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Optional route for targets that need Tailscale or fallback access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    detail = "Paste an auth key and connect only if this target needs a Tailscale route.",
                    tone = MetricTone.Warning,
                )
            }
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
                    Text("Connect Tailscale")
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
                                appState.copy(
                                    tailscale = TailscaleStateMetadata(
                                        nodeName = suggestedTailscaleNodeName(appState.settings.phoneHostname),
                                    ),
                                )
                            }
                            message = "Tailscale reset. Paste a new auth key to connect again."
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset Tailscale")
                }
            }
        }
        SectionCard {
            OutlinedTextField(
                testHost,
                { testHost = it },
                label = { Text("Target Tailscale host") },
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
        SectionCard {
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
                        it.contains("reset", ignoreCase = true) -> MetricTone.Warning
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
                label = { Text(mode.name.lowercase().replace('_', ' ')) },
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
