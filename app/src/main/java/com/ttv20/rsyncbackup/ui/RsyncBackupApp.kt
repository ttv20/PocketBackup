@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ttv20.rsyncbackup.R
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.permissions.PermissionStateReader
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import java.time.Instant
import java.util.Locale

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
        (context.applicationContext as? RsyncBackupApplication)?.diagnostics?.trackEvent(
            if (completed) "onboarding_completed" else "onboarding_skipped",
        )
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
