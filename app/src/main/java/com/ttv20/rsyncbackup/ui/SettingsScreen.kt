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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.BuildConfig
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.backup.BinaryPaths
import com.ttv20.rsyncbackup.backup.RsyncCommandBuilder
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.ExportCodec
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.ThemePreference
import com.ttv20.rsyncbackup.model.toExportDocument
import com.ttv20.rsyncbackup.model.withUpdatedSettings
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(
    state: AppState,
    permissions: com.ttv20.rsyncbackup.permissions.AppPermissionState,
    repository: AppRepository,
    secretStore: SecretStore,
    onRefreshPermissions: () -> Unit,
    onSelectScreen: (Screen) -> Unit,
    onStartOnboarding: (OnboardingStep) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var settings by remember(state.settings) { mutableStateOf(state.settings) }
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var exportError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportAction by remember { mutableStateOf<SettingsExportAction?>(null) }
    var pendingSaveExportText by remember { mutableStateOf<String?>(null) }
    var privateKeyExportPassword by remember { mutableStateOf("") }
    var privateKeyExportPasswordConfirmation by remember { mutableStateOf("") }
    var privateKeyExportError by remember { mutableStateOf<String?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    val hasExportablePrivateKey = state.sshKeySettings.privateKeySecretAlias != null
    val hasExportableTailscaleState = state.tailscale.stateSecretAlias != null
    val hasExportableProtectedData = hasExportablePrivateKey || hasExportableTailscaleState
    val exportText = remember(state) { ExportCodec.encode(state.toExportDocument()) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            pendingSaveExportText = null
            return@rememberLauncherForActivityResult
        }
        val text = pendingSaveExportText ?: exportText
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open export file")
        }.onSuccess {
            diagnosticsController(context)?.trackEvent("export_finished", configurationCounts(state))
            exportMessage = "Configuration saved"
            exportError = null
        }.onFailure {
            diagnosticsController(context)?.trackEvent(
                "export_failed",
                configurationCounts(state) + mapOf(DiagnosticsAttributes.FAILURE_CATEGORY to it.diagnosticsFailureCategory()),
            )
            exportMessage = null
            exportError = it.message ?: "Configuration save failed"
        }
        pendingSaveExportText = null
    }

    fun resetPrivateKeyExportDialog() {
        pendingExportAction = null
        privateKeyExportPassword = ""
        privateKeyExportPasswordConfirmation = ""
        privateKeyExportError = null
        exportBusy = false
    }

    fun completeExport(action: SettingsExportAction, text: String, message: String) {
        when (action) {
            SettingsExportAction.Copy -> {
                clipboard.setText(AnnotatedString(text))
                diagnosticsController(context)?.trackEvent("export_finished", configurationCounts(state))
                exportMessage = message
            }
            SettingsExportAction.Save -> {
                pendingSaveExportText = text
                exportLauncher.launch("pocket-backup-config.json")
            }
        }
        exportError = null
    }

    fun exportConfiguration(action: SettingsExportAction, protectedDataPassword: String? = null) {
        exportBusy = true
        exportError = null
        privateKeyExportError = null
        diagnosticsController(context)?.trackEvent("export_started", configurationCounts(state))
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    configurationExportText(
                        state = state,
                        secretStore = secretStore,
                        protectedDataPassword = protectedDataPassword,
                    )
                }
            }
            result.onSuccess { text ->
                val includesProtectedData = protectedDataPassword != null
                completeExport(
                    action = action,
                    text = text,
                    message = if (includesProtectedData) {
                        "Configuration copied with password-protected data"
                    } else {
                        "Configuration copied"
                    },
                )
                resetPrivateKeyExportDialog()
            }.onFailure {
                (context.applicationContext as? RsyncBackupApplication)?.diagnostics?.trackHandledException(
                    it,
                    mapOf(DiagnosticsAttributes.SOURCE to "settings_export"),
                )
                val message = it.message ?: "Configuration export failed"
                diagnosticsController(context)?.trackEvent(
                    "export_failed",
                    configurationCounts(state) + mapOf(DiagnosticsAttributes.FAILURE_CATEGORY to it.diagnosticsFailureCategory()),
                )
                if (pendingExportAction == null) {
                    exportError = message
                } else {
                    privateKeyExportError = message
                }
                exportBusy = false
            }
        }
    }

    fun requestExport(action: SettingsExportAction) {
        if (hasExportableProtectedData) {
            pendingExportAction = action
            privateKeyExportPassword = ""
            privateKeyExportPasswordConfirmation = ""
            privateKeyExportError = null
        } else {
            exportConfiguration(action)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Settings and import/export")
        SectionCard {
            Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SettingsToolRow("SSH Access", "Generate, copy, import, or delete the app key", Icons.Outlined.Key) {
                onSelectScreen(Screen.SshKeys)
            }
            SettingsToolRow("Tailscale", "Connect, test routes, and reset state", Icons.Outlined.Cloud) {
                onSelectScreen(Screen.Tailscale)
            }
            SettingsToolRow(
                label = "Run setup guide",
                detail = "Open onboarding from the beginning",
                icon = Icons.Outlined.CheckCircle,
                testTag = "settings-run-setup-guide",
            ) {
                onStartOnboarding(OnboardingStep.Welcome)
            }
        }
        PermissionSettingsSection(permissions, onRefreshPermissions)
        SectionCard {
            Text("Diagnostics and error reporting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DiagnosticsConsentToggle(
                checked = BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED && state.settings.diagnosticsEnabled == true,
                onCheckedChange = { enabled ->
                    updateDiagnosticsConsent(context, repository, enabled)
                },
                diagnosticsAvailable = BuildConfig.DIAGNOSTICS_BACKEND_CONFIGURED,
                modifier = Modifier.testTag("settings-diagnostics-toggle"),
            )
        }
        SectionCard {
            ThemePreferenceSelector(settings.themePreference) { preference ->
                val updated = settings.copy(themePreference = preference)
                settings = updated
                repository.update { it.withUpdatedSettings(updated) }
            }
            OutlinedTextField(settings.phoneHostname, { settings = settings.copy(phoneHostname = it) }, label = { Text("Phone hostname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.logRetentionLimit.toString(), { settings = settings.copy(logRetentionLimit = it.toIntOrNull() ?: settings.logRetentionLimit) }, label = { Text("Log retention") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { repository.update { it.withUpdatedSettings(settings) } }) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
        SectionCard {
            Text("Export", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { requestExport(SettingsExportAction.Copy) },
                    modifier = Modifier.testTag("settings-export-copy-button"),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = { requestExport(SettingsExportAction.Save) },
                    modifier = Modifier.testTag("settings-export-save-button"),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
            exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            exportError?.let { ErrorText(it) }
        }
        SectionCard {
            ConfigurationImportSection(
                state = state,
                repository = repository,
                secretStore = secretStore,
                onImported = { onSelectScreen(Screen.Dashboard) },
            )
        }
    }

    pendingExportAction?.let { action ->
        val passwordsMatch = privateKeyExportPassword == privateKeyExportPasswordConfirmation
        val exportProtectedData = buildList {
            if (hasExportablePrivateKey) add("SSH private key")
            if (hasExportableTailscaleState) add("Tailscale connection")
        }.humanReadableList()
        AlertDialog(
            onDismissRequest = {
                if (!exportBusy) resetPrivateKeyExportDialog()
            },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text("Include protected data in export?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Export includes $exportProtectedData. Anyone with the file and password can use that access. The normal export leaves it out.",
                    )
                    OutlinedTextField(
                        privateKeyExportPassword,
                        { privateKeyExportPassword = it },
                        label = { Text("Export password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        privateKeyExportPasswordConfirmation,
                        { privateKeyExportPasswordConfirmation = it },
                        label = { Text("Confirm password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!passwordsMatch) {
                        Text(
                            "Passwords do not match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    privateKeyExportError?.let {
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
                    enabled = !exportBusy && privateKeyExportPassword.isNotBlank() && passwordsMatch,
                    onClick = { exportConfiguration(action, privateKeyExportPassword) },
                ) {
                    Text(if (exportBusy) "Exporting" else "Include protected data")
                }
            },
            dismissButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !exportBusy,
                        onClick = { exportConfiguration(action) },
                    ) {
                        Text("Without protected data")
                    }
                    TextButton(
                        enabled = !exportBusy,
                        onClick = { resetPrivateKeyExportDialog() },
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

@Composable
internal fun ThemePreferenceSelector(
    selected: ThemePreference,
    onChange: (ThemePreference) -> Unit,
) {
    Selector("Theme") {
        ThemePreference.entries.forEach { preference ->
            FilterChip(
                selected = selected == preference,
                onClick = { onChange(preference) },
                label = {
                    Text(
                        when (preference) {
                            ThemePreference.SYSTEM -> "System"
                            ThemePreference.LIGHT -> "Light"
                            ThemePreference.DARK -> "Dark"
                        },
                    )
                },
            )
        }
    }
}

@Composable
internal fun SettingsToolRow(
    label: String,
    detail: String,
    icon: ImageVector,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        EntityIcon(icon, MetricTone.Route)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CommandPreview(state: AppState, profile: BackupProfile) {
    val target = state.targets.firstOrNull { it.id == profile.targetId } ?: return
    val route = when (profile.targetMode) {
        TargetMode.TAILSCALE_FIRST_LAN_FALLBACK, TargetMode.TAILSCALE_ONLY -> Route.TAILSCALE
        else -> Route.LAN
    }
    val preview = runCatching {
        val backupCommand = RsyncCommandBuilder.build(
            profile = profile,
            target = target,
            route = route,
            binaryPaths = BinaryPaths("rsync", "ssh", "tsnet-nc"),
            sshKeyPath = "files/ssh/id_ed25519",
            knownHostsPath = "files/ssh/known_hosts",
            excludesPath = "files/run/${profile.id}/excludes",
            tailscaleStateDir = "files/tailscale-state",
            tailscaleNodeName = state.tailscale.nodeName,
        ).preview
        if (!profile.dryRunBeforeBackup) {
            backupCommand
        } else {
            val dryRunCommand = RsyncCommandBuilder.build(
                profile = profile,
                target = target,
                route = route,
                binaryPaths = BinaryPaths("rsync", "ssh", "tsnet-nc"),
                sshKeyPath = "files/ssh/id_ed25519",
                knownHostsPath = "files/ssh/known_hosts",
                excludesPath = "files/run/${profile.id}/excludes",
                tailscaleStateDir = "files/tailscale-state",
                tailscaleNodeName = state.tailscale.nodeName,
                dryRun = true,
            ).preview
            "Dry run:\n$dryRunCommand\n\nBackup:\n$backupCommand"
        }
    }.getOrElse { it.message ?: "Invalid command" }
    SectionCard {
        Text("Command preview", style = MaterialTheme.typography.titleMedium)
        SelectableBlock(preview)
    }
}
