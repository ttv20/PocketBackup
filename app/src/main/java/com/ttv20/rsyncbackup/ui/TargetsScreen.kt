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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import java.util.UUID

@Composable
internal fun TargetsScreen(
    state: AppState,
    repository: AppRepository,
    secretStore: SecretStore,
    onDetailActiveChange: (Boolean, (() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val scheduler = remember(context) { BackupScheduler(context) }
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
    val openTargetEditor: (TargetRecord) -> Unit = { target ->
        editorIsDraft = false
        editorTarget = target
        onDetailActiveChange(true, closeEditor)
        compactEditorOpen = true
    }
    SideEffect {
        onDetailActiveChange(compactEditorOpen, if (compactEditorOpen) editorBackHandler ?: closeEditor else null)
    }
    DisposableEffect(Unit) {
        onDispose { onDetailActiveChange(false, null) }
    }
    AnimatedContent(
        targetState = editorTarget,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val opening = targetState != null
            val direction = if (opening) 1 else -1
            (
                slideInHorizontally(animationSpec = tween(220)) { width -> width / 6 * direction } +
                    fadeIn(animationSpec = tween(160))
                ).togetherWith(
                slideOutHorizontally(animationSpec = tween(170)) { width -> -width / 8 * direction } +
                    fadeOut(animationSpec = tween(130)),
            )
        },
        label = "target-editor-swap",
    ) { editingTarget ->
        if (editingTarget != null) {
            val isDraft = editorIsDraft
            val dependentProfiles = state.profiles.filter { it.targetId == editingTarget.id }
            val dependentProfileIds = dependentProfiles.map { it.id }
            TargetEditor(
                state = state,
                target = editingTarget,
                repository = repository,
                secretStore = secretStore,
                onSave = {
                    repository.upsertTarget(it)
                    closeEditor()
                },
                onDelete = if (isDraft) {
                    null
                } else {
                    {
                        if (state.queue.runningProfileId?.let { it in dependentProfileIds } == true) {
                            BackupService.cancel(context)
                        }
                        dependentProfileIds.forEach { scheduler.cancel(it) }
                        dependentProfiles
                            .filter { it.schedule.type != ScheduleType.DISABLED }
                            .forEach { profile ->
                                diagnosticsController(context)?.trackEvent(
                                    "schedule_disabled",
                                    DiagnosticsAttributes.backupIdentity(profile) + mapOf(
                                        DiagnosticsAttributes.SCHEDULE_TYPE to profile.schedule.type.name.lowercase(),
                                    ),
                                )
                            }
                        repository.removeTarget(editingTarget.id)
                        closeEditor()
                    }
                },
                onBack = closeEditor,
                onBackHandlerChange = { editorBackHandler = it },
                isDraft = isDraft,
                cancelLabel = if (isDraft) "Cancel" else "Back",
                deleteWarningText = targetDeleteWarningText(editingTarget, dependentProfiles.size),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                if (state.targets.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            SectionHeader("Targets")
                        }
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Storage,
                                title = "No targets yet",
                                body = "Add a server target before creating backups that sync to SSH storage.",
                                action = "Add target",
                                onAction = addTarget,
                                actionButtonTag = "targets-add-button",
                            )
                        }
                    }
                } else {
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
                                onClick = { openTargetEditor(target) },
                                trailing = {
                                    StatusBadge(if (trusted) "Reachable" else "Needs fingerprint", if (trusted) MetricTone.Success else MetricTone.Warning)
                                },
                            )
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
    }
}

internal fun targetDeleteWarningText(target: TargetRecord, dependentProfileCount: Int): String {
    val targetName = target.name.trim().ifBlank { "this target" }
    return if (dependentProfileCount == 0) {
        "This removes $targetName and its saved SSH trust records. This cannot be undone."
    } else {
        "This removes $targetName, ${profileCountLabel(dependentProfileCount)} that use it, and related schedules. This cannot be undone."
    }
}

internal fun defaultTarget(baseName: String, sequence: Int): TargetRecord =
    TargetRecord(
        id = UUID.randomUUID().toString(),
        name = if (sequence <= 1) baseName else "$baseName $sequence",
        user = "",
        lanHost = "",
        defaultRemotePath = "",
    )
