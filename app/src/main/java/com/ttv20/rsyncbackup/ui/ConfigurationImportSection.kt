@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VpnKey
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.BuildConfig
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.backup.SshRuntimeFiles
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ExportCodec
import com.ttv20.rsyncbackup.model.ExportDocument
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.SshPrivateKeyExportCrypto
import com.ttv20.rsyncbackup.model.SshPrivateKeyExportPayload
import com.ttv20.rsyncbackup.model.TailscaleStateExportCrypto
import com.ttv20.rsyncbackup.model.TailscaleStateExportPayload
import com.ttv20.rsyncbackup.model.toExportDocument
import com.ttv20.rsyncbackup.model.withImportedConfiguration
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

internal data class ConfigurationImportResult(
    val state: AppState,
    val message: String,
)

internal fun ExportDocument.protectedDataLabels(): List<String> =
    buildList {
        if (sshPrivateKey != null) add("SSH private key")
        if (tailscaleState != null) add("Tailscale connection")
    }

internal fun List<String>.humanReadableList(): String =
    when (size) {
        0 -> ""
        1 -> single()
        2 -> "${this[0]} and ${this[1]}"
        else -> dropLast(1).joinToString(", ") + ", and " + last()
    }

internal fun invalidConfigurationImportMessage(source: SettingsImportSource): String =
    when (source) {
        SettingsImportSource.Paste -> "Clipboard does not contain a Pocket Backup configuration export."
        SettingsImportSource.File -> "Selected file is not a Pocket Backup configuration export."
    }

internal fun configurationExportText(
    state: AppState,
    secretStore: SecretStore,
    protectedDataPassword: String? = null,
): String {
    val encryptedPrivateKey = protectedDataPassword?.let { password ->
        val keySettings = state.sshKeySettings
        val privateKeyAlias = keySettings.privateKeySecretAlias ?: return@let null
        val privateKeyBytes = secretStore.get(privateKeyAlias)
            ?: error("SSH private key is missing from secure storage")
        val passphrase = keySettings.passphraseSecretAlias?.let { alias ->
            secretStore.get(alias)?.toString(Charsets.UTF_8)
                ?: error("SSH private key passphrase is missing from secure storage")
        }
        SshPrivateKeyExportCrypto.encrypt(
            payload = SshPrivateKeyExportPayload(
                publicKey = keySettings.publicKey,
                privateKeyPem = SshRuntimeFiles.privateKeyText(privateKeyBytes),
                passphrase = passphrase,
            ),
            password = password,
        )
    }
    val encryptedTailscaleState = protectedDataPassword?.let { password ->
        val stateAlias = state.tailscale.stateSecretAlias ?: return@let null
        val stateArchive = secretStore.get(stateAlias)
            ?: error("Tailscale connection is missing from secure storage")
        TailscaleStateExportCrypto.encrypt(
            payload = TailscaleStateExportPayload.fromStateArchive(stateArchive),
            password = password,
        )
    }
    return ExportCodec.encode(
        state.toExportDocument(
            sshPrivateKey = encryptedPrivateKey,
            tailscaleState = encryptedTailscaleState,
        ),
    )
}

internal fun importedConfigurationState(
    currentState: AppState,
    document: ExportDocument,
    secretStore: SecretStore,
    protectedDataPassword: String,
): ConfigurationImportResult {
    val importedState = currentState.withImportedConfiguration(document)
    val encryptedPrivateKey = document.sshPrivateKey
    val encryptedTailscaleState = document.tailscaleState

    if (encryptedPrivateKey == null && encryptedTailscaleState == null) {
        return ConfigurationImportResult(importedState, "Configuration imported")
    }

    if (protectedDataPassword.isBlank()) {
        return ConfigurationImportResult(
            state = importedState,
            message = "Configuration imported without protected data",
        )
    }

    val privateKeyPayload = encryptedPrivateKey?.let {
        SshPrivateKeyExportCrypto.decrypt(it, protectedDataPassword)
    }
    val tailscalePayload = encryptedTailscaleState?.let {
        TailscaleStateExportCrypto.decrypt(it, protectedDataPassword)
    }
    val publicKey = privateKeyPayload?.publicKey ?: document.sshPublicKey
    if (privateKeyPayload != null) {
        require(!publicKey.isNullOrBlank()) { "Private key export is missing its public key" }
    }

    privateKeyPayload?.let { payload ->
        secretStore.put(IMPORTED_SSH_PRIVATE_KEY_ALIAS, payload.privateKeyPem.toByteArray(Charsets.UTF_8))
        if (payload.passphrase.isNullOrBlank()) {
            secretStore.delete(IMPORTED_SSH_PASSPHRASE_ALIAS)
        } else {
            secretStore.put(IMPORTED_SSH_PASSPHRASE_ALIAS, payload.passphrase.toByteArray(Charsets.UTF_8))
        }
    }
    tailscalePayload?.let { payload ->
        secretStore.put(IMPORTED_TAILSCALE_STATE_ALIAS, payload.stateArchiveBytes())
    }

    val restoredState = importedState.copy(
        sshKeySettings = privateKeyPayload?.let { payload ->
            GlobalSshKeySettings(
                publicKey = publicKey,
                privateKeySecretAlias = IMPORTED_SSH_PRIVATE_KEY_ALIAS,
                customPrivateKeyLabel = "Imported key",
                passphraseSecretAlias = IMPORTED_SSH_PASSPHRASE_ALIAS.takeIf { !payload.passphrase.isNullOrBlank() },
                generatedAt = Instant.now().toString(),
            )
        } ?: importedState.sshKeySettings,
        tailscale = tailscalePayload?.let {
            importedState.tailscale.copy(
                isConfigured = true,
                stateSecretAlias = IMPORTED_TAILSCALE_STATE_ALIAS,
                lastLoginAt = document.tailscale.lastLoginAt ?: Instant.now().toString(),
                lastReachabilityTestAt = document.tailscale.lastReachabilityTestAt,
                lastError = null,
            )
        } ?: importedState.tailscale,
    )
    val restoredLabels = buildList {
        if (privateKeyPayload != null) add("private key")
        if (tailscalePayload != null) add("Tailscale connection")
    }
    val message = if (restoredLabels.size == 1) {
        "Configuration and ${restoredLabels.single()} imported"
    } else {
        "Configuration, ${restoredLabels.humanReadableList()} imported"
    }
    return ConfigurationImportResult(
        state = restoredState,
        message = message,
    )
}

internal fun updateDiagnosticsConsent(
    context: Context,
    repository: AppRepository,
    enabled: Boolean,
) {
    val resolvedEnabled = enabled && BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED
    repository.update { state ->
        state.copy(settings = state.settings.copy(diagnosticsEnabled = resolvedEnabled))
    }
    (context.applicationContext as? RsyncBackupApplication)?.diagnostics?.updateConsent(resolvedEnabled)
}

internal fun diagnosticsController(context: Context) =
    (context.applicationContext as? RsyncBackupApplication)?.diagnostics

internal fun trackTargetCreated(context: Context, target: TargetRecord) {
    diagnosticsController(context)?.trackEvent(
        "target_created",
        mapOf(DiagnosticsAttributes.TARGET_ID to target.id),
    )
}

internal fun trackProfileSaved(context: Context, previousProfile: BackupProfile?, savedProfile: BackupProfile) {
    val diagnostics = diagnosticsController(context) ?: return
    val attributes = DiagnosticsAttributes.backupIdentity(savedProfile) + mapOf(
        DiagnosticsAttributes.SCHEDULE_TYPE to savedProfile.schedule.type.name.lowercase(),
    )
    if (previousProfile == null) {
        diagnostics.trackEvent("profile_created", attributes)
    }

    val previousScheduleType = previousProfile?.schedule?.type ?: ScheduleType.DISABLED
    val nextScheduleType = savedProfile.schedule.type
    val scheduleChanged = previousProfile?.schedule != savedProfile.schedule
    when {
        previousScheduleType == ScheduleType.DISABLED && nextScheduleType != ScheduleType.DISABLED -> {
            diagnostics.trackEvent("schedule_created", attributes)
        }
        previousScheduleType != ScheduleType.DISABLED && nextScheduleType == ScheduleType.DISABLED -> {
            diagnostics.trackEvent("schedule_disabled", attributes)
        }
        previousScheduleType != ScheduleType.DISABLED && nextScheduleType != ScheduleType.DISABLED && scheduleChanged -> {
            diagnostics.trackEvent("schedule_updated", attributes)
        }
    }
}

internal fun configurationCounts(state: AppState): Map<String, Any?> =
    mapOf(
        DiagnosticsAttributes.PROFILE_COUNT to state.profiles.size,
        DiagnosticsAttributes.TARGET_COUNT to state.targets.size,
    )

internal fun configurationCounts(document: ExportDocument): Map<String, Any?> =
    mapOf(
        DiagnosticsAttributes.PROFILE_COUNT to document.profiles.size,
        DiagnosticsAttributes.TARGET_COUNT to document.targets.size,
    )

internal fun Throwable.diagnosticsFailureCategory(): String =
    when (this) {
        is kotlinx.serialization.SerializationException -> "invalid_json"
        is IllegalArgumentException -> "invalid_input"
        is IllegalStateException -> "invalid_state"
        is SecurityException -> "permission_denied"
        else -> "unexpected_error"
    }

internal fun trackPermissionPromptOpened(context: Context, permissionType: String) {
    diagnosticsController(context)?.trackEvent(
        "permission_prompt_opened",
        mapOf(DiagnosticsAttributes.PERMISSION_TYPE to permissionType),
    )
}

internal fun trackPermissionResult(context: Context, permissionType: String, granted: Boolean) {
    diagnosticsController(context)?.trackEvent(
        if (granted) "permission_granted" else "permission_denied",
        mapOf(
            DiagnosticsAttributes.PERMISSION_TYPE to permissionType,
            DiagnosticsAttributes.RESULT to if (granted) "granted" else "denied",
        ),
    )
}

@Composable
internal fun ConfigurationImportSection(
    state: AppState,
    repository: AppRepository,
    secretStore: SecretStore,
    onImported: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var pendingImportDocument by remember { mutableStateOf<ExportDocument?>(null) }
    var protectedImportPassword by remember { mutableStateOf("") }
    var protectedImportError by remember { mutableStateOf<String?>(null) }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }

    fun importConfigurationDocument(
        document: ExportDocument,
        protectedDataPassword: String,
        keepDialogOnFailure: Boolean,
    ) {
        importBusy = true
        importError = null
        importMessage = null
        protectedImportError = null
        diagnosticsController(context)?.trackEvent("import_started")
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    importedConfigurationState(
                        currentState = state,
                        document = document,
                        secretStore = secretStore,
                        protectedDataPassword = protectedDataPassword,
                    )
                }
            }
            result.onSuccess { importResult ->
                repository.update { importResult.state }
                diagnosticsController(context)?.trackEvent(
                    "import_finished",
                    configurationCounts(document),
                )
                protectedImportPassword = ""
                protectedImportError = null
                pendingImportDocument = null
                importError = null
                importMessage = importResult.message
                onImported()
            }.onFailure {
                (context.applicationContext as? RsyncBackupApplication)?.diagnostics?.trackHandledException(
                    it,
                    mapOf(DiagnosticsAttributes.SOURCE to "settings_import"),
                )
                diagnosticsController(context)?.trackEvent(
                    "import_failed",
                    mapOf(DiagnosticsAttributes.FAILURE_CATEGORY to it.diagnosticsFailureCategory()),
                )
                importMessage = null
                if (keepDialogOnFailure) {
                    protectedImportError = it.message ?: "Configuration import failed"
                } else {
                    pendingImportDocument = null
                    importError = it.message ?: "Configuration import failed"
                }
            }
            importBusy = false
        }
    }

    fun requestImport(text: String, source: SettingsImportSource) {
        importError = null
        importMessage = null
        protectedImportError = null
        pendingImportDocument = null
        val document = runCatching { ExportCodec.decode(text) }
            .onFailure {
                diagnosticsController(context)?.trackEvent(
                    "import_failed",
                    mapOf(DiagnosticsAttributes.FAILURE_CATEGORY to it.diagnosticsFailureCategory()),
                )
                importError = invalidConfigurationImportMessage(source)
            }
            .getOrNull()
            ?: return

        if (document.protectedDataLabels().isNotEmpty()) {
            pendingImportDocument = document
            protectedImportPassword = ""
            protectedImportError = null
        } else {
            importConfigurationDocument(
                document = document,
                protectedDataPassword = "",
                keepDialogOnFailure = false,
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not open import file")
        }.onSuccess { text ->
            protectedImportPassword = ""
            protectedImportError = null
            pendingImportDocument = null
            requestImport(text, SettingsImportSource.File)
        }.onFailure {
            importMessage = null
            importError = it.message ?: "Configuration file read failed"
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Import", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    requestImport(
                        text = clipboard.getText()?.text.orEmpty(),
                        source = SettingsImportSource.Paste,
                    )
                },
                enabled = !importBusy,
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Paste")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                enabled = !importBusy,
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose file")
            }
        }
        importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        importError?.let { ErrorText(it) }
    }

    pendingImportDocument?.let { document ->
        val protectedData = document.protectedDataLabels().humanReadableList()
        AlertDialog(
            onDismissRequest = {
                if (!importBusy) {
                    pendingImportDocument = null
                    protectedImportPassword = ""
                    protectedImportError = null
                }
            },
            icon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
            title = { Text("Restore protected data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This export has encrypted $protectedData. Enter the password to restore it, or import without protected data.",
                    )
                    OutlinedTextField(
                        protectedImportPassword,
                        { protectedImportPassword = it },
                        label = { Text("Export password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    protectedImportError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !importBusy && protectedImportPassword.isNotBlank(),
                    onClick = {
                        importConfigurationDocument(
                            document = document,
                            protectedDataPassword = protectedImportPassword,
                            keepDialogOnFailure = true,
                        )
                    },
                ) {
                    Text(if (importBusy) "Importing" else "Restore protected data")
                }
            },
            dismissButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !importBusy,
                        onClick = {
                            importConfigurationDocument(
                                document = document,
                                protectedDataPassword = "",
                                keepDialogOnFailure = false,
                            )
                        },
                    ) {
                        Text("Without protected data")
                    }
                    TextButton(
                        enabled = !importBusy,
                        onClick = {
                            pendingImportDocument = null
                            protectedImportPassword = ""
                            protectedImportError = null
                        },
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

internal fun com.ttv20.rsyncbackup.permissions.AppPermissionState.isPermissionGranted(permissionType: String): Boolean =
    when (permissionType) {
        PERMISSION_ALL_FILES_ACCESS -> allFilesAccess
        PERMISSION_BATTERY_OPTIMIZATION -> batteryOptimizationExempt
        PERMISSION_EXACT_ALARM -> exactAlarmAccess
        PERMISSION_NOTIFICATIONS -> notifications
        PERMISSION_WIFI_STATE -> wifiStateAccess
        else -> false
    }
