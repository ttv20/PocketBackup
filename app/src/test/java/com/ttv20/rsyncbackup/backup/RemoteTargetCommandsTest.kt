package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.RemoteSafetySettings
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.TargetRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTargetCommandsTest {
    private val target = TargetRecord(
        id = "target",
        name = "Home",
        user = "ttv20",
        lanHost = "192.168.3.200",
        defaultRemotePath = "/mnt/backup/phone",
    )

    private val connection = SshConnection(
        target = target,
        route = Route.LAN,
        binaryPaths = BinaryPaths("rsync", "ssh", "tsnet-nc"),
        sshKeyPath = "/files/id_ed25519",
        knownHostsPath = "/files/known_hosts",
        tailscaleStateDir = "/files/tailscale",
        tailscaleNodeName = "phone-rsync",
    )

    @Test
    fun connectivityTestUsesKeyOnlySshNoop() {
        val command = RemoteTargetCommands.connectivityTest(connection)

        assertEquals("true", command.command.last())
        assertTrue(command.command.containsAll(listOf("ssh", "-F", "/dev/null", "-i", "/files/id_ed25519")))
        assertEquals(null, command.stdin)
    }

    @Test
    fun prepareTargetChecksMarkerBeforeDeleteCapableBackup() {
        val command = RemoteTargetCommands.prepareTarget(profile(), connection)

        assertTrue(command.command.containsAll(listOf("sh", "-s", "--", "/mnt/backup/phone")))
        assertEquals("1", command.command[command.command.lastIndex - 1])
        assertEquals("0", command.command.last())
        assertTrue(command.stdin!!.contains(".android-rsync-backup-root"))
        assertTrue(command.stdin.contains("remote target is non-empty and unmarked"))
        assertTrue(command.stdin.contains("exit 23"))
    }

    @Test
    fun prepareTargetCanRecordExplicitNonEmptyConfirmation() {
        val command = RemoteTargetCommands.prepareTarget(
            profile(
                remoteSafety = RemoteSafetySettings(
                    createDirectoryIfMissing = true,
                    allowUnmarkedNonEmptyTarget = true,
                ),
            ),
            connection,
        )

        assertEquals("1", command.command.last())
    }

    @Test
    fun uploadStatusWritesExpectedRemoteMarkerFile() {
        val command = RemoteTargetCommands.uploadStatus(profile(), connection, """{"status":"success"}""")

        assertTrue(command.command.contains(RemoteTargetCommands.STATUS_FILE))
        assertTrue(command.command.containsAll(listOf("sh", "-s", "--")))
        assertTrue(command.stdin!!.endsWith("\n"))
        assertTrue(command.stdin.contains("success"))
        assertTrue(command.stdin.contains("cat > \"\$target/\$name\""))
    }

    @Test
    fun uploadLastLogWritesExpectedRemoteLogFile() {
        val command = RemoteTargetCommands.uploadLastLog(profile(), connection, "raw log\n")

        assertTrue(command.command.contains(RemoteTargetCommands.LAST_LOG_FILE))
        assertTrue(command.stdin!!.contains("raw log\n"))
        assertTrue(command.stdin.contains("cat > \"\$target/\$name\""))
    }

    private fun profile(
        remoteSafety: RemoteSafetySettings = RemoteSafetySettings(),
    ) = BackupProfile(
        id = "profile",
        name = "Phone",
        targetId = "target",
        remotePath = "/mnt/backup/phone",
        remoteSafety = remoteSafety,
        excludes = "cache/\n",
    )
}
