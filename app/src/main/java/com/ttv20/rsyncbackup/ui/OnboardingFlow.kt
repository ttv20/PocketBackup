@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.BuildConfig
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.backup.AndroidConstraintSnapshotReader
import com.ttv20.rsyncbackup.backup.BackupConstraintEvaluator
import com.ttv20.rsyncbackup.backup.ConstraintSnapshot
import com.ttv20.rsyncbackup.diagnostics.diagnosticsWelcomeDefaultChecked
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ProfileValidator
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.model.requiresTailscale
import com.ttv20.rsyncbackup.permissions.PermissionStateReader
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import java.time.Instant
import java.util.UUID

@Composable
internal fun OnboardingFlow(
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
    var exitAfterImportedPermissions by remember { mutableStateOf(false) }
    var welcomeDiagnosticsEnabled by rememberSaveable {
        mutableStateOf(
            state.settings.diagnosticsEnabled
                ?: diagnosticsWelcomeDefaultChecked(),
        )
    }
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
    LaunchedEffect(exitAfterImportedPermissions, permissions.allRequiredGranted) {
        if (exitAfterImportedPermissions && permissions.allRequiredGranted) {
            exitAfterImportedPermissions = false
            onExitToDashboard(true)
        }
    }

    fun saveTargetDraft() {
        val wasNew = state.targets.none { it.id == targetDraft.id }
        repository.upsertTarget(targetDraft)
        if (wasNew) {
            trackTargetCreated(context, targetDraft)
        }
        savedTargetId = targetDraft.id
    }

    fun saveProfileDraft() {
        val previousProfile = state.profiles.firstOrNull { it.id == profileDraft.id }
        val reviewed = profileDraft.copy(remoteSafetyReviewedAt = Instant.now().toString())
        repository.upsertProfile(reviewed)
        scheduler.schedule(reviewed)
        trackProfileSaved(context, previousProfile, reviewed)
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

    fun exitToDashboardAfterImportWhenReady() {
        val refreshedPermissions = PermissionStateReader(context).read()
        onRefreshPermissions()
        if (refreshedPermissions.allRequiredGranted) {
            exitAfterImportedPermissions = false
            onExitToDashboard(true)
        } else {
            exitAfterImportedPermissions = true
            goTo(OnboardingStep.Permissions)
        }
    }

    fun runPendingNavigation(action: PendingOnboardingNavigation) {
        when (action) {
            PendingOnboardingNavigation.Back -> {
                val previous = OnboardingSteps.getOrNull(stepIndex - 1)
                if (previous != null) goTo(previous)
            }
            PendingOnboardingNavigation.Skip -> {
                if (state.settings.diagnosticsEnabled == null) {
                    updateDiagnosticsConsent(context, repository, welcomeDiagnosticsEnabled)
                }
                onExitToDashboard(false)
            }
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
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (OnboardingSteps.indexOf(targetState) >= OnboardingSteps.indexOf(initialState)) 1 else -1
                    (
                        slideInHorizontally(animationSpec = tween(240)) { width -> width / 5 * direction } +
                            fadeIn(animationSpec = tween(180))
                        ).togetherWith(
                        slideOutHorizontally(animationSpec = tween(180)) { width -> -width / 7 * direction } +
                            fadeOut(animationSpec = tween(140)),
                    )
                },
                label = "onboarding-step",
            ) { targetStep ->
                when (targetStep) {
                    OnboardingStep.Welcome -> WelcomeStep(
                        state = state,
                        repository = repository,
                        secretStore = secretStore,
                        diagnosticsEnabled = welcomeDiagnosticsEnabled,
                        onDiagnosticsEnabledChange = { welcomeDiagnosticsEnabled = it },
                        onStart = {
                            updateDiagnosticsConsent(context, repository, welcomeDiagnosticsEnabled)
                            (context.applicationContext as? RsyncBackupApplication)?.diagnostics?.trackEvent(
                                "onboarding_started",
                            )
                            goTo(OnboardingStep.Permissions)
                        },
                        onSkip = {
                            updateDiagnosticsConsent(context, repository, welcomeDiagnosticsEnabled)
                            onExitToDashboard(false)
                        },
                        onImportSuccess = { exitToDashboardAfterImportWhenReady() },
                    )
                    OnboardingStep.Permissions -> OnboardingPermissionsStep(
                        permissions = permissions,
                        onRefreshPermissions = onRefreshPermissions,
                        onContinue = {
                            if (exitAfterImportedPermissions) {
                                exitToDashboardAfterImportWhenReady()
                            } else {
                                goTo(OnboardingStep.Tailscale)
                            }
                        },
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
}

@Composable
internal fun WelcomeStep(
    state: AppState,
    repository: AppRepository,
    secretStore: SecretStore,
    diagnosticsEnabled: Boolean,
    onDiagnosticsEnabledChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onImportSuccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Welcome")
        SectionCard {
            Text("Pocket Backup copies selected phone folders to a server you control.")
            Text(
                "Setup will guide you through permissions, optional Tailscale access, one server, and one backup profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticsConsentToggle(
                checked = BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED && diagnosticsEnabled,
                onCheckedChange = onDiagnosticsEnabledChange,
                diagnosticsAvailable = BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED,
                modifier = Modifier.testTag("onboarding-diagnostics-toggle"),
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
        SectionCard {
            ConfigurationImportSection(
                state = state,
                repository = repository,
                secretStore = secretStore,
                onImported = onImportSuccess,
            )
        }
    }
}

@Composable
internal fun DiagnosticsConsentToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    diagnosticsAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = diagnosticsAvailable) { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = diagnosticsAvailable,
                modifier = Modifier.testTag("diagnostics-consent-checkbox"),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 11.dp),
            ) {
                Text(
                    if (diagnosticsAvailable) {
                        "Send diagnostics and error reports"
                    } else {
                        "Diagnostics unavailable in this build"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (diagnosticsAvailable) {
                        "Helps find crashes, failed backups, and setup problems. No backup paths, server addresses, usernames, SSH keys, file names, rsync output, or Wi-Fi names are sent."
                    } else {
                        "This build was compiled without a diagnostics endpoint, so nothing can be sent."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = { openUrlInUserBrowser(context, PRIVACY_POLICY_URL) },
            modifier = Modifier.testTag("diagnostics-privacy-policy-button"),
        ) {
            Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Privacy policy")
        }
    }
}

@Composable
internal fun OnboardingPermissionsStep(
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
            "Approve the required permissions so scheduled backups can read files and keep running in the background. Wi-Fi access is optional.",
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
internal fun OnboardingWrappedScreen(
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
internal fun OnboardingTargetStep(
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
internal fun OnboardingProfileStep(
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
            val previousProfile = state.profiles.firstOrNull { it.id == savedProfile.id }
            repository.upsertProfile(savedProfile)
            scheduler.schedule(savedProfile)
            trackProfileSaved(context, previousProfile, savedProfile)
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
internal fun OnboardingReviewStep(
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
        AnimatedStateBlock(visible = dryRunResult != null) {
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
internal fun SetupRepairCard(checklist: List<SetupChecklistItem>, onOpenStep: () -> Unit) {
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

internal data class SetupChecklistItem(
    val label: String,
    val complete: Boolean,
    val nextAction: String,
    val step: OnboardingStep,
    val detail: String? = null,
)

internal data class DryRunResult(
    val passed: Boolean,
    val message: String,
    val checklist: List<SetupChecklistItem>,
)

internal fun defaultOnboardingProfile(state: AppState, target: TargetRecord, defaultExcludes: String): BackupProfile {
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

internal fun startDryRun(
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

internal fun setupChecklistForProfile(
    profile: BackupProfile,
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    constraintSnapshot: ConstraintSnapshot,
): List<SetupChecklistItem> =
    listOf(setupPermissionChecklistItem(permissions)) +
        profileSetupChecklistForProfile(profile, state, constraintSnapshot)

internal fun setupPermissionChecklistItem(
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
): SetupChecklistItem =
    SetupChecklistItem("Permissions approved", permissions.allRequiredGranted, "Grant permissions", OnboardingStep.Permissions)

internal fun profileSetupChecklistForProfile(
    profile: BackupProfile,
    state: AppState,
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

internal fun firstMissingSetupStep(checklist: List<SetupChecklistItem>): OnboardingStep =
    checklist.firstOrNull { !it.complete }?.step ?: OnboardingStep.Review
