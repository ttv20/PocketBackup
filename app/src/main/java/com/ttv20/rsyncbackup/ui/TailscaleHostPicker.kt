@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.effectiveTailscaleNodeName
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import com.ttv20.rsyncbackup.tailscale.TailscalePeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal fun sanitizePortText(value: String): String = value.filter { it.isDigit() }

internal fun portFromText(value: String): Int? =
    value.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }

internal fun normalizedHostUi(value: String): String =
    value.trim().trimEnd('.').lowercase(Locale.US)

@Composable
internal fun PortTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = portFromText(value) == null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizePortText(it)) },
        label = { Text("Port") },
        isError = isError,
        supportingText = if (isError) {
            { Text("Enter a port from $MIN_PORT to $MAX_PORT") }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
internal fun TailscaleHostPicker(
    state: AppState,
    secretStore: SecretStore,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier.fillMaxWidth(),
    loadButtonTag: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val canLoadPeers = state.tailscale.isConfigured && state.tailscale.stateSecretAlias != null
    val listNodeName = effectiveTailscaleNodeName(state)
    var peers by remember(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf<List<TailscalePeer>>(emptyList()) }
    var loading by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf(false) }
    var loadAttempted by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf(false) }
    var loadError by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf<String?>(null) }
    var dropdownExpanded by rememberSaveable(state.tailscale.stateSecretAlias, listNodeName) { mutableStateOf(false) }
    val filteredPeers = remember(peers, value) {
        val query = value.trim()
        if (query.isBlank()) {
            peers
        } else {
            peers.filter { it.matchesHostQuery(query) }
        }
    }
    val hasExactPeerMatch = peers.any { normalizedHostUi(it.host) == normalizedHostUi(value) }
    val showCustomChoice = value.isNotBlank() && !hasExactPeerMatch

    fun loadPeers() {
        if (!canLoadPeers || loading) return
        loading = true
        loadAttempted = true
        loadError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TailscaleManager(context, secretStore).listPeers(
                    nodeName = listNodeName,
                    stateSecretAlias = state.tailscale.stateSecretAlias,
                )
            }
            if (result.success) {
                peers = result.peers
                loadError = null
            } else {
                loadError = result.output.ifBlank { "Could not load Tailscale devices" }
            }
            loading = false
        }
    }

    LaunchedEffect(canLoadPeers, state.tailscale.stateSecretAlias, listNodeName) {
        if (canLoadPeers) {
            if (dropdownExpanded) loadPeers()
        } else {
            peers = emptyList()
            loading = false
            loadAttempted = false
            loadError = null
            dropdownExpanded = false
        }
    }

    LaunchedEffect(dropdownExpanded, filteredPeers.size, loading, loadError) {
        if (dropdownExpanded) {
            delay(120)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                dropdownExpanded = true
            },
            label = { Text(label) },
            trailingIcon = {
                IconButton(
                    enabled = canLoadPeers,
                    modifier = loadButtonTag?.let { Modifier.testTag(it) } ?: Modifier,
                    onClick = {
                        dropdownExpanded = !dropdownExpanded
                        if (canLoadPeers && !loadAttempted) loadPeers()
                    },
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = "Show Tailscale devices")
                }
            },
            modifier = fieldModifier.onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    dropdownExpanded = true
                    if (canLoadPeers && !loadAttempted) loadPeers()
                }
            },
            singleLine = true,
        )
        if (dropdownExpanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "load") {
                        TailscaleHostMenuRow(
                            leadingIcon = Icons.Outlined.Sync,
                            title = when {
                                loading -> "Loading peers"
                                peers.isEmpty() -> "Load peers"
                                else -> "Refresh peers"
                            },
                            subtitle = if (canLoadPeers) "Search Tailscale devices or enter a custom host" else "Enter a host manually",
                            enabled = canLoadPeers && !loading,
                            onClick = { loadPeers() },
                        )
                    }
                    items(filteredPeers, key = { it.host }) { peer ->
                        TailscaleHostMenuRow(
                            leadingIcon = Icons.Outlined.Cloud,
                            title = peer.primaryLabel(),
                            subtitle = peer.secondaryLabel(),
                            selected = normalizedHostUi(value) == normalizedHostUi(peer.host),
                            onClick = {
                                onValueChange(peer.host)
                                dropdownExpanded = false
                            },
                        )
                    }
                    if (showCustomChoice) {
                        item(key = "custom") {
                            TailscaleHostMenuRow(
                                leadingIcon = Icons.Outlined.Edit,
                                title = "Use custom host",
                                subtitle = value.trim(),
                                onClick = { dropdownExpanded = false },
                            )
                        }
                    }
                    if (!loading && loadAttempted && peers.isNotEmpty() && filteredPeers.isEmpty() && !showCustomChoice) {
                        item(key = "empty-filter") {
                            TailscaleHostMenuRow(
                                title = "No matching Tailscale devices",
                                subtitle = "Keep typing to use a custom host",
                                enabled = false,
                            )
                        }
                    }
                    if (!loading && loadAttempted && peers.isEmpty()) {
                        item(key = "empty-peers") {
                            TailscaleHostMenuRow(
                                title = "No Tailscale devices found",
                                subtitle = "Enter a host manually or refresh peers",
                                enabled = false,
                            )
                        }
                    }
                }
            }
        }
        when {
            !canLoadPeers -> Text(
                "Tailscale is not connected; enter a host manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loadError != null -> Text(
                friendlyTailscaleError(loadError ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = toneColor(MetricTone.Destructive),
            )
            loadAttempted && !loading && peers.isEmpty() -> Text(
                "No Tailscale devices found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TailscaleHostMenuRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun TailscalePeer.primaryLabel(): String =
    hostName?.takeIf { it.isNotBlank() } ?: dnsName?.takeIf { it.isNotBlank() } ?: host

internal fun TailscalePeer.secondaryLabel(): String {
    val status = if (online) "online" else "offline"
    val hostPart = host.takeIf { it != primaryLabel() }
    val ipPart = tailscaleIps.firstOrNull()
    return listOfNotNull(hostPart, ipPart, os?.takeIf { it.isNotBlank() }, status).joinToString(" - ")
}

internal fun TailscalePeer.matchesHostQuery(query: String): Boolean {
    val normalizedQuery = normalizedHostUi(query)
    return listOf(host, hostName, dnsName, os)
        .filterNotNull()
        .any { normalizedHostUi(it).contains(normalizedQuery) } ||
        tailscaleIps.any { it.contains(query.trim(), ignoreCase = true) }
}

internal fun Uri.toSharedStoragePath(): String? =
    runCatching { DocumentsContract.getTreeDocumentId(this) }
        .getOrNull()
        ?.let(::sharedStoragePathFromTreeDocumentId)

internal fun sharedStoragePathFromTreeDocumentId(treeDocumentId: String): String? {
    val parts = treeDocumentId.split(":", limit = 2)
    val volume = parts.firstOrNull()?.lowercase() ?: return null
    val relativePath = parts.getOrNull(1).orEmpty().trim('/')
    val root = when (volume) {
        "primary" -> "/storage/emulated/0"
        "home" -> "/storage/emulated/0/Documents"
        else -> return null
    }
    return if (relativePath.isBlank()) root else "$root/$relativePath"
}
