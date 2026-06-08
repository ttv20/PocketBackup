package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.ShellArgs
import com.ttv20.rsyncbackup.model.routeOrder

data class BinaryPaths(
    val rsync: String,
    val ssh: String,
    val tsnetHelper: String,
)

data class RsyncCommand(
    val command: List<String>,
    val preview: String,
    val targetHost: String,
    val route: Route,
)

object RsyncCommandBuilder {
    private val baseOptions = listOf(
        "-rt",
        "--links",
        "--filter=P .android-rsync-backup-root",
        "--filter=P .backup-status.json",
        "--filter=P .backup-last.log",
        "--partial",
        "--partial-dir=.rsync-partial",
        "--modify-window=2",
        "--compress",
        "--compress-choice=zstd",
        "--compress-level=3",
        "--info=stats2,progress2",
        "--out-format=%n",
        "--outbuf=L",
    )

    private val deleteOptions = listOf(
        "--delete",
        "--delete-delay",
        "--delete-excluded",
    )

    fun firstRoute(profile: BackupProfile): Route = profile.targetMode.routeOrder().first()

    fun targetHost(target: TargetRecord, route: Route): String = when (route) {
        Route.LAN -> target.lanHost.takeIf { it.isNotBlank() } ?: error("Server-address route requires a server address")
        Route.TAILSCALE -> target.tailscaleHost?.takeIf { it.isNotBlank() } ?: error(
            "Tailscale route requires a Tailscale device"
        )
    }

    fun buildSshCommand(
        target: TargetRecord,
        route: Route,
        binaryPaths: BinaryPaths,
        sshKeyPath: String,
        knownHostsPath: String,
        tailscaleStateDir: String,
        tailscaleNodeName: String,
        usesAskpass: Boolean = false,
        connectHostOverride: String? = null,
        connectPortOverride: Int? = null,
        hostKeyAlias: String? = null,
    ): List<String> =
        buildSshArgs(
            target = target,
            route = route,
            binaryPaths = binaryPaths,
            sshKeyPath = sshKeyPath,
            knownHostsPath = knownHostsPath,
            tailscaleStateDir = tailscaleStateDir,
            tailscaleNodeName = tailscaleNodeName,
            usesAskpass = usesAskpass,
            connectPortOverride = connectPortOverride,
            hostKeyAlias = hostKeyAlias,
        ) + "${target.user}@${connectHostOverride ?: targetHost(target, route)}"

    fun build(
        profile: BackupProfile,
        target: TargetRecord,
        route: Route,
        binaryPaths: BinaryPaths,
        sshKeyPath: String,
        knownHostsPath: String,
        excludesPath: String,
        tailscaleStateDir: String,
        tailscaleNodeName: String,
        usesAskpass: Boolean = false,
        connectHostOverride: String? = null,
        connectPortOverride: Int? = null,
        hostKeyAlias: String? = null,
        dryRun: Boolean = false,
    ): RsyncCommand {
        val targetHost = targetHost(target, route)
        val connectHost = connectHostOverride ?: targetHost

        val sshArgs = buildSshArgs(
            target = target,
            route = route,
            binaryPaths = binaryPaths,
            sshKeyPath = sshKeyPath,
            knownHostsPath = knownHostsPath,
            tailscaleStateDir = tailscaleStateDir,
            tailscaleNodeName = tailscaleNodeName,
            usesAskpass = usesAskpass,
            connectPortOverride = connectPortOverride,
            hostKeyAlias = hostKeyAlias,
        )
        val args = mutableListOf(binaryPaths.rsync)
        args += baseOptions
        if (profile.deleteEnabled) args += deleteOptions
        args += ShellArgs.split(profile.advancedArgs)
        if (dryRun) args += "--dry-run"
        args += "-e"
        args += sshArgs.joinToString(" ") { shellQuote(it) }
        args += "--exclude-from=$excludesPath"
        args += ensureTrailingSlash(profile.sourcePath)
        args += "${target.user}@$connectHost:${ensureTrailingSlash(profile.remotePath)}"

        return RsyncCommand(
            command = args,
            preview = args.joinToString(" ") { shellQuote(it) },
            targetHost = targetHost,
            route = route,
        )
    }

    private fun buildSshArgs(
        target: TargetRecord,
        route: Route,
        binaryPaths: BinaryPaths,
        sshKeyPath: String,
        knownHostsPath: String,
        tailscaleStateDir: String,
        tailscaleNodeName: String,
        usesAskpass: Boolean,
        connectPortOverride: Int?,
        hostKeyAlias: String?,
    ): List<String> {
        val args = mutableListOf(
            binaryPaths.ssh,
            "-F",
            "/dev/null",
            "-p",
            (connectPortOverride ?: target.port).toString(),
            "-i",
            sshKeyPath,
            "-o",
            "ConnectTimeout=10",
            "-o",
            "ServerAliveInterval=15",
            "-o",
            "ServerAliveCountMax=2",
            "-o",
            "StrictHostKeyChecking=yes",
            "-o",
            "UserKnownHostsFile=$knownHostsPath",
            "-o",
            "IdentitiesOnly=yes",
            "-o",
            "PubkeyAuthentication=yes",
            "-o",
            "PasswordAuthentication=no",
            "-o",
            "KbdInteractiveAuthentication=no",
        )
        if (usesAskpass) {
            args += "-o"
            args += "BatchMode=no"
        } else {
            args += "-o"
            args += "BatchMode=yes"
        }
        hostKeyAlias?.let {
            args += "-o"
            args += "HostKeyAlias=$it"
        }
        return args
    }

    private fun ensureTrailingSlash(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    fun shellQuote(value: String): String {
        if (value.isEmpty()) return "''"
        if (value.all { it.isLetterOrDigit() || it in setOf('/', '.', '_', '-', '=', ':', ',', '+', '@', '%') }) {
            return value
        }
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
