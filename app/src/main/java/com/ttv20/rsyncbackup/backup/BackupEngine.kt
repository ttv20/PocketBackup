package com.ttv20.rsyncbackup.backup

import android.content.Context
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupStatusMarker
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.requiresTailscale
import com.ttv20.rsyncbackup.model.routeOrder
import com.ttv20.rsyncbackup.model.toJson
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

class BackupEngine(
    private val context: Context,
    private val repository: AppRepository,
    private val secretStore: SecretStore,
    private val nativeBinaryManager: NativeBinaryManager = NativeBinaryManager(context),
    private val processController: BackupProcessController = BackupProcessController(),
) {
    suspend fun runProfile(profileId: String): BackupLog = withContext(Dispatchers.IO) {
        val state = repository.state.value
        val profile = state.profiles.first { it.id == profileId }
        val server = state.servers.first { it.id == profile.serverId }
        val startedAt = Instant.now().toString()
        val recentOutput = mutableListOf<String>()
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

        fun failedLog(
            summary: String,
            raw: String = "",
            targetHostUsed: String? = null,
        ): BackupLog {
            val finishedAt = Instant.now().toString()
            val log = BackupLog(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                profileName = profile.name,
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = RunStatus.FAILED,
                targetHostUsed = targetHostUsed,
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
                return@withContext failedLog("Missing native binaries: ${nativeInstall.missing.joinToString()}")
            }

            if (profile.targetMode.requiresTailscale()) {
                val stateAlias = state.tailscale.stateSecretAlias
                if (!state.tailscale.isConfigured || stateAlias == null) {
                    return@withContext failedLog("Tailscale is not configured for ${profile.targetMode}")
                }
                runCatching { tailscaleManager.restoreState(stateAlias) }
                    .onFailure { error ->
                        return@withContext failedLog("Tailscale state restore failed: ${error.message}")
                    }
                restoredTailscaleStateAlias = stateAlias
            }

            val privateKeyAlias = state.sshKeySettings.privateKeySecretAlias
            val privateKeyBytes = privateKeyAlias?.let(secretStore::get)
            if (privateKeyBytes == null) {
                return@withContext failedLog("No SSH private key is configured")
            }
            val passphraseBytes = state.sshKeySettings.passphraseSecretAlias?.let { alias ->
                secretStore.get(alias) ?: return@withContext failedLog("SSH private key passphrase is missing")
            }
            val knownHostsText = SshRuntimeFiles.knownHostsText(server, state.trustedHostFingerprints)
            if (knownHostsText.isBlank()) {
                return@withContext failedLog("No trusted SSH host key is configured for ${server.name}")
            }
            val commandInputs = writeCommandInputs(profile, privateKeyBytes, passphraseBytes, knownHostsText)
            val output = StringBuilder()
            var lastRsyncProgressLine: String? = null

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
            ) {
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
                        progressPercent = progress.progressPercent,
                        bytesTransferred = progress.bytesTransferred,
                        bytesTransferredRaw = progress.bytesTransferredRaw,
                        speed = progress.speed,
                        averageBytesPerSecond = progress.averageBytesPerSecond,
                        recentAverageBytesPerSecond = progress.averageBytesPerSecond,
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
                    targetHostUsed = targetHostUsed,
                    summary = summary,
                    raw = finalRaw(summary),
                )
                updateProgress(RunProgressPhase.CANCELLED, summary, persist = true)
                repository.appendLog(log)
                repository.markProfile(profile.id, RunStatus.CANCELLED, summary, finishedAt)
                return log
            }

            fun routeConnection(route: Route, forward: TailscaleTcpForward? = null) = SshConnection(
                    server = server,
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

            var selectedRoute: Route? = null
            var lastRouteFailure: String? = null
            for (candidate in profile.targetMode.routeOrder()) {
                val hostResult = runCatching { RsyncCommandBuilder.targetHost(server, candidate) }
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
                            targetPort = server.port,
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
                summary = "No usable route for ${server.name}: ${lastRouteFailure ?: "no routes configured"}",
                raw = output.toString(),
            )
            val connection = routeConnection(route, selectedTailscaleForward)
            val command = buildRsyncCommand(profile, server, route, nativeInstall.paths, commandInputs, selectedTailscaleForward)

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
            val log = BackupLog(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                profileName = profile.name,
                startedAt = startedAt,
                finishedAt = Instant.now().toString(),
                status = RunStatus.FAILED,
                targetHostUsed = command.targetHost,
                summary = summary,
                raw = output.toString(),
            )
            updateProgress(RunProgressPhase.FAILED, summary, persist = true)
            repository.appendLog(log)
            repository.markProfile(profile.id, RunStatus.FAILED, summary)
            return@withContext log
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
        val finishedAt = Instant.now().toString()

        if (status == RunStatus.SUCCESS || status == RunStatus.WARNING) {
            updateProgress(RunProgressPhase.UPLOADING_STATUS, "Uploading backup status", parser.snapshot())
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
                updateProgress(RunProgressPhase.UPLOADING_STATUS, "Uploading backup status", parser.snapshot())
            }
            uploadStatusResult.stopReason?.let { return@withContext cancelledLog(it, command.targetHost) }
            if ((uploadStatusResult.exitCode ?: -1) != 0) {
                return@withContext failedLog(
                    summary = "Backup completed but status upload failed with exit ${uploadStatusResult.exitCode ?: -1}",
                    raw = output.toString(),
                    targetHostUsed = command.targetHost,
                )
            }
            val uploadLogResult = runCommand(
                RemoteTargetCommands.uploadLastLog(profile, connection, finalRaw(summary)),
                commandInputs.askpassPath,
            ) { line ->
                recordLine(line)
                updateProgress(RunProgressPhase.UPLOADING_STATUS, "Uploading backup log", parser.snapshot())
            }
            uploadLogResult.stopReason?.let { return@withContext cancelledLog(it, command.targetHost) }
            if ((uploadLogResult.exitCode ?: -1) != 0) {
                return@withContext failedLog(
                    summary = "Backup completed but log upload failed with exit ${uploadLogResult.exitCode ?: -1}",
                    raw = output.toString(),
                    targetHostUsed = command.targetHost,
                )
            }
        }

        val log = BackupLog(
            id = UUID.randomUUID().toString(),
            profileId = profile.id,
            profileName = profile.name,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = status,
            exitCode = exitCode,
            targetHostUsed = command.targetHost,
            summary = summary,
            raw = finalRaw(summary),
        )
        updateProgress(
            phase = if (status == RunStatus.FAILED) RunProgressPhase.FAILED else RunProgressPhase.COMPLETED,
            message = summary,
            progress = parser.snapshot(),
            persist = true,
        )
        repository.appendLog(log)
        repository.markProfile(profile.id, status, summary, finishedAt)
            log
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
        server: com.ttv20.rsyncbackup.model.ServerRecord,
        route: Route,
        binaryPaths: BinaryPaths,
        inputs: BackupCommandInputs,
        forward: TailscaleTcpForward?,
    ): RsyncCommand {
        return RsyncCommandBuilder.build(
            profile = profile,
            server = server,
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
        )
    }

    private companion object {
        const val RECENT_OUTPUT_LIMIT = 50
    }
}
