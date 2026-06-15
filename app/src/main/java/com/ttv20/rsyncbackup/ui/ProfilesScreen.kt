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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
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
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ProfileValidator
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.Severity
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import java.util.UUID

@Composable
internal fun ProfilesScreen(
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
        trackTargetCreated(context, target)
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
    val openProfileEditor: (BackupProfile) -> Unit = { profile ->
        editorIsDraft = false
        editorProfile = profile
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
        targetState = editorProfile,
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
        label = "profile-editor-swap",
    ) { editingProfile ->
        if (editingProfile != null) {
            val isDraft = editorIsDraft
            ProfileEditor(
                state = state,
                profile = editingProfile,
                onSave = { savedProfile ->
                    val previousProfile = state.profiles.firstOrNull { it.id == savedProfile.id }
                    repository.upsertProfile(savedProfile)
                    scheduler.schedule(savedProfile)
                    trackProfileSaved(context, previousProfile, savedProfile)
                    closeEditor()
                },
                onDelete = {
                    if (!isDraft) {
                        if (state.queue.runningProfileId == editingProfile.id) {
                            BackupService.cancel(context)
                        }
                        scheduler.cancel(editingProfile.id)
                        if (editingProfile.schedule.type != ScheduleType.DISABLED) {
                            diagnosticsController(context)?.trackEvent(
                                "schedule_disabled",
                                DiagnosticsAttributes.backupIdentity(editingProfile) + mapOf(
                                    DiagnosticsAttributes.SCHEDULE_TYPE to editingProfile.schedule.type.name.lowercase(),
                                ),
                            )
                        }
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
                if (state.profiles.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            SectionHeader("Profiles")
                        }
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Folder,
                                title = "No profiles yet",
                                body = "Add a backup profile to choose a phone folder, target, schedule, and sync rules.",
                                action = "Add profile",
                                onAction = addProfile,
                                actionButtonTag = "profiles-add-button",
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
                            SectionHeader("Profiles")
                        }
                        items(state.profiles, key = { it.id }) { profile ->
                            val target = state.targets.firstOrNull { it.id == profile.targetId }
                            val issues = ProfileValidator.validate(profile, state)
                            val isRunningProfile = state.queue.runningProfileId == profile.id
                            val liveProgress = state.runProgress.takeIf { it.profileId == profile.id && isRunningProfile }
                            ProfileListRow(
                                profile = profile,
                                target = target,
                                issues = issues,
                                showRunStatus = isRunningProfile,
                                liveProgress = liveProgress,
                                isRunning = isRunningProfile,
                                onClick = { openProfileEditor(profile) },
                                trailing = {
                                    RunStopButton(
                                        isRunning = isRunningProfile,
                                        onClick = {
                                            if (isRunningProfile) {
                                                BackupService.cancel(context)
                                            } else {
                                                BackupService.start(context, profile.id)
                                                onOpenDashboard()
                                            }
                                        },
                                        enabled = isRunningProfile || issues.none { it.severity == Severity.ERROR },
                                    )
                                },
                                trailingSupporting = if (isRunningProfile) {
                                    { ProfileStatusBadge(profile, liveProgress) }
                                } else {
                                    null
                                },
                            )
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
    }
}
