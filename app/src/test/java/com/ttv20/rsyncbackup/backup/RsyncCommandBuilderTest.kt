package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsyncCommandBuilderTest {
    private val target = TargetRecord(
        id = "target",
        name = "Home",
        user = "ttv20",
        lanHost = "192.168.3.200",
        tailscaleHost = "bardugo-home-ts",
        defaultRemotePath = "/mnt/backup/phone",
    )
    private val binaries = BinaryPaths("rsync", "ssh", "tsnet-nc")

    @Test
    fun deleteToggleControlsDeleteArgs() {
        val deleteCommand = command(profile(deleteEnabled = true), Route.LAN)
        val noDeleteCommand = command(profile(deleteEnabled = false), Route.LAN)

        assertTrue(deleteCommand.command.contains("--delete"))
        assertTrue(deleteCommand.command.contains("--delete-delay"))
        assertFalse(noDeleteCommand.command.contains("--delete"))
        assertFalse(noDeleteCommand.command.contains("--delete-delay"))
    }

    @Test
    fun advancedArgsAreIncludedInPreview() {
        val command = command(profile(advancedArgs = "--max-size '10 M' --dry-run"), Route.LAN)

        assertTrue(command.command.contains("--max-size"))
        assertTrue(command.command.contains("10 M"))
        assertTrue(command.command.contains("--dry-run"))
        assertTrue(command.preview.contains("'10 M'"))
    }

    @Test
    fun dryRunFlagAddsDryRunArg() {
        val command = RsyncCommandBuilder.build(
            profile = profile(advancedArgs = "--max-size 10M"),
            target = target,
            route = Route.LAN,
            binaryPaths = binaries,
            sshKeyPath = "/files/id_ed25519",
            knownHostsPath = "/files/known_hosts",
            excludesPath = "/files/excludes",
            tailscaleStateDir = "/files/tailscale",
            tailscaleNodeName = "phone-rsync",
            dryRun = true,
        )

        assertTrue(command.command.contains("--dry-run"))
        assertTrue(command.command.lastIndexOf("--dry-run") > command.command.indexOf("--max-size"))
    }

    @Test
    fun tailscaleRouteUsesForwardedEndpointAndHostKeyAlias() {
        val command = RsyncCommandBuilder.build(
            profile = profile(targetMode = TargetMode.TAILSCALE_ONLY),
            target = target,
            route = Route.TAILSCALE,
            binaryPaths = binaries,
            sshKeyPath = "/files/id_ed25519",
            knownHostsPath = "/files/known_hosts",
            excludesPath = "/files/excludes",
            tailscaleStateDir = "/files/tailscale",
            tailscaleNodeName = "phone-rsync",
            connectHostOverride = "127.0.0.1",
            connectPortOverride = 41234,
            hostKeyAlias = "bardugo-home-ts",
        )

        assertEquals("bardugo-home-ts", command.targetHost)
        assertFalse(command.preview.contains("ProxyCommand"))
        assertTrue(command.preview.contains("HostKeyAlias=bardugo-home-ts"))
        assertTrue(command.preview.contains("-p 41234"))
        assertTrue(command.command.any { it.contains("ttv20@127.0.0.1:") })
    }

    @Test
    fun sshIgnoresBundledPackageConfigPath() {
        val command = command(profile(), Route.LAN)

        assertTrue(command.preview.contains("-F /dev/null"))
    }

    @Test
    fun plainKeysUseBatchMode() {
        val command = command(profile(), Route.LAN)

        assertTrue(command.command.any { it.contains("BatchMode=yes") })
        assertFalse(command.command.any { it.contains("BatchMode=no") })
        assertTrue(command.command.any { it.contains("PasswordAuthentication=no") })
    }

    @Test
    fun askpassKeysAllowPassphrasePromptButDisablePasswordAuth() {
        val command = RsyncCommandBuilder.build(
            profile = profile(),
            target = target,
            route = Route.LAN,
            binaryPaths = binaries,
            sshKeyPath = "/files/id_ed25519",
            knownHostsPath = "/files/known_hosts",
            excludesPath = "/files/excludes",
            tailscaleStateDir = "/files/tailscale",
            tailscaleNodeName = "phone-rsync",
            usesAskpass = true,
        )

        assertTrue(command.command.any { it.contains("BatchMode=no") })
        assertFalse(command.command.any { it.contains("BatchMode=yes") })
        assertTrue(command.command.any { it.contains("PasswordAuthentication=no") })
        assertTrue(command.command.any { it.contains("KbdInteractiveAuthentication=no") })
    }

    @Test
    fun protectsAppMetadataFilesFromDelete() {
        val command = command(profile(deleteEnabled = true), Route.LAN)

        assertTrue(command.command.contains("--filter=P .android-rsync-backup-root"))
        assertTrue(command.command.contains("--filter=P .backup-status.json"))
        assertTrue(command.command.contains("--filter=P .backup-last.log"))
    }

    @Test
    fun emitsFileNamesForLiveProgress() {
        val command = command(profile(), Route.LAN)

        assertTrue(command.command.contains("--out-format=%n"))
    }

    @Test
    fun buildSshCommandTargetsSelectedRoute() {
        val command = RsyncCommandBuilder.buildSshCommand(
            target = target,
            route = Route.LAN,
            binaryPaths = binaries,
            sshKeyPath = "/files/id_ed25519",
            knownHostsPath = "/files/known_hosts",
            tailscaleStateDir = "/files/tailscale",
            tailscaleNodeName = "phone-rsync",
        )

        assertEquals("ttv20@192.168.3.200", command.last())
        assertTrue(command.containsAll(listOf("ssh", "-F", "/dev/null")))
    }

    private fun command(profile: BackupProfile, route: Route): RsyncCommand =
        RsyncCommandBuilder.build(
            profile = profile,
            target = target,
            route = route,
            binaryPaths = binaries,
            sshKeyPath = "/files/id_ed25519",
            knownHostsPath = "/files/known_hosts",
            excludesPath = "/files/excludes",
            tailscaleStateDir = "/files/tailscale",
            tailscaleNodeName = "phone-rsync",
        )

    private fun profile(
        deleteEnabled: Boolean = true,
        advancedArgs: String = "",
        targetMode: TargetMode = TargetMode.LAN_ONLY,
    ) = BackupProfile(
        id = "profile",
        name = "Phone",
        targetId = "target",
        remotePath = "/mnt/backup/phone",
        targetMode = targetMode,
        deleteEnabled = deleteEnabled,
        excludes = "cache/\n",
        advancedArgs = advancedArgs,
    )
}
