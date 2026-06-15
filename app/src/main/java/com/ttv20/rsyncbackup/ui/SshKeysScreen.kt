@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VpnKey
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.suggestedSshKeyName
import com.ttv20.rsyncbackup.ssh.SshKeyManager
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
internal fun SshKeysScreen(state: AppState, repository: AppRepository, secretStore: SecretStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customKey by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var successMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteWarning by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val publicKey = state.sshKeySettings.publicKey
    val hasConfiguredSshKey = publicKey != null ||
        state.sshKeySettings.privateKeySecretAlias != null ||
        state.sshKeySettings.passphraseSecretAlias != null
    val hasStoredPrivateKey = state.sshKeySettings.privateKeySecretAlias != null
    val isCustomKey = state.sshKeySettings.customPrivateKeyLabel != null
    val keyStatus = when {
        !hasConfiguredSshKey -> "No SSH key yet"
        isCustomKey -> "Custom key stored"
        else -> "SSH key ready"
    }
    val passphraseStatus = if (state.sshKeySettings.passphraseSecretAlias != null) {
        "Passphrase stored"
    } else {
        "No passphrase stored"
    }
    val keyDetail = state.sshKeySettings.generatedAt?.let { "Generated ${formatTimestampUi(it)}" }
        ?: state.sshKeySettings.customPrivateKeyLabel
        ?: state.sshKeySettings.keyType
    val generateKey: () -> Unit = {
        runCatching {
            SshKeyManager(secretStore).generateEd25519(
                keyName = suggestedSshKeyName(state.settings.phoneHostname),
            )
            }
            .onSuccess { key ->
                repository.update { appState ->
                    appState.copy(
                        sshKeySettings = GlobalSshKeySettings(
                            publicKey = key.publicKey,
                            privateKeySecretAlias = key.privateKeyAlias,
                            generatedAt = key.generatedAt,
                        ),
                    )
                }
                diagnosticsController(context)?.trackEvent(
                    "ssh_key_generated",
                    mapOf(DiagnosticsAttributes.SOURCE to "settings"),
                )
                error = null
                successMessage = "App SSH key generated. Copy the public key or install it on your target."
            }
            .onFailure {
                successMessage = null
                error = it.message
            }
        Unit
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ssh-keys-scroll")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("SSH Access")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EntityIcon(Icons.Outlined.Key, if (hasConfiguredSshKey) MetricTone.Success else MetricTone.Warning)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        keyStatus,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        passphraseStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        keyDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(
                    label = if (hasConfiguredSshKey) "Ready" else "Needs setup",
                    tone = if (hasConfiguredSshKey) MetricTone.Success else MetricTone.Warning,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (publicKey == null) {
                    Button(
                        onClick = generateKey,
                        modifier = Modifier.testTag("ssh-generate-key-button"),
                    ) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate app key")
                    }
                } else {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(publicKey)) },
                        modifier = Modifier.testTag("ssh-public-key-copy-button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy public key")
                    }
                }
                if (hasConfiguredSshKey) {
                    OutlinedButton(
                        onClick = { showDeleteWarning = true },
                        modifier = Modifier.testTag("ssh-delete-key-button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete key")
                    }
                }
            }
            if (showDeleteWarning) {
                AlertDialog(
                    onDismissRequest = { showDeleteWarning = false },
                    icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
                    title = { Text("Delete SSH key?") },
                    text = {
                        Text("Backups cannot authenticate until you generate or store another SSH key. This removes the private key and passphrase from secure storage.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                runCatching {
                                    SshKeyManager(secretStore).deleteConfiguredKey(state.sshKeySettings)
                                    repository.update { appState ->
                                        appState.copy(sshKeySettings = GlobalSshKeySettings())
                                    }
                                }.onSuccess {
                                    error = null
                                    successMessage = "SSH key deleted. Generate or store another key before running backups."
                                    showDeleteWarning = false
                                }.onFailure {
                                    successMessage = null
                                    error = it.message
                                }
                            },
                            modifier = Modifier.testTag("ssh-confirm-delete-key-button"),
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteWarning = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
            successMessage?.let {
                FeedbackBanner("SSH access updated", it, MetricTone.Success)
            }
            error?.let {
                FeedbackBanner("SSH action failed", it, MetricTone.Destructive)
            }
        }
        SectionCard {
            Text("Use existing key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(customKey, { customKey = it }, label = { Text("Private key") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val keyAlias = "custom-ssh-private-key"
                    val passphraseAlias = "custom-ssh-passphrase"
                    val privateKey = customKey
                    val privateKeyPassphrase = passphrase
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val nativeInstall = NativeBinaryManager(context).ensureInstalled()
                                val sshKeygen = nativeInstall.requireTool("ssh-keygen")
                                val publicKey = SshKeyManager(secretStore).extractPublicKeyFromPrivateKey(
                                    sshKeygenPath = sshKeygen,
                                    filesDir = context.filesDir,
                                    workDir = context.cacheDir,
                                    privateKey = privateKey,
                                    passphrase = privateKeyPassphrase,
                                )
                                SshKeyManager(secretStore).storeCustomPrivateKey(keyAlias, privateKey)
                                if (privateKeyPassphrase.isNotBlank()) {
                                    secretStore.put(passphraseAlias, privateKeyPassphrase.toByteArray())
                                }
                                publicKey
                            }
                        }.onSuccess { publicKey ->
                            repository.update { appState ->
                                appState.copy(
                                    sshKeySettings = GlobalSshKeySettings(
                                        publicKey = publicKey,
                                        privateKeySecretAlias = keyAlias,
                                        customPrivateKeyLabel = "Custom key",
                                        passphraseSecretAlias = passphraseAlias.takeIf { privateKeyPassphrase.isNotBlank() },
                                        generatedAt = Instant.now().toString(),
                                    ),
                                )
                            }
                            customKey = ""
                            passphrase = ""
                            error = null
                            successMessage = "Custom private key stored. Backups can use it for SSH authentication."
                        }.onFailure {
                            successMessage = null
                            error = it.message
                        }
                    }
                },
                enabled = customKey.isNotBlank(),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Store existing key")
            }
        }
        SectionCard {
            Text("Key details", style = MaterialTheme.typography.titleMedium)
            StatusBadge(if (hasStoredPrivateKey) "Private key stored" else "No private key stored", if (hasStoredPrivateKey) MetricTone.Success else MetricTone.Warning)
            Text(if (isCustomKey) "Custom key stored" else "Generated key", style = MaterialTheme.typography.bodySmall)
            publicKey?.let { value ->
                Text("Public key preview", style = MaterialTheme.typography.labelLarge)
                SelectableBlock(value)
            }
        }
    }
}
