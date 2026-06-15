@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.R
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.requiresLan
import com.ttv20.rsyncbackup.model.requiresTailscale
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScaffold(
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name))
                    }
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
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (
                            slideInHorizontally(animationSpec = tween(220)) { width -> width / 8 * direction } +
                                fadeIn(animationSpec = tween(180))
                            ).togetherWith(
                            slideOutHorizontally(animationSpec = tween(180)) { width -> -width / 10 * direction } +
                                fadeOut(animationSpec = tween(140)),
                        )
                    },
                    label = "screen-content",
                ) { targetScreen ->
                    when (targetScreen) {
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
                            secretStore,
                            onRefreshPermissions,
                            onSelect,
                            onStartOnboarding,
                        )
                    }
                }
            }
        }
    }
}

internal data class RunRequest(val context: Context, val profileId: String)

internal data class RunProgressSummary(
    val message: String,
    val metrics: List<Pair<String, String>>,
    val fileLine: String?,
    val transferPercent: Int?,
    val spinnerOnly: Boolean = false,
)

internal fun profileCountLabel(count: Int): String =
    "$count ${if (count == 1) "profile" else "profiles"}"

internal fun targetCountLabel(count: Int): String =
    "$count ${if (count == 1) "target" else "targets"}"

internal fun defaultTargetModeFor(target: TargetRecord, preferred: TargetMode? = null): TargetMode {
    if (preferred != null && preferred.unavailableReason(target) == null) return preferred
    return when {
        target.lanHost.isNotBlank() && !target.tailscaleHost.isNullOrBlank() -> TargetMode.LAN_FIRST_TAILSCALE_FALLBACK
        target.lanHost.isNotBlank() -> TargetMode.LAN_ONLY
        !target.tailscaleHost.isNullOrBlank() -> TargetMode.TAILSCALE_ONLY
        else -> preferred ?: TargetMode.LAN_ONLY
    }
}

internal fun TargetMode.unavailableReason(target: TargetRecord): String? {
    val needsLan = requiresLan() && target.lanHost.isBlank()
    val needsTailscale = requiresTailscale() && target.tailscaleHost.isNullOrBlank()
    return when {
        needsLan && needsTailscale -> "This mode needs a server address and Tailscale device."
        needsLan -> "This mode needs a server address."
        needsTailscale -> "This mode needs a Tailscale device."
        else -> null
    }
}

internal fun unavailableTargetModeMessage(target: TargetRecord): String? {
    val missing = listOfNotNull(
        "Server-address modes are disabled because this target has no server address.".takeIf { target.lanHost.isBlank() },
        "Tailscale modes are disabled because this target has no Tailscale device.".takeIf { target.tailscaleHost.isNullOrBlank() },
    )
    return missing.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

@Composable
internal fun PhoneBottomNavigation(selected: Screen, onSelect: (Screen) -> Unit) {
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
