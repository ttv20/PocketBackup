package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.Route
import com.ttv20.rsyncbackup.model.ServerRecord

data class SshConnection(
    val server: ServerRecord,
    val route: Route,
    val binaryPaths: BinaryPaths,
    val sshKeyPath: String,
    val knownHostsPath: String,
    val tailscaleStateDir: String,
    val tailscaleNodeName: String,
    val usesAskpass: Boolean = false,
    val connectHostOverride: String? = null,
    val connectPortOverride: Int? = null,
    val hostKeyAlias: String? = null,
)

data class RemoteCommand(
    val command: List<String>,
    val stdin: String? = null,
)

object RemoteTargetCommands {
    const val ROOT_MARKER = ".android-rsync-backup-root"
    const val STATUS_FILE = ".backup-status.json"
    const val LAST_LOG_FILE = ".backup-last.log"

    fun connectivityTest(connection: SshConnection): RemoteCommand =
        RemoteCommand(command = baseSsh(connection) + "true")

    fun prepareTarget(profile: BackupProfile, connection: SshConnection): RemoteCommand =
        RemoteCommand(
            command = baseSsh(connection) + listOf(
                "sh",
                "-s",
                "--",
                remoteArg(profile.remotePath),
                if (profile.remoteSafety.createDirectoryIfMissing) "1" else "0",
                if (profile.remoteSafety.allowUnmarkedNonEmptyTarget) "1" else "0",
            ),
            stdin = PREPARE_TARGET_SCRIPT,
        )

    fun uploadStatus(profile: BackupProfile, connection: SshConnection, json: String): RemoteCommand =
        uploadFile(profile, connection, STATUS_FILE, json.trimEnd() + "\n")

    fun uploadLastLog(profile: BackupProfile, connection: SshConnection, log: String): RemoteCommand =
        uploadFile(profile, connection, LAST_LOG_FILE, log)

    private fun uploadFile(
        profile: BackupProfile,
        connection: SshConnection,
        fileName: String,
        contents: String,
    ): RemoteCommand =
        RemoteCommand(
            command = baseSsh(connection) + listOf(
                "sh",
                "-s",
                "--",
                remoteArg(profile.remotePath),
                remoteArg(fileName),
            ),
            stdin = uploadFileScript(contents),
        )

    private fun remoteArg(value: String): String =
        RsyncCommandBuilder.shellQuote(value)

    private fun baseSsh(connection: SshConnection): List<String> =
        RsyncCommandBuilder.buildSshCommand(
            server = connection.server,
            route = connection.route,
            binaryPaths = connection.binaryPaths,
            sshKeyPath = connection.sshKeyPath,
            knownHostsPath = connection.knownHostsPath,
            tailscaleStateDir = connection.tailscaleStateDir,
            tailscaleNodeName = connection.tailscaleNodeName,
            usesAskpass = connection.usesAskpass,
            connectHostOverride = connection.connectHostOverride,
            connectPortOverride = connection.connectPortOverride,
            hostKeyAlias = connection.hostKeyAlias,
        )

    private const val PREPARE_TARGET_SCRIPT = """
set -eu
target=${'$'}1
create_missing=${'$'}2
allow_unmarked_non_empty=${'$'}3
marker="${'$'}target/.android-rsync-backup-root"

if [ -e "${'$'}target" ] && [ ! -d "${'$'}target" ]; then
  echo "remote target exists but is not a directory: ${'$'}target" >&2
  exit 21
fi

if [ ! -d "${'$'}target" ]; then
  if [ "${'$'}create_missing" != "1" ]; then
    echo "remote target directory is missing: ${'$'}target" >&2
    exit 22
  fi
  mkdir -p "${'$'}target"
fi

if [ ! -f "${'$'}marker" ]; then
  first_entry=${'$'}(
    find "${'$'}target" -mindepth 1 -maxdepth 1 \
      ! -name ".android-rsync-backup-root" \
      ! -name ".backup-status.json" \
      ! -name ".backup-last.log" \
      -print -quit
  )
  if [ -n "${'$'}first_entry" ] && [ "${'$'}allow_unmarked_non_empty" != "1" ]; then
    echo "remote target is non-empty and unmarked: ${'$'}target" >&2
    exit 23
  fi
  {
    echo "android-rsync-backup-root-v1"
    date -Iseconds 2>/dev/null || date
  } > "${'$'}marker"
  chmod 0644 "${'$'}marker"
fi
"""

    private fun uploadFileScript(contents: String): String {
        var delimiter = "__ANDROID_RSYNC_BACKUP_PAYLOAD__"
        while (contents.contains(delimiter)) {
            delimiter += "_"
        }
        return "set -eu\n" +
            "target=\$1\n" +
            "name=\$2\n" +
            "mkdir -p \"\$target\"\n" +
            "cat > \"\$target/\$name\" <<'$delimiter'\n" +
            contents.trimEnd() + "\n" +
            "$delimiter\n" +
            "chmod 0644 \"\$target/\$name\"\n"
    }
}
