@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.resolvedSshKeySettings
import com.ttv20.rsyncbackup.ssh.SshKeyManager
import com.ttv20.rsyncbackup.ssh.TargetConnectResult
import com.ttv20.rsyncbackup.ssh.TargetConnectionSetup
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
internal fun TargetEditor(
    state: AppState,
    target: TargetRecord,
    repository: AppRepository,
    secretStore: SecretStore,
    onSave: (TargetRecord) -> Unit,
    onDelete: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onBackHandlerChange: ((() -> Unit)?) -> Unit,
    isDraft: Boolean,
    cancelLabel: String = "Back",
    deleteWarningText: String = "This target will be removed. This cannot be undone.",
    showEditorHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember(target.id, target) { mutableStateOf(target) }
    var showUnsavedPrompt by rememberSaveable(target.id) { mutableStateOf(false) }
    var portText by rememberSaveable(target.id) { mutableStateOf(target.port.toString()) }
    var showDeletePrompt by rememberSaveable(target.id) { mutableStateOf(false) }
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
        val wasNew = isDraft && state.targets.none { it.id == connectedTarget.id }
        repository.update { appState ->
            appState.copy(
                targets = appState.targets.filterNot { it.id == connectedTarget.id } + connectedTarget,
                trustedHostFingerprints = trustedHostFingerprints,
            )
        }
        if (wasNew) {
            trackTargetCreated(context, connectedTarget)
        }
        onSave(connectedTarget)
    }

    fun connectAndSave() {
        val targetToConnect = normalizedTargetForConnect()
        val keySettings = targetToConnect.resolvedSshKeySettings(state.sshKeySettings)
        val connectionSetup = TargetConnectionSetup(context, secretStore)
        val preferredRoute = connectionSetup.preferredRoute(targetToConnect)?.route
        val connectAttributes = mapOf(
            DiagnosticsAttributes.TARGET_ID to targetToConnect.id,
            DiagnosticsAttributes.ROUTE_USED to preferredRoute?.name?.lowercase(),
        )
        editing = targetToConnect
        connectBusy = true
        connectMessage = "Connecting to the server..."
        connectError = null
        setupError = null
        diagnosticsController(context)?.trackEvent("connectivity_test_started", connectAttributes)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                connectionSetup.connect(
                    state = state,
                    target = targetToConnect,
                    sshKeySettings = keySettings,
                )
            }
            when (result) {
                is TargetConnectResult.Authorized -> {
                    val attributes = mapOf(
                        DiagnosticsAttributes.TARGET_ID to targetToConnect.id,
                        DiagnosticsAttributes.ROUTE_USED to result.route.route.name.lowercase(),
                        DiagnosticsAttributes.RESULT to "authorized",
                    )
                    diagnosticsController(context)?.trackEvent("connectivity_test_finished", attributes)
                    diagnosticsController(context)?.trackEvent("host_key_confirmed", attributes)
                    connectMessage = result.message
                    saveConnectedTarget(result.target, result.trustedHostFingerprints)
                }
                is TargetConnectResult.NeedsPassword -> {
                    diagnosticsController(context)?.trackEvent(
                        "connectivity_test_finished",
                        mapOf(
                            DiagnosticsAttributes.TARGET_ID to targetToConnect.id,
                            DiagnosticsAttributes.ROUTE_USED to result.route.route.name.lowercase(),
                            DiagnosticsAttributes.RESULT to "needs_password",
                        ),
                    )
                    pendingPasswordSetup = result
                    connectMessage = null
                }
                is TargetConnectResult.Failed -> {
                    diagnosticsController(context)?.trackEvent(
                        "connectivity_test_finished",
                        connectAttributes + mapOf(
                            DiagnosticsAttributes.RESULT to "failed",
                            DiagnosticsAttributes.FAILURE_CATEGORY to result.failureCategory,
                        ),
                    )
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

    if (showDeletePrompt) {
        DeleteConfirmationDialog(
            title = "Delete target?",
            message = deleteWarningText,
            confirmLabel = "Delete",
            onConfirm = {
                showDeletePrompt = false
                onDelete?.invoke()
            },
            onDismiss = { showDeletePrompt = false },
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
                        "Server fingerprint, for manual checking:\n${pending.fingerprintText}",
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
                                    diagnosticsController(context)?.trackEvent(
                                        "host_key_confirmed",
                                        mapOf(
                                            DiagnosticsAttributes.TARGET_ID to updatedTarget.id,
                                            DiagnosticsAttributes.ROUTE_USED to pending.route.route.name.lowercase(),
                                            DiagnosticsAttributes.RESULT to "authorized_after_password",
                                        ),
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
                onSecondaryAction = onDelete?.let { { showDeletePrompt = true } },
                secondaryActionLabel = "Delete".takeIf { onDelete != null },
                secondaryActionIcon = Icons.Outlined.Delete.takeIf { onDelete != null },
                secondaryActionEnabled = !connectBusy,
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
                    "Connect checks the server, trusts its fingerprint, and sets up the app key if the server asks for a password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedSshKeySettings.privateKeySecretAlias == null || selectedSshKeySettings.publicKey == null) {
                    FeedbackBanner("SSH key unavailable", "Open Settings and configure an SSH key before connecting.", MetricTone.Warning)
                }
                AnimatedStateBlock(visible = connectMessage != null) {
                    connectMessage?.let {
                        FeedbackBanner("Target setup", it, MetricTone.Route)
                    }
                }
                AnimatedStateBlock(visible = connectError != null) {
                    connectError?.let {
                        FeedbackBanner("Connection failed", it, MetricTone.Destructive)
                    }
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
                    AnimatedStateBlock(visible = customKeyMessage != null) {
                        customKeyMessage?.let { FeedbackBanner("SSH key updated", it, MetricTone.Success) }
                    }
                    AnimatedStateBlock(visible = customKeyError != null) {
                        customKeyError?.let { FeedbackBanner("SSH key import failed", it, MetricTone.Destructive) }
                    }
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
