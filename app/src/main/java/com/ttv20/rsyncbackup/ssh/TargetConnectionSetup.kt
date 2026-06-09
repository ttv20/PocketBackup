package com.ttv20.rsyncbackup.ssh

import android.content.Context
import com.ttv20.rsyncbackup.backup.BackupProcessController
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.backup.RemoteTargetCommands
import com.ttv20.rsyncbackup.backup.SshConnection
import com.ttv20.rsyncbackup.backup.SshRuntimeFiles
import com.ttv20.rsyncbackup.backup.TailscaleTcpForward
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.TrustedHostFingerprint
import com.ttv20.rsyncbackup.storage.SecretStore
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import java.io.File
import java.time.Instant
import java.util.UUID

data class TargetSetupRoute(
    val route: Route,
    val host: String,
)

sealed class TargetConnectResult {
    data class Authorized(
        val target: TargetRecord,
        val route: TargetSetupRoute,
        val trustedHostFingerprints: List<TrustedHostFingerprint>,
        val message: String,
    ) : TargetConnectResult()

    data class NeedsPassword(
        val target: TargetRecord,
        val route: TargetSetupRoute,
        val trustedHostFingerprints: List<TrustedHostFingerprint>,
        val fingerprintText: String,
        val message: String,
    ) : TargetConnectResult()

    data class Failed(
        val message: String,
        val failureCategory: String,
    ) : TargetConnectResult()
}

class TargetConnectionSetup(
    private val context: Context,
    private val secretStore: SecretStore,
    private val nativeBinaryManager: NativeBinaryManager = NativeBinaryManager(context),
) {
    fun preferredRoute(target: TargetRecord): TargetSetupRoute? =
        when {
            target.lanHost.isNotBlank() -> TargetSetupRoute(Route.LAN, target.lanHost.trim())
            !target.tailscaleHost.isNullOrBlank() -> TargetSetupRoute(Route.TAILSCALE, target.tailscaleHost.trim())
            else -> null
        }

    fun connect(
        state: AppState,
        target: TargetRecord,
        sshKeySettings: GlobalSshKeySettings,
    ): TargetConnectResult {
        val route = preferredRoute(target) ?: return TargetConnectResult.Failed(
            message = "Enter a server address or Tailscale device.",
            failureCategory = "missing_address",
        )
        val scannedKeys = runCatching { scanHostKeys(state, target, route) }
            .getOrElse { error ->
                return TargetConnectResult.Failed(
                    message = error.message ?: "Could not reach SSH server",
                    failureCategory = "host_key_scan_failed",
                )
            }
        if (scannedKeys.isEmpty()) {
            return TargetConnectResult.Failed(
                message = "No SSH host key was returned from ${route.host}:${target.port}.",
                failureCategory = "no_host_key",
            )
        }

        val trusted = mergeTrustedHostKeys(
            state.trustedHostFingerprints,
            target,
            scannedKeys,
        )
        val updatedTarget = target.copy(keyOnlyLoginVerifiedAt = null)
        val keyAuth = runCatching {
            testKeyAuth(state, updatedTarget, route, sshKeySettings, trusted)
        }.getOrElse { error ->
            return TargetConnectResult.Failed(
                message = error.message ?: "SSH connection failed",
                failureCategory = "key_auth_failed",
            )
        }

        return if (keyAuth.exitCode == 0) {
            TargetConnectResult.Authorized(
                target = updatedTarget.copy(keyOnlyLoginVerifiedAt = Instant.now().toString()),
                route = route,
                trustedHostFingerprints = trusted,
                message = "Connected with SSH key",
            )
        } else {
            TargetConnectResult.NeedsPassword(
                target = updatedTarget,
                route = route,
                trustedHostFingerprints = trusted,
                fingerprintText = scannedKeys.joinToString("\n") { "${it.algorithm} ${it.fingerprint}" },
                message = keyAuth.output.ifBlank { "SSH key is not authorized yet" },
            )
        }
    }

    fun installPublicKey(
        state: AppState,
        target: TargetRecord,
        route: TargetSetupRoute,
        trustedHostFingerprints: List<TrustedHostFingerprint>,
        publicKey: String,
        password: String,
    ): SshPasswordSetupResult =
        when (route.route) {
            Route.LAN -> SshPasswordSetupClient().installPublicKey(
                target = target,
                trustedHostFingerprints = trustedHostFingerprints,
                publicKey = publicKey,
                password = password,
                workDir = context.cacheDir,
                hostname = route.host,
            )
            Route.TAILSCALE -> {
                require(state.tailscale.isConfigured && state.tailscale.stateSecretAlias != null) {
                    "Tailscale is not connected."
                }
                TailscaleManager(context, secretStore, nativeBinaryManager).withRestoredState(state.tailscale.stateSecretAlias) { stateDir ->
                    val nativeInstall = nativeBinaryManager.ensureInstalled()
                    require(nativeInstall.isComplete) {
                        "Missing native binaries: ${nativeInstall.missing.joinToString()}"
                    }
                    SshPasswordSetupClient().installPublicKeyWithNativeSsh(
                        target = target,
                        trustedHostFingerprints = trustedHostFingerprints,
                        publicKey = publicKey,
                        password = password,
                        workDir = context.cacheDir,
                        filesDir = context.filesDir,
                        tsnetHelperPath = nativeInstall.paths.tsnetHelper,
                        tailscaleStateDir = stateDir,
                        tailscaleNodeName = state.tailscale.nodeName,
                        hostname = route.host,
                    )
                }
            }
        }

    private fun scanHostKeys(
        state: AppState,
        target: TargetRecord,
        route: TargetSetupRoute,
    ): List<ScannedHostKey> =
        when (route.route) {
            Route.LAN -> SshHostKeyScanner(context).scanAll(route.host, target.port)
            Route.TAILSCALE -> {
                require(state.tailscale.isConfigured && state.tailscale.stateSecretAlias != null) {
                    "Tailscale is not connected."
                }
                TailscaleManager(context, secretStore, nativeBinaryManager).withRestoredState(state.tailscale.stateSecretAlias) { stateDir ->
                    SshHostKeyScanner(context).scanAllOverTailscale(
                        hostname = route.host,
                        port = target.port,
                        user = target.user,
                        tailscaleStateDir = stateDir,
                        tailscaleNodeName = state.tailscale.nodeName,
                    )
                }
            }
        }

    private fun testKeyAuth(
        state: AppState,
        target: TargetRecord,
        route: TargetSetupRoute,
        sshKeySettings: GlobalSshKeySettings,
        trustedHostFingerprints: List<TrustedHostFingerprint>,
    ): CommandOutput {
        val privateKeyAlias = requireNotNull(sshKeySettings.privateKeySecretAlias) { "No SSH private key is configured." }
        val privateKeyBytes = requireNotNull(secretStore.get(privateKeyAlias)) { "SSH private key is missing." }
        val passphraseBytes = sshKeySettings.passphraseSecretAlias?.let { alias ->
            requireNotNull(secretStore.get(alias)) { "SSH private key passphrase is missing." }
        }
        val nativeInstall = nativeBinaryManager.ensureInstalled()
        require(nativeInstall.isComplete) {
            "Missing native binaries: ${nativeInstall.missing.joinToString()}"
        }
        val knownHostsText = SshRuntimeFiles.knownHostsText(target, trustedHostFingerprints)
        require(knownHostsText.isNotBlank()) { "No trusted SSH host key is configured." }
        val runDir = File(context.cacheDir, "target-connect-${System.nanoTime()}").also { it.mkdirs() }
        var forward: TailscaleTcpForward? = null
        return try {
            val knownHosts = File(runDir, "known_hosts").also {
                it.writeText(knownHostsText)
                it.privateFilePermissions()
            }
            val privateKey = File(runDir, "id_ed25519").also {
                it.writeText(SshRuntimeFiles.privateKeyText(privateKeyBytes))
                it.privateFilePermissions()
            }
            val askpass = passphraseBytes?.let { bytes ->
                val passphrase = File(runDir, "id_ed25519.passphrase").also {
                    it.writeBytes(bytes)
                    it.privateFilePermissions()
                }
                File(runDir, "ssh-askpass").also {
                    it.writeText("#!/system/bin/sh\ncat '${passphrase.absolutePath.replace("'", "'\\''")}'\n")
                    it.privateFilePermissions(executable = true)
                }
            }
            val tailscaleStateDir = File(context.filesDir, "tailscale-state").also { it.mkdirs() }
            if (route.route == Route.TAILSCALE) {
                val stateAlias = requireNotNull(state.tailscale.stateSecretAlias) { "Tailscale state is not configured." }
                TailscaleManager(context, secretStore, nativeBinaryManager).restoreState(stateAlias)
                forward = TailscaleTcpForward.start(
                    tsnetHelperPath = nativeInstall.paths.tsnetHelper,
                    filesDir = context.filesDir,
                    tailscaleStateDir = tailscaleStateDir,
                    tailscaleNodeName = state.tailscale.nodeName,
                    targetHost = route.host,
                    targetPort = target.port,
                )
            }
            val connection = SshConnection(
                target = target,
                route = route.route,
                binaryPaths = nativeInstall.paths,
                sshKeyPath = privateKey.absolutePath,
                knownHostsPath = knownHosts.absolutePath,
                tailscaleStateDir = tailscaleStateDir.absolutePath,
                tailscaleNodeName = state.tailscale.nodeName,
                usesAskpass = askpass != null,
                connectHostOverride = forward?.host,
                connectPortOverride = forward?.port,
                hostKeyAlias = forward?.targetHost,
            )
            runCommand(RemoteTargetCommands.connectivityTest(connection), askpass?.absolutePath)
        } finally {
            forward?.close()
            if (route.route == Route.TAILSCALE) {
                val manager = TailscaleManager(context, secretStore, nativeBinaryManager)
                state.tailscale.stateSecretAlias?.let { alias ->
                    runCatching { manager.persistState(alias) }
                }
                manager.clearPlainState()
            }
            runDir.deleteRecursively()
        }
    }

    private fun runCommand(command: com.ttv20.rsyncbackup.backup.RemoteCommand, askpassPath: String?): CommandOutput {
        val output = StringBuilder()
        val result = BackupProcessController().run(
            command = command.command,
            directory = context.filesDir,
            stdin = command.stdin,
            configure = { processBuilder ->
                NativeBinaryManager.configureProcessEnvironment(processBuilder, context.filesDir)
                askpassPath?.let {
                    val env = processBuilder.environment()
                    env["SSH_ASKPASS"] = it
                    env["SSH_ASKPASS_REQUIRE"] = "force"
                    env["DISPLAY"] = ":0"
                }
            },
            onLine = { output.appendLine(it) },
        )
        return CommandOutput(result.exitCode ?: -1, output.toString().trim())
    }

    private fun mergeTrustedHostKeys(
        current: List<TrustedHostFingerprint>,
        target: TargetRecord,
        scannedKeys: List<ScannedHostKey>,
    ): List<TrustedHostFingerprint> {
        val hostnames = configuredHostnames(target)
        val targetIds = setOf(target.id, target.fingerprintGroupId)
        val additions = scannedKeys.map { scanned ->
            TrustedHostFingerprint(
                id = UUID.randomUUID().toString(),
                targetId = target.fingerprintGroupId,
                hostnames = hostnames.ifEmpty { listOf(scanned.hostname) },
                port = scanned.port,
                algorithm = scanned.algorithm,
                fingerprint = scanned.fingerprint,
                publicKey = scanned.publicKey,
                confirmedAt = Instant.now().toString(),
            )
        }
        val filtered = current.filterNot { existing ->
            existing.targetId in targetIds && additions.any { added ->
                existing.port == added.port && existing.algorithm == added.algorithm
            }
        }
        return filtered + additions
    }

    private fun configuredHostnames(target: TargetRecord): List<String> =
        listOfNotNull(
            target.lanHost.trim().takeIf { it.isNotBlank() },
            target.tailscaleHost?.trim()?.takeIf { it.isNotBlank() },
        ).distinct()

    private data class CommandOutput(
        val exitCode: Int,
        val output: String,
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
