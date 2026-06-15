@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ttv20.rsyncbackup.MainActivity
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TailscaleStateMetadata
import com.ttv20.rsyncbackup.model.effectiveTailscaleNodeName
import com.ttv20.rsyncbackup.model.suggestedTailscaleNodeName
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun TailscaleScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
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
    var browserLoginRequest by remember { mutableStateOf<BrowserLoginRequest?>(null) }
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
    browserLoginRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { browserLoginRequest = null },
            icon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
            title = { Text("Choose browser") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    request.browsers.forEach { browser ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                browserLoginRequest = null
                                startTailscaleBrowserLogin(
                                    context = context,
                                    secretStore = secretStore,
                                    repository = repository,
                                    scope = scope,
                                    requestedNodeName = request.nodeName,
                                    browserPackage = browser.packageName,
                                    setBusy = { busy = it },
                                    setMessage = { message = it },
                                )
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                browser.icon?.let { icon ->
                                    Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } ?: Icon(
                                    Icons.Outlined.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(browser.label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { browserLoginRequest = null }) {
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
            "Optional. Use Tailscale if your server is only reachable through your private Tailscale network.",
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
                        val requestedNodeName = nodeName.trim().ifBlank { defaultNodeName }
                        val defaultBrowserPackage = defaultCustomTabsBrowserPackage(context)
                        val browserChoices = customTabsBrowserChoices(context)
                        when {
                            defaultBrowserPackage != null ->
                                startTailscaleBrowserLogin(
                                    context = context,
                                    secretStore = secretStore,
                                    repository = repository,
                                    scope = scope,
                                    requestedNodeName = requestedNodeName,
                                    browserPackage = defaultBrowserPackage,
                                    setBusy = { busy = it },
                                    setMessage = { message = it },
                                )
                            browserChoices.size > 1 ->
                                browserLoginRequest = BrowserLoginRequest(
                                    nodeName = requestedNodeName,
                                    browsers = browserChoices,
                                )
                            browserChoices.size == 1 ->
                                startTailscaleBrowserLogin(
                                    context = context,
                                    secretStore = secretStore,
                                    repository = repository,
                                    scope = scope,
                                    requestedNodeName = requestedNodeName,
                                    browserPackage = browserChoices.single().packageName,
                                    setBusy = { busy = it },
                                    setMessage = { message = it },
                                )
                            else ->
                                startTailscaleBrowserLogin(
                                    context = context,
                                    secretStore = secretStore,
                                    repository = repository,
                                    scope = scope,
                                    requestedNodeName = requestedNodeName,
                                    browserPackage = null,
                                    setBusy = { busy = it },
                                    setMessage = { message = it },
                                )
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

private fun startTailscaleBrowserLogin(
    context: Context,
    secretStore: SecretStore,
    repository: AppRepository,
    scope: CoroutineScope,
    requestedNodeName: String,
    browserPackage: String?,
    setBusy: (Boolean) -> Unit,
    setMessage: (String) -> Unit,
) {
    setBusy(true)
    setMessage("Waiting for Tailscale login in browser")
    val browserOpened = AtomicBoolean(false)
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            TailscaleManager(context, secretStore).authenticateWithBrowser(
                nodeName = requestedNodeName,
            ) { authUrl ->
                if (browserOpened.compareAndSet(false, true)) {
                    scope.launch {
                        val opened = openUrlInUserBrowser(context, authUrl, browserPackage)
                        setMessage(
                            if (opened) {
                                "Complete Tailscale login in your browser"
                            } else {
                                "Could not open a browser for Tailscale login"
                            },
                        )
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
        setMessage(
            if (result.success) {
                "Connected as $requestedNodeName"
            } else {
                "Browser login failed"
            },
        )
        setBusy(false)
        if (result.success && browserOpened.get()) {
            returnToAppAfterBrowserLogin(context)
        }
    }
}

internal fun openUrlInUserBrowser(context: Context, url: String): Boolean {
    return openUrlInUserBrowser(context, url, browserPackage = null)
}

private fun openUrlInUserBrowser(context: Context, url: String, browserPackage: String?): Boolean {
    val uri = Uri.parse(url)
    val customTabsPackage = browserPackage ?: customTabsBrowserPackage(context)
    Log.i(TAG, "Opening URL with Custom Tabs package: ${customTabsPackage ?: "default"}")
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
        Log.w(TAG, "Custom Tabs launch failed with no activity; falling back to VIEW intent", error)
        openUrlWithViewIntent(context, uri, customTabsPackage)
    } catch (error: IllegalArgumentException) {
        Log.w(TAG, "Custom Tabs launch failed with invalid arguments; falling back to VIEW intent", error)
        openUrlWithViewIntent(context, uri, customTabsPackage)
    } catch (error: RuntimeException) {
        Log.w(TAG, "Custom Tabs launch failed; falling back to VIEW intent", error)
        openUrlWithViewIntent(context, uri, customTabsPackage)
    }
}

internal fun customTabsBrowserPackage(context: Context): String? {
    defaultCustomTabsBrowserPackage(context)?.let { return it }
    customTabsBrowserChoices(context).firstOrNull()?.packageName?.let { return it }
    return CustomTabsClient.getPackageName(context, null)
        ?.takeUnless(::isNonBrowserCustomTabsPackage)
}

internal fun defaultCustomTabsBrowserPackage(context: Context): String? {
    val servicePackages = customTabsServicePackages(context)
        .filterNot(::isNonBrowserCustomTabsPackage)
        .toSet()
    return context.packageManager.resolveDefaultBrowserPackage()
        ?.takeIf { it in servicePackages }
}

internal fun customTabsBrowserChoices(context: Context): List<BrowserChoice> {
    val packageManager = context.packageManager
    val preferredOrder = PreferredCustomTabsPackages
        .withIndex()
        .associate { it.value to it.index }
    return customTabsServicePackages(context)
        .filterNot(::isNonBrowserCustomTabsPackage)
        .distinct()
        .map { packageName ->
            BrowserChoice(
                packageName = packageName,
                label = packageManager.applicationLabel(packageName),
                icon = packageManager.applicationIcon(packageName),
            )
        }
        .sortedWith(
            compareBy<BrowserChoice> { preferredOrder[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label },
        )
}

private fun customTabsServicePackages(context: Context): List<String> {
    val intent = Intent(CUSTOM_TABS_SERVICE_ACTION)
    return context.packageManager.queryCustomTabsServices(intent)
        .mapNotNull { it.serviceInfo?.packageName }
        .distinct()
}

private fun PackageManager.queryCustomTabsServices(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentServices(intent, 0)
    }

private fun PackageManager.resolveDefaultBrowserPackage(): String? {
    val intent = Intent(Intent.ACTION_VIEW, TAILSCALE_LOGIN_BASE_URI)
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    val activityInfo = resolveInfo?.activityInfo ?: return null
    val packageName = activityInfo.packageName ?: return null
    return packageName.takeUnless {
        packageName == "android" ||
            packageName == "com.android.intentresolver" ||
            activityInfo.name.contains("Resolver", ignoreCase = true) ||
            isNonBrowserCustomTabsPackage(packageName)
    }
}

private fun PackageManager.applicationLabel(packageName: String): String =
    runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getApplicationInfo(packageName, 0)
        }
        getApplicationLabel(applicationInfo).toString()
    }.getOrDefault(packageName)

private fun PackageManager.applicationIcon(packageName: String): ImageBitmap? =
    runCatching {
        getApplicationIcon(packageName)
            .toBitmap(width = BROWSER_ICON_SIZE_PX, height = BROWSER_ICON_SIZE_PX)
            .asImageBitmap()
    }.getOrNull()

private fun isNonBrowserCustomTabsPackage(packageName: String): Boolean =
    packageName in NonBrowserCustomTabsPackages || packageName.startsWith("fe.linksheet.")

internal data class BrowserChoice(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

private data class BrowserLoginRequest(
    val nodeName: String,
    val browsers: List<BrowserChoice>,
)

internal fun openUrlWithViewIntent(context: Context, uri: Uri, packageName: String?): Boolean =
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

internal fun returnToAppAfterBrowserLogin(context: Context): Boolean =
    (sendBrowserLoginReturnPendingIntent(context) || startBrowserLoginReturnActivity(context))
        .also { returned ->
            Log.i(TAG, "Tailscale browser login return requested; accepted=$returned")
        }

private fun sendBrowserLoginReturnPendingIntent(context: Context): Boolean =
    runCatching {
        val appContext = context.applicationContext
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            TAILSCALE_BROWSER_LOGIN_RETURN_REQUEST_CODE,
            browserLoginReturnIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            pendingIntentCreatorOptions(),
        )
        pendingIntent.send(appContext, 0, null, null, null, null, pendingIntentSenderOptions())
    }.isSuccess

private fun startBrowserLoginReturnActivity(context: Context): Boolean =
    runCatching {
        val launchContext = context.findActivity() ?: context
        launchContext.startActivity(browserLoginReturnIntent(launchContext))
    }.isSuccess

internal fun browserLoginReturnIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        .putExtra(MainActivity.EXTRA_START_SCREEN, Screen.Tailscale.name)

private fun pendingIntentCreatorOptions(): Bundle? =
    backgroundActivityStartOptions()?.setPendingIntentCreatorBackgroundActivityStartMode(
        browserLoginBackgroundActivityStartMode(),
    )?.toBundle()

private fun pendingIntentSenderOptions(): Bundle? =
    backgroundActivityStartOptions()?.setPendingIntentBackgroundActivityStartMode(
        browserLoginBackgroundActivityStartMode(),
    )?.toBundle()

private fun backgroundActivityStartOptions(): ActivityOptions? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic()
    } else {
        null
    }

@Suppress("DEPRECATION")
private fun browserLoginBackgroundActivityStartMode(): Int =
    if (Build.VERSION.SDK_INT >= 36) {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
    } else {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

internal fun browserLoginFailureMessage(output: String, browserOpened: Boolean): String {
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

internal fun redactUrls(text: String): String =
    UrlRedactionRegex.replace(text, "[redacted-url]")

internal fun friendlyTailscaleError(error: String): String =
    when {
        error.contains("invalid key", ignoreCase = true) ->
            "The auth key was rejected. Generate a new Tailscale auth key and paste it here."
        error.contains("NeedsLogin", ignoreCase = true) ->
            "Tailscale still needs login. Paste a valid auth key and connect again."
        error.contains("failed", ignoreCase = true) ->
            conciseFeedbackMessage(error)
        else -> conciseFeedbackMessage(error)
    }

private const val TAILSCALE_BROWSER_LOGIN_RETURN_REQUEST_CODE = 42120
private const val BROWSER_ICON_SIZE_PX = 96
private const val TAG = "Pocket Backup Tailscale"
private const val CUSTOM_TABS_SERVICE_ACTION = "android.support.customtabs.action.CustomTabsService"
private val TAILSCALE_LOGIN_BASE_URI = Uri.parse("https://login.tailscale.com/")
