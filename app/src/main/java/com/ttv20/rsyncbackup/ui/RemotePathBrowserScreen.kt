@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.ssh.SshRemotePathBrowser
import com.ttv20.rsyncbackup.ssh.SshRemotePathBrowserSession
import com.ttv20.rsyncbackup.ssh.SshRemotePathListing
import com.ttv20.rsyncbackup.storage.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EntityList(
    title: String,
    items: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    addButtonTag: String,
    modifier: Modifier = Modifier,
) {
    val rows = items
    val listState = rememberLazyListState()
    LaunchedEffect(rows.map { it.first }, selectedId) {
        val selectedIndex = rows.indexOfFirst { it.first == selectedId }
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag(addButtonTag),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add")
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows, key = { it.first }) { (id, label) ->
                    FilterChip(
                        selected = selectedId == id,
                        onClick = { onSelect(id) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TargetModeSelector(
    targetMode: TargetMode,
    target: TargetRecord? = null,
    onChange: (TargetMode) -> Unit,
) {
    Selector("Target mode") {
        TargetMode.entries.forEach { mode ->
            val unavailableReason = target?.let { mode.unavailableReason(it) }
            FilterChip(
                selected = targetMode == mode,
                onClick = { if (unavailableReason == null) onChange(mode) },
                enabled = unavailableReason == null,
                label = { Text(routeModeLabel(mode)) },
                modifier = Modifier.testTag("target-mode-${mode.name.lowercase()}"),
            )
        }
    }
    target?.let { selectedTarget ->
        unavailableTargetModeMessage(selectedTarget)?.let { message ->
            FeedbackBanner("Target mode unavailable", message, MetricTone.Warning)
        }
    }
}

@Composable
internal fun RemotePathPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    target: TargetRecord?,
    routes: List<Route>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fieldTag: String? = null,
    browseButtonTag: String? = null,
    onBrowse: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier
                .weight(1f)
                .then(fieldTag?.let { Modifier.testTag(it) } ?: Modifier),
        )
        OutlinedButton(
            enabled = enabled && target != null && routes.isNotEmpty(),
            onClick = onBrowse,
            modifier = browseButtonTag?.let { Modifier.testTag(it) } ?: Modifier,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Browse")
        }
    }
}

@Composable
internal fun RemotePathBrowserScreen(
    title: String,
    state: AppState,
    target: TargetRecord,
    routes: List<Route>,
    startPath: String,
    secretStore: SecretStore,
    onPathSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browser = remember(context, secretStore) { SshRemotePathBrowser(context, secretStore) }
    var draftPath by rememberSaveable(target.id, startPath) { mutableStateOf(startPath.trim().ifBlank { "~" }) }
    var listing by remember(target.id, startPath) { mutableStateOf<SshRemotePathListing?>(null) }
    var session by remember(target.id, startPath) { mutableStateOf<SshRemotePathBrowserSession?>(null) }
    val activeSession = rememberUpdatedState(session)
    var loading by rememberSaveable(target.id, startPath) { mutableStateOf(false) }
    var connecting by rememberSaveable(target.id, startPath) { mutableStateOf(true) }
    var error by rememberSaveable(target.id, startPath) { mutableStateOf<String?>(null) }
    var showHidden by rememberSaveable(target.id) { mutableStateOf(false) }
    var loadingPath by rememberSaveable(target.id, startPath) { mutableStateOf<String?>(null) }

    fun load(path: String, rowPath: String? = null) {
        val currentSession = session ?: return
        val requestedPath = path.trim().ifBlank { "~" }
        if (rowPath == null) {
            draftPath = requestedPath
        }
        loading = true
        loadingPath = rowPath
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    currentSession.listDirectories(requestedPath, showHidden)
                }
            }.onSuccess { result ->
                listing = result
                draftPath = result.resolvedPath
                error = null
            }.onFailure { failure ->
                error = failure.message ?: "Remote path browser failed"
                listing?.resolvedPath?.let { currentPath ->
                    draftPath = currentPath
                }
            }
            loadingPath = null
            loading = false
        }
    }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        onDispose {
            activeSession.value?.let { closingSession ->
                scope.launch(Dispatchers.IO) {
                    closingSession.close()
                }
            }
        }
    }

    LaunchedEffect(target.id, target.user, target.lanHost, target.tailscaleHost, target.port, routes, state.sshKeySettings, state.trustedHostFingerprints, state.tailscale) {
        val previousSession = session
        session = null
        previousSession?.let { closingSession ->
            withContext(Dispatchers.IO) { closingSession.close() }
        }
        connecting = true
        loading = true
        loadingPath = null
        error = null
        listing = null
        runCatching {
            withContext(Dispatchers.IO) {
                val openedSession = browser.openSession(state, target, routes)
                openedSession to openedSession.listDirectories(draftPath, showHidden)
            }
        }.onSuccess { (openedSession, firstListing) ->
            session = openedSession
            listing = firstListing
            draftPath = firstListing.resolvedPath
            error = null
        }.onFailure { failure ->
            error = failure.message ?: "Remote path browser failed"
        }
        connecting = false
        loading = false
    }

    LaunchedEffect(showHidden) {
        session?.let { currentSession ->
            loading = true
            loadingPath = null
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    currentSession.listDirectories(draftPath, showHidden)
                }
            }.onSuccess { result ->
                listing = result
                draftPath = result.resolvedPath
                error = null
            }.onFailure { failure ->
                error = failure.message ?: "Remote path browser failed"
                listing?.resolvedPath?.let { currentPath ->
                    draftPath = currentPath
                }
            }
            loading = false
        }
    }

    val selectedPath = listing?.resolvedPath ?: draftPath.trim().ifBlank { "~" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("remote-path-browser-screen"),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${target.user}@${target.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(
                        label = listing?.let { "via ${routeLabel(it.route)}" }
                            ?: when {
                                connecting -> "Connecting"
                                error != null -> "Failed"
                                else -> "Ready"
                            },
                        tone = if (listing != null || error == null) MetricTone.Route else MetricTone.Destructive,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = draftPath,
                        onValueChange = { draftPath = it },
                        label = { Text("Current path") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("remote-path-browser-current-path-field"),
                    )
                    OutlinedButton(
                        enabled = !loading && session != null,
                        onClick = { load(draftPath) },
                        modifier = Modifier.testTag("remote-path-browser-go-button"),
                    ) {
                        Text("Go")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ToggleRow(
                        label = "Show hidden folders",
                        checked = showHidden,
                        switchTag = "remote-path-browser-hidden-switch",
                    ) { showHidden = it }
                }
                listing?.let { result ->
                    Text(
                        "${result.resolvedPath} on ${result.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (connecting || (loading && listing == null)) {
                FeedbackBanner(
                    title = if (connecting) "Opening SSH session" else "Browsing server",
                    detail = if (connecting) {
                        "Connecting to ${target.name}"
                    } else {
                        "Loading $draftPath"
                    },
                    tone = MetricTone.Route,
                )
            } else if (loading && loadingPath == null) {
                RemotePathLoadingStrip("Refreshing folders")
            }
            error?.let {
                FeedbackBanner(
                    title = "Browse failed",
                    detail = conciseFeedbackMessage(it),
                    tone = MetricTone.Destructive,
                )
            }
            listing?.let { result ->
                RemotePathList(
                    listing = result,
                    loading = loading,
                    loadingPath = loadingPath,
                    onOpenPath = { path -> load(path, rowPath = path) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text("Cancel")
                }
                Button(
                    enabled = !loading && selectedPath.isNotBlank() && listing != null,
                    onClick = { onPathSelected(selectedPath) },
                    modifier = Modifier.testTag("remote-path-browser-use-button"),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use path")
                }
            }
        }
    }
}

@Composable
internal fun RemotePathList(
    listing: SshRemotePathListing,
    loading: Boolean,
    loadingPath: String?,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("remote-path-browser-list"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listing.parentPath?.let { parentPath ->
            item(key = "parent") {
                RemotePathRow(
                    label = "Parent folder",
                    path = parentPath,
                    enabled = !loading,
                    loading = loadingPath == parentPath,
                    parent = true,
                    onClick = { onOpenPath(parentPath) },
                )
            }
        }
        items(listing.entries, key = { it.path }) { entry ->
            RemotePathRow(
                label = entry.name,
                path = entry.path,
                enabled = !loading,
                loading = loadingPath == entry.path,
                parent = false,
                onClick = { onOpenPath(entry.path) },
            )
        }
        if (listing.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No child folders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun RemotePathRow(
    label: String,
    path: String,
    enabled: Boolean,
    loading: Boolean,
    parent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (parent) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                Text(
                    "Opening",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun RemotePathLoadingStrip(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

internal fun browseRoutesForTarget(target: TargetRecord): List<Route> =
    buildList {
        if (target.lanHost.isNotBlank()) add(Route.LAN)
        if (!target.tailscaleHost.isNullOrBlank()) add(Route.TAILSCALE)
    }

internal fun routeLabel(route: Route): String =
    when (route) {
        Route.LAN -> "Server address"
        Route.TAILSCALE -> "Tailscale device"
    }
