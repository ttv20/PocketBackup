package com.ttv20.rsyncbackup.backup

import android.content.Context
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsController
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.BackupStatusMarker
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.requiresTailscale
import com.ttv20.rsyncbackup.model.resolvedSshKeySettings
import com.ttv20.rsyncbackup.model.routeOrder
import com.ttv20.rsyncbackup.model.toJson
import com.ttv20.rsyncbackup.model.transferProgressPercent
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

private const val RSYNC_PROGRESS_UPDATE_INTERVAL_MILLIS = 1_000L

class BackupEngine(
    private val context: Context,
    private val repository: AppRepository,
    private val secretStore: SecretStore,
    private val nativeBinaryManager: NativeBinaryManager = NativeBinaryManager(context),
    private val processController: BackupProcessController = BackupProcessController(),
    private val diagnostics: DiagnosticsController? = null,
) {
    suspend fun runProfile(
        profileId: String,
        trigger: BackupRunTrigger = BackupRunTrigger.MANUAL,
    ): BackupLog = withContext(Dispatchers.IO) {
        val state = repository.state.value
        val profile = state.profiles.first { it.id == profileId }
        val target = state.targets.first { it.id == profile.targetId }
        val startedAt = Instant.now().toString()
        val recentOutput = mutableListOf<String>()
        var selectedRoute: Route? = null
        processController.reset()
        repository.setRunProgress(
            RunProgressState(
                profileId = profile.id,
                profileName = profile.name,
                phase = RunProgressPhase.PREPARING,
                message = "Preparing backup",
                startedAt = startedAt,
                updatedAt = startedAt,
            ),
        )
        repository.markProfile(profile.id, RunStatus.RUNNING, "Backup running", startedAt)

        fun backupDiagnosticsAttributes(routeUsed: Route? = selectedRoute): Map<String, Any?> =
            DiagnosticsAttributes.backupIdentity(profile) + mapOf(
                DiagnosticsAttributes.TRIGGER_TYPE to trigger.name.lowercase(),
                DiagnosticsAttributes.TARGET_MODE to profile.targetMode.name.lowercase(),
                DiagnosticsAttributes.ROUTE_USED to routeUsed?.name?.lowercase(),
                DiagnosticsAttributes.DRY_RUN_ENABLED to profile.dryRunBeforeBackup,
                DiagnosticsAttributes.DELETE_ENABLED to profile.deleteEnabled,
            )

        fun trackBackupPrepareFailed(
            failureStage: String,
            failureCategory: String,
            routeUsed: Route? = selectedRoute,
            exitCode: Int? = null,
            missingComponents: List<String> = emptyList(),
        ) {
            diagnostics?.trackEvent(
                "backup_prepare_failed",
                backupDiagnosticsAttributes(routeUsed) + mapOf(
                    DiagnosticsAttributes.FAILURE_STAGE to failureStage,
                    DiagnosticsAttributes.FAILURE_CATEGORY to failureCategory,
                    DiagnosticsAttributes.EXIT_CODE to exitCode,
                    DiagnosticsAttributes.END_REASON to "error",
                    DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS to missingComponents.takeIf { it.isNotEmpty() },
                ),
            )
        }

        fun failedLog(
            summary: String,
            raw: String = "",
            targetHostUsed: String? = null,
            routeUsed: Route? = selectedRoute,
            failureStage: String? = null,
            failureCategory: String? = null,
            rsyncExitCode: Int? = null,
        ): BackupLog {
            val finishedAt = Instant.now().toString()
            val log = BackupLog(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                profileName = profile.name,
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = RunStatus.FAILED,
                trigger = trigger,
                endReason = errorBackupEndReason(summary, raw),
                endReasonDetail = summary,
                exitCode = rsyncExitCode,
                targetHostUsed = targetHostUsed,
                targetMode = profile.targetMode,
                routeUsed = routeUsed,
                failureStage = failureStage,
                failureCategory = failureCategory,
                dryRunEnabled = profile.dryRunBeforeBackup,
                deleteEnabled = profile.deleteEnabled,
                summary = summary,
                raw = raw,
            )
            repository.setRunProgress(
                repository.state.value.runProgress.copy(
                    phase = RunProgressPhase.FAILED,
                    message = summary,
                    updatedAt = finishedAt,
                ),
            )
            repository.appendLog(log)
            repository.markProfile(profile.id, RunStatus.FAILED, summary, finishedAt)
            return log
        }

        val tailscaleManager = TailscaleManager(context, secretStore, nativeBinaryManager)
        var restoredTailscaleStateAlias: String? = null
        var selectedTailscaleForward: TailscaleTcpForward? = null
        try {
            val nativeInstall = nativeBinaryManager.ensureInstalled()
            if (!nativeInstall.isComplete) {
                diagnostics?.trackEvent(
                    "native_payload_install_failed",
                    mapOf(
                        DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS to nativeInstall.missing,
                    ),
                )
                trackBackupPrepareFailed(
                    failureStage = "native_install",
                    failureCategory = "missing_native_binaries",
                    missingComponents = nativeInstall.missing,
                )
                return@withContext failedLog(
                    summary = "Missing native binaries: ${nativeInstall.missing.joinToString()}",
                    failureStage = "native_install",
                    failureCategory = "missing_native_binaries",
                )
            }

            if (profile.targetMode.requiresTailscale()) {
                val stateAlias = state.tailscale.stateSecretAlias
                if (!state.tailscale.isConfigured || stateAlias == null) {
                    trackBackupPrepareFailed(
                        failureStage = "tailscale_restore",
                        failureCategory = "tailscale_not_configured",
                    )
                    return@withContext failedLog(
                        summary = "Tailscale is not configured for ${profile.targetMode}",
                        failureStage = "tailscale_restore",
                        failureCategory = "tailscale_not_configured",
                    )
                }
                runCatching { tailscaleManager.restoreState(stateAlias) }
                    .onFailure { error ->
                        trackBackupPrepareFailed(
                            failureStage = "tailscale_restore",
                            failureCategory = "tailscale_restore_failed",
                        )
                        return@withContext failedLog(
                            summary = "Tailscale state restore failed: ${error.message}",
                            failureStage = "tailscale_restore",
                            failureCategory = "tailscale_restore_failed",
                        )
                    }
                restoredTailscaleStateAlias = stateAlias
            }

            val sshKeySettings = target.resolvedSshKeySettings(state.sshKeySettings)
            val privateKeyAlias = sshKeySettings.privateKeySecretAlias
            val privateKeyBytes = privateKeyAlias?.let(secretStore::get)
            if (privateKeyBytes == null) {
                trackBackupPrepareFailed(
                    failureStage = "ssh_key",
                    failureCategory = "missing_ssh_private_key",
                )
                return@withContext failedLog(
                    summary = "No SSH private key is configured",
                    failureStage = "ssh_key",
                    failureCategory = "missing_ssh_private_key",
                )
            }
            val passphraseBytes = sshKeySettings.passphraseSecretAlias?.let { alias ->
                secretStore.get(alias) ?: run {
                    trackBackupPrepareFailed(
                        failureStage = "ssh_key",
                        failureCategory = "missing_ssh_passphrase",
                    )
                    return@withContext failedLog(
                        summary = "SSH private key passphrase is missing",
                        failureStage = "ssh_key",
                        failureCategory = "missing_ssh_passphrase",
                    )
                }
            }
            val knownHostsText = SshRuntimeFiles.knownHostsText(target, state.trustedHostFingerprints)
            if (knownHostsText.isBlank()) {
                trackBackupPrepareFailed(
                    failureStage = "host_key",
                    failureCategory = "missing_trusted_host_key",
                )
                return@withContext failedLog(
                    summary = "No trusted SSH host key is configured for ${target.name}",
                    failureStage = "host_key",
                    failureCategory = "missing_trusted_host_key",
                )
            }
            val commandInputs = writeCommandInputs(profile, privateKeyBytes, passphraseBytes, knownHostsText)
            val output = StringBuilder()
            var lastRsyncProgressLine: String? = null
            var plannedTransferBytesRaw: Long? = null
            var lastRsyncProgressUpdateMillis = 0L

            fun recordLine(line: String) {
                output.appendLine(line)
                recentOutput += line
                if (recentOutput.size > RECENT_OUTPUT_LIMIT) {
                    recentOutput.removeAt(0)
                }
            }

            fun finalRaw(summary: String): String =
                buildString {
                    append(output)
                    lastRsyncProgressLine?.let { progressLine ->
                        if (isNotEmpty() && !endsWith("\n")) appendLine()
                        appendLine(progressLine)
                    }
                    appendLine(summary)
                }

            fun updateProgress(
                phase: RunProgressPhase,
                message: String,
                progress: RsyncProgress = RsyncProgress(),
                persist: Boolean = false,
                force: Boolean = true,
            ) {
                val isRsyncProgressPhase = phase == RunProgressPhase.DRY_RUN || phase == RunProgressPhase.RUNNING_RSYNC
                val nowMillis = System.nanoTime() / 1_000_000L
                if (
                    isRsyncProgressPhase &&
                    !persist &&
                    !force &&
                    lastRsyncProgressUpdateMillis != 0L &&
                    nowMillis - lastRsyncProgressUpdateMillis < RSYNC_PROGRESS_UPDATE_INTERVAL_MILLIS
                ) {
                    return
                }
                if (isRsyncProgressPhase) {
                    lastRsyncProgressUpdateMillis = nowMillis
                }
                val now = Instant.now().toString()
                repository.setRunProgress(
                    RunProgressState(
                        profileId = profile.id,
                        profileName = profile.name,
                        phase = phase,
                        message = message,
                        startedAt = startedAt,
                        updatedAt = now,
                        filesDiscovered = progress.filesDiscovered,
                        filesTransferred = progress.filesTransferred,
                        progressPercent = transferProgressPercent(
                            bytesTransferredRaw = progress.bytesTransferredRaw,
                            plannedTransferBytesRaw = plannedTransferBytesRaw,
                        ) ?: progress.progressPercent,
                        bytesTransferred = progress.bytesTransferred,
                        bytesTransferredRaw = progress.bytesTransferredRaw,
                        plannedTransferBytesRaw = plannedTransferBytesRaw,
                        speed = progress.speed,
                        averageBytesPerSecond = progress.averageBytesPerSecond,
                        recentAverageBytesPerSecond = progress.recentAverageBytesPerSecond,
                        duration = progress.duration,
                        currentFile = progress.currentFile,
                        finalStats = progress.finalStats,
                        recentOutput = recentOutput.toList(),
                    ),
                    persist = persist,
                )
            }

            fun cancelledLog(reason: BackupStopReason, targetHostUsed: String? = null): BackupLog {
                val finishedAt = Instant.now().toString()
                val summary = when (reason) {
                    BackupStopReason.CANCELLED -> "Backup cancelled"
                    BackupStopReason.FORCE_STOPPED -> "Backup force-stopped"
                }
                val log = BackupLog(
                    id = UUID.randomUUID().toString(),
                    profileId = profile.id,
                    profileName = profile.name,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    status = RunStatus.CANCELLED,
                    trigger = trigger,
                    endReason = reason.toBackupEndReason(),
                    endReasonDetail = reason.toBackupEndReasonDetail(),
                    targetHostUsed = targetHostUsed,
                    targetMode = profile.targetMode,
                    routeUsed = selectedRoute,
                    dryRunEnabled = profile.dryRunBeforeBackup,
                    deleteEnabled = profile.deleteEnabled,
                    summary = summary,
                    raw = finalRaw(summary),
                )
                updateProgress(RunProgressPhase.CANCELLED, summary, persist = true)
                repository.appendLog(log)
                repository.markProfile(profile.id, RunStatus.CANCELLED, summary, finishedAt)
                return log
            }

            fun routeConnection(route: Route, forward: TailscaleTcpForward? = null) = SshConnection(
                    target = target,
                    route = route,
                    binaryPaths = nativeInstall.paths,
                    sshKeyPath = commandInputs.sshKeyPath,
                    knownHostsPath = commandInputs.knownHostsPath,
                    tailscaleStateDir = commandInputs.tailscaleStateDir,
                    tailscaleNodeName = repository.state.value.tailscale.nodeName,
                    usesAskpass = commandInputs.askpassPath != null,
                    connectHostOverride = forward?.host,
                    connectPortOverride = forward?.port,
                    hostKeyAlias = forward?.targetHost,
                )

            var lastRouteFailure: String? = null
            for (candidate in profile.targetMode.routeOrder()) {
                val hostResult = runCatching { RsyncCommandBuilder.targetHost(target, candidate) }
                if (hostResult.isFailure) {
                    val message = "Route $candidate is not configured: ${hostResult.exceptionOrNull()?.message}"
                    lastRouteFailure = message
                    recordLine(message)
                    continue
                }
                val host = hostResult.getOrThrow()
                var candidateForward: TailscaleTcpForward? = null
                try {
                    if (candidate == Route.TAILSCALE) {
                        updateProgress(RunProgressPhase.PREPARING, "Opening Tailscale route to $host")
                        candidateForward = TailscaleTcpForward.start(
                            tsnetHelperPath = nativeInstall.paths.tsnetHelper,
                            filesDir = context.filesDir,
                            tailscaleStateDir = File(commandInputs.tailscaleStateDir),
                            tailscaleNodeName = repository.state.value.tailscale.nodeName,
                            targetHost = host,
                            targetPort = target.port,
                        )
                        recordLine("Tailscale route to $host forwarded on ${candidateForward.host}:${candidateForward.port}")
                    }
                    val connectionProbe = routeConnection(candidate, candidateForward)
                    updateProgress(RunProgressPhase.PREPARING, "Testing $candidate route to $host")
                    val probeCommand = RemoteTargetCommands.connectivityTest(connectionProbe)
                    val probeResult = runCommand(probeCommand, commandInputs.askpassPath) { line ->
                        recordLine(line)
                        updateProgress(RunProgressPhase.PREPARING, "Testing $candidate route to $host")
                    }
                    probeResult.stopReason?.let { return@withContext cancelledLog(it, host) }
                    val probeExit = probeResult.exitCode ?: -1
                    if (probeExit == 0) {
                        selectedRoute = candidate
                        selectedTailscaleForward = candidateForward
                        candidateForward = null
                        break
                    }
                    val message = "Route $candidate to $host failed with exit $probeExit"
                    lastRouteFailure = message
                    recordLine(message)
                } catch (error: Exception) {
                    val message = "Route $candidate to $host failed: ${error.message}"
                    lastRouteFailure = message
                    recordLine(message)
                } finally {
                    candidateForward?.close()
                }
            }
            val route = selectedRoute ?: return@withContext failedLog(
                summary = "No usable route for ${target.name}: ${lastRouteFailure ?: "no routes configured"}",
                raw = output.toString(),
                failureStage = "route_probe",
                failureCategory = "no_usable_route",
            )
            diagnostics?.trackEvent(
                "backup_route_selected",
                backupDiagnosticsAttributes(route),
            )
            val connection = routeConnection(route, selectedTailscaleForward)
            val command = buildRsyncCommand(
                profile,
                target,
                route,
                nativeInstall.paths,
                commandInputs,
                selectedTailscaleForward,
            )

            fun uploadCompletionArtifacts(
                status: RunStatus,
                exitCode: Int,
                finishedAt: String,
                summary: String,
                progress: RsyncProgress,
            ): BackupLog? {
                updateProgress(RunProgressPhase.UPLOADING_STATUS, "Uploading backup status", progress)
                val statusJson = BackupStatusMarker(
                    profileId = profile.id,
                    profileName = profile.name,
                    phoneHostname = state.settings.phoneHostname,
                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0",
                    sourcePath = profile.sourcePath,
                    targetHostUsed = command.targetHost,
                    targetMode = profile.targetMode,
                    status = status.name.lowercase(),
                    finishTime = finishedAt,
                    rsyncExitCode = exitCode,
                ).toJson()
                val uploadStatusResult = runCommand(
                    RemoteTargetCommands.uploadStatus(profile, connection, statusJson),
                    commandInputs.askpassPath,
                ) { line ->
                    recordLine(line)
                    updateProgress(RunProgressPhase.UPLOADING_STATUS, "Uploading backup status", progress)
                }
                uploadStatusResult.stopReason?.let { return cancelledLog(it, command.targetHost) }
                if ((uploadStatusResult.exitCode ?: -1) != 0) {
                    return failedLog(
                        summary = "Backup completed but status upload failed with exit ${uploadStatusResult.exitCode ?: -1}",
                        raw = output.toString(),
                        targetHostUsed = command.targetHost,
                        routeUsed = route,
                        failureStage = "status_upload",
                        failureCategory = "command_exit_nonzero",
                    )
                }
                val uploadLogResult = runCommand(
                    RemoteTargetCommands.uploadLastLog(profile, connection, finalRaw(summary)),
                    commandInputs.askpassPath,
                ) { line ->
                    recordLine(line)
                    updateProgress(RunProgressPhase.UPLOADING_STATUS, "Uploading backup log", progress)
                }
                uploadLogResult.stopReason?.let { return cancelledLog(it, command.targetHost) }
                if ((uploadLogResult.exitCode ?: -1) != 0) {
                    return failedLog(
                        summary = "Backup completed but log upload failed with exit ${uploadLogResult.exitCode ?: -1}",
                        raw = output.toString(),
                        targetHostUsed = command.targetHost,
                        routeUsed = route,
                        failureStage = "log_upload",
                        failureCategory = "command_exit_nonzero",
                    )
                }
                return null
            }

            fun completeRun(
                status: RunStatus,
                exitCode: Int,
                summary: String,
                progress: RsyncProgress,
            ): BackupLog {
                val finishedAt = Instant.now().toString()
                if (status == RunStatus.SUCCESS || status == RunStatus.WARNING) {
                    uploadCompletionArtifacts(status, exitCode, finishedAt, summary, progress)?.let { return it }
                }
                val raw = finalRaw(summary)
                val log = BackupLog(
                    id = UUID.randomUUID().toString(),
                    profileId = profile.id,
                    profileName = profile.name,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    status = status,
                    trigger = trigger,
                    endReason = if (status == RunStatus.FAILED) {
                        errorBackupEndReason(summary, raw)
                    } else {
                        null
                    },
                    endReasonDetail = if (status == RunStatus.FAILED) summary else null,
                    exitCode = exitCode,
                    targetHostUsed = command.targetHost,
                    targetMode = profile.targetMode,
                    routeUsed = route,
                    failureStage = if (status == RunStatus.FAILED) "rsync" else null,
                    failureCategory = if (status == RunStatus.FAILED) "rsync_exit_nonzero" else null,
                    dryRunEnabled = profile.dryRunBeforeBackup,
                    deleteEnabled = profile.deleteEnabled,
                    summary = summary,
                    raw = raw,
                )
                updateProgress(
                    phase = if (status == RunStatus.FAILED) RunProgressPhase.FAILED else RunProgressPhase.COMPLETED,
                    message = summary,
                    progress = progress,
                    persist = true,
                )
                repository.appendLog(log)
                repository.markProfile(profile.id, status, summary, finishedAt)
                return log
            }

            updateProgress(RunProgressPhase.PREPARING, "Checking remote target")
            val prepareResult = runCommand(RemoteTargetCommands.prepareTarget(profile, connection), commandInputs.askpassPath) { line ->
                recordLine(line)
                updateProgress(RunProgressPhase.PREPARING, "Checking remote target")
            }
            prepareResult.stopReason?.let { return@withContext cancelledLog(it, command.targetHost) }
            val prepareExit = prepareResult.exitCode ?: -1
            if (prepareExit != 0) {
                val summary = when (prepareExit) {
                    21 -> "Remote target exists but is not a directory"
                    22 -> "Remote target directory is missing"
                    23 -> "Remote target is non-empty and unmarked"
                    else -> "Remote target safety check failed with exit $prepareExit"
                }
                val failureCategory = when (prepareExit) {
                    21 -> "remote_not_directory"
                    22 -> "remote_missing"
                    23 -> "remote_nonempty_unmarked"
                    else -> "remote_safety_failed"
                }
                trackBackupPrepareFailed(
                    failureStage = "remote_safety",
                    failureCategory = failureCategory,
                    routeUsed = route,
                    exitCode = prepareExit,
                )
                return@withContext failedLog(
                    summary = summary,
                    raw = output.toString(),
                    targetHostUsed = command.targetHost,
                    routeUsed = route,
                    failureStage = "remote_safety",
                    failureCategory = failureCategory,
                )
            }

            if (profile.dryRunBeforeBackup) {
                val dryRunCommand = buildRsyncCommand(
                    profile,
                    target,
                    route,
                    nativeInstall.paths,
                    commandInputs,
                    selectedTailscaleForward,
                    dryRun = true,
                )
                val dryRunParser = RsyncOutputParser()
                updateProgress(RunProgressPhase.DRY_RUN, "Estimating transfer size")
                val dryRunStartedNanos = System.nanoTime()
                val dryRunResult = runCommand(RemoteCommand(dryRunCommand.command), commandInputs.askpassPath) { line ->
                    val progress = dryRunParser.accept(line)
                    if (dryRunParser.isProgressLine(line)) {
                        lastRsyncProgressLine = line
                    } else {
                        recordLine(line)
                    }
                    updateProgress(
                        phase = RunProgressPhase.DRY_RUN,
                        message = "Estimating transfer size",
                        progress = progress,
                        force = false,
                    )
                }
                val dryRunElapsedMillis = (System.nanoTime() - dryRunStartedNanos) / 1_000_000L
                recordLine("Dry run elapsed: $dryRunElapsedMillis ms")
                dryRunResult.stopReason?.let { return@withContext cancelledLog(it, command.targetHost) }
                val dryRunExit = dryRunResult.exitCode ?: -1
                if (dryRunExit != 0) {
                    val summary = "Dry run failed with rsync exit $dryRunExit"
                    return@withContext failedLog(
                        summary = summary,
                        raw = finalRaw(summary),
                        targetHostUsed = command.targetHost,
                        routeUsed = route,
                        failureStage = "dry_run",
                        failureCategory = "rsync_exit_nonzero",
                        rsyncExitCode = dryRunExit,
                    )
                }
                val dryRunProgress = dryRunParser.snapshot()
                val plannedBytes = dryRunProgress.bytesTransferredRaw
                if (plannedBytes == null) {
                    val summary = "Dry run did not report transfer size"
                    return@withContext failedLog(
                        summary = summary,
                        raw = finalRaw(summary),
                        targetHostUsed = command.targetHost,
                        routeUsed = route,
                        failureStage = "dry_run",
                        failureCategory = "missing_transfer_size",
                    )
                }
                diagnostics?.trackEvent(
                    "backup_dry_run_finished",
                    backupDiagnosticsAttributes(route) + mapOf(
                        DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS to dryRunElapsedMillis,
                        DiagnosticsAttributes.BACKUP_CHANGED_FILES to dryRunProgress.filesTransferred,
                        DiagnosticsAttributes.BACKUP_CHANGED_BYTES to plannedBytes,
                        DiagnosticsAttributes.RSYNC_EXIT_CODE to dryRunExit,
                    ),
                )
                plannedTransferBytesRaw = plannedBytes
                recordLine("Dry run planned transfer: $plannedBytes bytes")
                lastRsyncProgressLine = null
                if (plannedBytes == 0L) {
                    return@withContext completeRun(
                        status = RunStatus.SUCCESS,
                        exitCode = 0,
                        summary = "Backup completed: no data to transfer",
                        progress = dryRunProgress.copy(
                            progressPercent = 100,
                            bytesTransferred = "0 bytes",
                            bytesTransferredRaw = 0L,
                        ),
                    )
                }
            }

            val parser = RsyncOutputParser()
            updateProgress(RunProgressPhase.RUNNING_RSYNC, "Running rsync")
            val rsyncResult = runCommand(RemoteCommand(command.command), commandInputs.askpassPath) { line ->
                val progress = parser.accept(line)
                if (parser.isProgressLine(line)) {
                    lastRsyncProgressLine = line
                } else {
                    recordLine(line)
                }
                updateProgress(
                    phase = RunProgressPhase.RUNNING_RSYNC,
                    message = "Running rsync",
                    progress = progress,
                    force = false,
                )
            }
            rsyncResult.stopReason?.let { return@withContext cancelledLog(it, command.targetHost) }
            val exitCode = rsyncResult.exitCode ?: -1
            val status = when (exitCode) {
                0 -> RunStatus.SUCCESS
                24 -> RunStatus.WARNING
                else -> RunStatus.FAILED
            }
            val summary = when (status) {
                RunStatus.SUCCESS -> "Backup completed"
                RunStatus.WARNING -> "Backup completed with accepted rsync warning 24"
                else -> "Backup failed with rsync exit $exitCode"
            }
            completeRun(status, exitCode, summary, parser.snapshot())
        } finally {
            selectedTailscaleForward?.close()
            restoredTailscaleStateAlias?.let { alias ->
                runCatching { tailscaleManager.persistState(alias) }
                tailscaleManager.clearPlainState()
            }
        }
    }

    private fun runCommand(
        remoteCommand: RemoteCommand,
        askpassPath: String? = null,
        onLine: (String) -> Unit,
    ): CommandRunResult =
        processController.run(
            command = remoteCommand.command,
            directory = context.filesDir,
            stdin = remoteCommand.stdin,
            configure = { processBuilder ->
                NativeBinaryManager.configureProcessEnvironment(processBuilder, context.filesDir)
                askpassPath?.let {
                    val env = processBuilder.environment()
                    env["SSH_ASKPASS"] = it
                    env["SSH_ASKPASS_REQUIRE"] = "force"
                    env["DISPLAY"] = ":0"
                }
            },
            onLine = onLine,
        )

    private data class BackupCommandInputs(
        val excludesPath: String,
        val knownHostsPath: String,
        val sshKeyPath: String,
        val tailscaleStateDir: String,
        val askpassPath: String?,
    )

    private fun writeCommandInputs(
        profile: com.ttv20.rsyncbackup.model.BackupProfile,
        privateKeyBytes: ByteArray,
        passphraseBytes: ByteArray?,
        knownHostsText: String,
    ): BackupCommandInputs {
        val runDir = File(context.filesDir, "run/${profile.id}").also { it.mkdirs() }
        val excludes = File(runDir, "excludes").also { it.writeText(profile.excludes.trimEnd() + "\n") }
        val knownHosts = File(runDir, "known_hosts").also { it.writeText(knownHostsText) }
        val sshKey = File(runDir, "id_ed25519").also {
            it.writeText(SshRuntimeFiles.privateKeyText(privateKeyBytes))
            it.privateFilePermissions()
        }
        val askpass = passphraseBytes?.let { bytes ->
            val passphrase = File(runDir, "id_ed25519.passphrase").also {
                it.writeBytes(bytes)
                it.privateFilePermissions()
            }
            File(runDir, "ssh-askpass").also {
                it.writeText("#!/system/bin/sh\ncat ${RsyncCommandBuilder.shellQuote(passphrase.absolutePath)}\n")
                it.privateFilePermissions(executable = true)
            }
        }
        val tailscaleState = File(context.filesDir, "tailscale-state").also { it.mkdirs() }
        return BackupCommandInputs(
            excludesPath = excludes.absolutePath,
            knownHostsPath = knownHosts.absolutePath,
            sshKeyPath = sshKey.absolutePath,
            tailscaleStateDir = tailscaleState.absolutePath,
            askpassPath = askpass?.absolutePath,
        )
    }

    private fun File.privateFilePermissions(executable: Boolean = false) {
        setReadable(false, false)
        setWritable(false, false)
        setExecutable(false, false)
        setReadable(true, true)
        setWritable(true, true)
        if (executable) setExecutable(true, true)
    }

    private fun buildRsyncCommand(
        profile: com.ttv20.rsyncbackup.model.BackupProfile,
        target: com.ttv20.rsyncbackup.model.TargetRecord,
        route: Route,
        binaryPaths: BinaryPaths,
        inputs: BackupCommandInputs,
        forward: TailscaleTcpForward?,
        dryRun: Boolean = false,
    ): RsyncCommand {
        return RsyncCommandBuilder.build(
            profile = profile,
            target = target,
            route = route,
            binaryPaths = binaryPaths,
            sshKeyPath = inputs.sshKeyPath,
            knownHostsPath = inputs.knownHostsPath,
            excludesPath = inputs.excludesPath,
            tailscaleStateDir = inputs.tailscaleStateDir,
            tailscaleNodeName = repository.state.value.tailscale.nodeName,
            usesAskpass = inputs.askpassPath != null,
            connectHostOverride = forward?.host,
            connectPortOverride = forward?.port,
            hostKeyAlias = forward?.targetHost,
            dryRun = dryRun,
        )
    }

    private companion object {
        const val RECENT_OUTPUT_LIMIT = 50
    }
}
