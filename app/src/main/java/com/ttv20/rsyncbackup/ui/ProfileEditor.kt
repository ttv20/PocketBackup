@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.backup.AndroidWifiNetworkReader
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ProfileValidator
import com.ttv20.rsyncbackup.model.RemoteSafetySettings
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.model.routeOrder
import com.ttv20.rsyncbackup.storage.SecretStore
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun ProfileEditor(
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
    var showUnsavedPrompt by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showDeletePrompt by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showExcludesDialog by rememberSaveable(profile.id) { mutableStateOf(false) }
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
    val requestDelete = {
        if (isDraft) {
            onDelete?.invoke()
        } else {
            showDeletePrompt = true
        }
    }
    val saveProfile = {
        val sanitized = editing.copy(
            remoteSafety = RemoteSafetySettings(),
            remoteSafetyReviewedAt = Instant.now().toString(),
        )
        onSave(sanitized)
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

    if (showDeletePrompt) {
        val profileName = editing.name.trim().ifBlank { "this profile" }
        DeleteConfirmationDialog(
            title = "Delete profile?",
            message = "This removes $profileName and cancels its scheduled backup jobs. Backup logs are kept.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeletePrompt = false
                onDelete?.invoke()
            },
            onDismiss = { showDeletePrompt = false },
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
                onSecondaryAction = onDelete?.let { { requestDelete() } },
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
            SectionCard {
                Text("Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose what to back up from this phone.",
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
                AnimatedStateBlock(visible = sourcePickerError != null) {
                    sourcePickerError?.let { ErrorText(it) }
                }
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
                ToggleDetailRow(
                    title = "Run dry-run estimate first",
                    detail = "Estimates transfer bytes before backup so progress can be shown.",
                    checked = editing.dryRunBeforeBackup,
                    onChange = { editing = editing.copy(dryRunBeforeBackup = it) },
                )
                WarningRow("Mirror mode: delete server files missing from phone", "Only use this with a dedicated backup folder. It makes the server match the phone source.", editing.deleteEnabled) {
                    editing = editing.copy(deleteEnabled = it)
                }
                ExcludesSummaryRow(
                    value = editing.excludes,
                    onOpen = { showExcludesDialog = true },
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

    if (showExcludesDialog) {
        ExcludesEditorDialog(
            value = editing.excludes,
            onSave = {
                editing = editing.copy(excludes = it)
                showExcludesDialog = false
            },
            onCancel = { showExcludesDialog = false },
        )
    }
}
