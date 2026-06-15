@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.MainActivity
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TailscaleStateMetadata
import com.ttv20.rsyncbackup.model.effectiveTailscaleNodeName
import com.ttv20.rsyncbackup.model.suggestedTailscaleNodeName
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
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
internal fun openUrlInUserBrowser(context: Context, url: String): Boolean {
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

internal fun customTabsBrowserPackage(context: Context): String? =
    CustomTabsClient.getPackageName(context, PreferredCustomTabsPackages)
        ?: CustomTabsClient.getPackageName(context, null)
            ?.takeUnless { it in NonBrowserCustomTabsPackages || it.startsWith("fe.linksheet.") }

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
