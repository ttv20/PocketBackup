package com.ttv20.rsyncbackup.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.model.AppState
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.BackupQueueState
import com.ttv20.rsyncbackup.model.ConstraintSettings
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.ProfileStatus
import com.ttv20.rsyncbackup.model.RemoteSafetySettings
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.ServerRecord
import com.ttv20.rsyncbackup.model.TailscaleStateMetadata
import com.ttv20.rsyncbackup.model.TargetMode
import com.ttv20.rsyncbackup.model.TrustedHostFingerprint
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.ssh.SshPasswordSetupClient
import com.ttv20.rsyncbackup.tailscale.TailscaleManager
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Random

class DebugBackupSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SCHEDULE_CONSTRAINT_SMOKE) {
            scheduleConstraintSmoke(context, intent)
            return
        }
        if (intent.action == ACTION_TAILSCALE_FAILURE_SMOKE) {
            runTailscaleFailureSmoke(context)
            return
        }
        if (intent.action == ACTION_TAILSCALE_LIVE_SMOKE) {
            runTailscaleLiveSmoke(context, intent)
            return
        }
        if (intent.action != ACTION_RUN_BACKUP_SMOKE) return
        val app = context.applicationContext as RsyncBackupApplication
        val config = SmokeConfig.from(intent)
        val sourceDir = prepareSourceTree(context, config)
        app.secretStore.put(PRIVATE_KEY_ALIAS, config.privateKey.toByteArray(Charsets.UTF_8))
        config.privateKeyPassphrase?.let {
            app.secretStore.put(PRIVATE_KEY_PASSPHRASE_ALIAS, it.toByteArray(Charsets.UTF_8))
        }
        app.repository.update { state -> state.withSmokeConfig(config, sourceDir.absolutePath) }
        if (config.setupPassword == null) {
            startBackup(context, config.autoCancelAfterMs)
            return
        }

        val pendingResult = goAsync()
        Thread {
            runCatching {
                val state = app.repository.state.value
                val server = state.servers.first { it.id == SERVER_ID }
                val publicKey = requireNotNull(config.publicKey) {
                    "Public key is required for password setup smoke"
                }
                SshPasswordSetupClient().installPublicKey(
                    server = server,
                    trustedHostFingerprints = state.trustedHostFingerprints,
                    publicKey = publicKey,
                    password = config.setupPassword,
                    workDir = context.cacheDir,
                    hostname = config.host,
                )
            }.onSuccess { result ->
                if (result.isSuccess) {
                    startBackup(context, config.autoCancelAfterMs)
                } else {
                    app.repository.markProfile(
                        PROFILE_ID,
                        RunStatus.FAILED,
                        result.output.ifBlank { "Debug smoke password setup failed with exit ${result.exitStatus}" },
                    )
                }
            }.onFailure { error ->
                app.repository.markProfile(
                    PROFILE_ID,
                    RunStatus.FAILED,
                    "Debug smoke password setup failed: ${error.message}",
                )
            }
            pendingResult.finish()
        }.start()
    }

    private fun scheduleConstraintSmoke(context: Context, intent: Intent) {
        val app = context.applicationContext as RsyncBackupApplication
        val delayMinutes = intent.getIntExtra(EXTRA_ALARM_DELAY_MINUTES, 2).coerceAtLeast(1)
        val scheduleType = runCatching {
            ScheduleType.valueOf(intent.getStringExtra(EXTRA_SCHEDULE_TYPE) ?: ScheduleType.EXACT_DAILY.name)
        }.getOrDefault(ScheduleType.EXACT_DAILY)
        val timeLocal = LocalTime.now()
            .plusMinutes(delayMinutes.toLong())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        val now = Instant.now().toString()
        val server = ServerRecord(
            id = SCHEDULE_SERVER_ID,
            name = "Debug scheduled constraint server",
            user = "debug",
            lanHost = "127.0.0.1",
            port = 22,
            defaultRemotePath = "/tmp/debug-scheduled-constraint",
            fingerprintGroupId = SCHEDULE_SERVER_ID,
        )
        val profile = BackupProfile(
            id = SCHEDULE_PROFILE_ID,
            name = "Debug scheduled constraint backup",
            sourcePath = "/storage/emulated/0",
            serverId = SCHEDULE_SERVER_ID,
            remotePath = server.defaultRemotePath,
            targetMode = TargetMode.LAN_ONLY,
            schedule = BackupSchedule(type = scheduleType, timeLocal = timeLocal),
            constraints = ConstraintSettings(
                batteryNotLow = false,
                selectedSsidOnly = true,
                manualOverrideAllowed = true,
            ),
            remoteSafety = RemoteSafetySettings(createDirectoryIfMissing = true),
            deleteEnabled = false,
            excludes = "",
            status = ProfileStatus(lastMessage = "Scheduled constraint smoke for $timeLocal at $now"),
        )

        app.repository.update { state ->
            state.copy(
                settings = state.settings.copy(selectedSsid = "debug-ssid-that-should-not-match"),
                servers = state.servers.filterNot { it.id == SCHEDULE_SERVER_ID } + server,
                profiles = state.profiles.filterNot { it.id == SCHEDULE_PROFILE_ID } + profile,
                queue = BackupQueueState(),
                runProgress = RunProgressState(),
            )
        }
        BackupScheduler(context).schedule(profile)
    }

    private fun runTailscaleFailureSmoke(context: Context) {
        val app = context.applicationContext as RsyncBackupApplication
        val now = Instant.now().toString()
        val server = ServerRecord(
            id = TAILSCALE_FAILURE_SERVER_ID,
            name = "Debug Tailscale failure server",
            user = "debug",
            lanHost = "192.0.2.10",
            tailscaleHost = "debug-tailnet-host",
            port = 22,
            defaultRemotePath = "/tmp/debug-tailscale-failure",
            fingerprintGroupId = TAILSCALE_FAILURE_SERVER_ID,
        )
        val profile = BackupProfile(
            id = TAILSCALE_FAILURE_PROFILE_ID,
            name = "Debug Tailscale failure backup",
            sourcePath = "/storage/emulated/0",
            serverId = TAILSCALE_FAILURE_SERVER_ID,
            remotePath = server.defaultRemotePath,
            targetMode = TargetMode.TAILSCALE_ONLY,
            constraints = ConstraintSettings(batteryNotLow = false),
            remoteSafety = RemoteSafetySettings(createDirectoryIfMissing = true),
            deleteEnabled = false,
            excludes = "",
            status = ProfileStatus(lastMessage = "Configured Tailscale failure smoke at $now"),
        )

        app.repository.update { state ->
            state.copy(
                tailscale = TailscaleStateMetadata(),
                servers = state.servers.filterNot { it.id == TAILSCALE_FAILURE_SERVER_ID } + server,
                profiles = state.profiles.filterNot { it.id == TAILSCALE_FAILURE_PROFILE_ID } + profile,
                queue = BackupQueueState(),
                runProgress = RunProgressState(),
            )
        }
        BackupService.start(context, TAILSCALE_FAILURE_PROFILE_ID)
    }

    private fun runTailscaleLiveSmoke(context: Context, intent: Intent) {
        val app = context.applicationContext as RsyncBackupApplication
        val nodeName = intent.getStringExtra(EXTRA_TAILSCALE_NODE_NAME)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "android-rsync-debug"
        val authKey = intent.tailscaleAuthKey()
        val testHost = intent.getStringExtra(EXTRA_TAILSCALE_TEST_HOST)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val testPort = intent.getIntExtra(EXTRA_TAILSCALE_TEST_PORT, 22)

        val pendingResult = goAsync()
        Thread {
            runCatching {
                val manager = TailscaleManager(context, app.secretStore)
                val authResult = manager.authenticate(nodeName, authKey)
                val loginAt = Instant.now().toString()
                if (!authResult.success) {
                    app.repository.update { state ->
                        state.copy(
                            tailscale = state.tailscale.copy(
                                nodeName = nodeName,
                                lastError = authResult.output.ifBlank { "Tailscale auth failed" },
                            ),
                        )
                    }
                    return@Thread
                }

                val stateAlias = authResult.stateSecretAlias ?: TailscaleManager.STATE_SECRET_ALIAS
                app.repository.update { state ->
                    state.copy(
                        tailscale = TailscaleStateMetadata(
                            isConfigured = true,
                            nodeName = nodeName,
                            stateSecretAlias = stateAlias,
                            lastLoginAt = loginAt,
                            lastReachabilityTestAt = state.tailscale.lastReachabilityTestAt,
                            lastError = null,
                            keyExpiryAdviceAcknowledged = state.tailscale.keyExpiryAdviceAcknowledged,
                        ),
                    )
                }

                if (testHost != null) {
                    val testResult = manager.testReachability(
                        nodeName = nodeName,
                        stateSecretAlias = stateAlias,
                        host = testHost,
                        port = testPort,
                    )
                    val testedAt = Instant.now().toString()
                    app.repository.update { state ->
                        state.copy(
                            tailscale = state.tailscale.copy(
                                lastReachabilityTestAt = if (testResult.success) {
                                    testedAt
                                } else {
                                    state.tailscale.lastReachabilityTestAt
                                },
                                lastError = if (testResult.success) {
                                    null
                                } else {
                                    testResult.output.ifBlank { "Tailscale test failed" }
                                },
                            ),
                        )
                    }
                }
            }.onFailure { error ->
                app.repository.update { state ->
                    state.copy(
                        tailscale = state.tailscale.copy(
                            nodeName = nodeName,
                            lastError = "Debug live Tailscale smoke failed: ${error.message}",
                        ),
                    )
                }
            }
            pendingResult.finish()
        }.start()
    }

    private fun startBackup(context: Context, autoCancelAfterMs: Long) {
        BackupService.start(context, PROFILE_ID)
        if (autoCancelAfterMs > 0) {
            Thread {
                Thread.sleep(autoCancelAfterMs)
                BackupService.cancel(context)
            }.start()
        }
    }

    private fun AppState.withSmokeConfig(config: SmokeConfig, sourcePath: String): AppState {
        val now = Instant.now().toString()
        val server = ServerRecord(
            id = SERVER_ID,
            name = "Ampere smoke SSH server",
            user = config.user,
            lanHost = config.host,
            port = config.port,
            defaultRemotePath = config.remotePath,
            fingerprintGroupId = SERVER_ID,
        )
        val profile = BackupProfile(
            id = PROFILE_ID,
            name = "Debug smoke backup",
            sourcePath = sourcePath,
            serverId = SERVER_ID,
            remotePath = config.remotePath,
            targetMode = TargetMode.LAN_ONLY,
            constraints = if (config.selectedSsidConstraint) {
                ConstraintSettings(
                    batteryNotLow = false,
                    selectedSsidOnly = true,
                    manualOverrideAllowed = true,
                )
            } else {
                ConstraintSettings(batteryNotLow = false)
            },
            remoteSafety = RemoteSafetySettings(
                createDirectoryIfMissing = true,
                allowUnmarkedNonEmptyTarget = false,
            ),
            deleteEnabled = true,
            excludes = "",
            advancedArgs = config.advancedArgs,
            status = ProfileStatus(lastMessage = "Configured smoke backup $now"),
        )
        val trustedHosts = config.hostKeys.mapIndexed { index, hostKey ->
            TrustedHostFingerprint(
                id = "debug-smoke-host-key-$index",
                serverId = SERVER_ID,
                hostnames = listOf(config.host),
                port = config.port,
                algorithm = hostKey.algorithm,
                fingerprint = hostKey.fingerprint,
                publicKey = hostKey.publicKey,
                confirmedAt = now,
            )
        }
        return copy(
            settings = settings.copy(
                phoneHostname = "redroid-smoke",
                selectedSsid = if (config.selectedSsidConstraint) {
                    "debug-ssid-that-should-not-match"
                } else {
                    settings.selectedSsid
                },
            ),
            sshKeySettings = GlobalSshKeySettings(
                publicKey = config.publicKey,
                privateKeySecretAlias = PRIVATE_KEY_ALIAS,
                customPrivateKeyLabel = "Debug smoke key",
                passphraseSecretAlias = PRIVATE_KEY_PASSPHRASE_ALIAS.takeIf {
                    config.privateKeyPassphrase != null
                },
            ),
            servers = servers.filterNot { it.id == SERVER_ID } + server,
            profiles = profiles.filterNot { it.id == PROFILE_ID } + profile,
            trustedHostFingerprints = trustedHostFingerprints
                .filterNot { it.serverId == SERVER_ID } + trustedHosts,
            queue = BackupQueueState(),
            runProgress = RunProgressState(),
        )
    }

    private fun prepareSourceTree(context: Context, config: SmokeConfig): File {
        val sourceDir = File(context.filesDir, "debug-smoke-source").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        File(sourceDir, "hello.txt").writeText(config.sourceText)
        File(sourceDir, "nested").mkdirs()
        File(sourceDir, "nested/info.txt").writeText("profile=$PROFILE_ID\n")
        repeat(config.sourceFileCount) { index ->
            writeDeterministicPayload(
                file = File(sourceDir, "large-$index.bin"),
                bytes = config.sourceFileBytes,
                seed = index.toLong(),
            )
        }
        return sourceDir
    }

    private fun writeDeterministicPayload(file: File, bytes: Long, seed: Long) {
        require(bytes >= 0) { "sourceFileBytes must be non-negative" }
        val random = Random(seed)
        val buffer = ByteArray(8192)
        var remaining = bytes
        file.outputStream().use { output ->
            while (remaining > 0) {
                random.nextBytes(buffer)
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    private data class SmokeConfig(
        val host: String,
        val port: Int,
        val user: String,
        val remotePath: String,
        val privateKey: String,
        val privateKeyPassphrase: String?,
        val publicKey: String?,
        val hostKeys: List<SmokeHostKey>,
        val setupPassword: String?,
        val sourceText: String,
        val advancedArgs: String,
        val sourceFileCount: Int,
        val sourceFileBytes: Long,
        val autoCancelAfterMs: Long,
        val selectedSsidConstraint: Boolean,
    ) {
        companion object {
            fun from(intent: Intent): SmokeConfig {
                val sourceFileCount = intent.getIntExtra(EXTRA_SOURCE_FILE_COUNT, 0)
                val sourceFileBytes = intent.getLongExtra(EXTRA_SOURCE_FILE_BYTES, 0)
                val autoCancelAfterMs = intent.getLongExtra(EXTRA_AUTO_CANCEL_AFTER_MS, 0)
                require(sourceFileCount >= 0) { "sourceFileCount must be non-negative" }
                require(sourceFileBytes >= 0) { "sourceFileBytes must be non-negative" }
                require(autoCancelAfterMs >= 0) { "autoCancelAfterMs must be non-negative" }
                return SmokeConfig(
                    host = intent.requiredString(EXTRA_HOST),
                    port = intent.getIntExtra(EXTRA_PORT, 22),
                    user = intent.requiredString(EXTRA_USER),
                    remotePath = intent.requiredString(EXTRA_REMOTE_PATH),
                    privateKey = intent.privateKey(),
                    privateKeyPassphrase = intent.getStringExtra(EXTRA_PRIVATE_KEY_PASSPHRASE),
                    publicKey = intent.publicKey(),
                    hostKeys = intent.hostKeys(),
                    setupPassword = intent.getStringExtra(EXTRA_SETUP_PASSWORD),
                    sourceText = intent.getStringExtra(EXTRA_SOURCE_TEXT) ?: "android rsync smoke\n",
                    advancedArgs = intent.advancedArgs(),
                    sourceFileCount = sourceFileCount,
                    sourceFileBytes = sourceFileBytes,
                    autoCancelAfterMs = autoCancelAfterMs,
                    selectedSsidConstraint = intent.getBooleanExtra(EXTRA_SELECTED_SSID_CONSTRAINT, false),
                )
            }

            private fun Intent.requiredString(name: String): String =
                requireNotNull(getStringExtra(name)) { "Missing required smoke extra: $name" }

            private fun Intent.privateKey(): String =
                getStringExtra(EXTRA_PRIVATE_KEY)
                    ?: String(
                        Base64.getDecoder().decode(requiredString(EXTRA_PRIVATE_KEY_BASE64)),
                        Charsets.UTF_8,
                    )

            private fun Intent.publicKey(): String? =
                getStringExtra(EXTRA_PUBLIC_KEY)
                    ?: getStringExtra(EXTRA_PUBLIC_KEY_BASE64)?.let {
                        String(Base64.getDecoder().decode(it), Charsets.UTF_8)
                    }

            private fun Intent.advancedArgs(): String =
                getStringExtra(EXTRA_ADVANCED_ARGS)
                    ?: getStringExtra(EXTRA_ADVANCED_ARGS_BASE64)?.let {
                        String(Base64.getDecoder().decode(it), Charsets.UTF_8)
                    }
                    ?: ""

            private fun Intent.hostKeys(): List<SmokeHostKey> {
                val encoded = getStringExtra(EXTRA_HOST_KEYS_BASE64)
                if (encoded != null) {
                    val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                    return decoded.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val parts = line.split('\t')
                            require(parts.size == 3) { "Invalid smoke host key line" }
                            SmokeHostKey(
                                algorithm = parts[0],
                                publicKey = parts[1],
                                fingerprint = parts[2],
                            )
                        }
                        .toList()
                        .also { require(it.isNotEmpty()) { "No smoke host keys supplied" } }
                }
                return listOf(
                    SmokeHostKey(
                        algorithm = requiredString(EXTRA_HOST_KEY_ALGORITHM),
                        publicKey = requiredString(EXTRA_HOST_KEY_PUBLIC_KEY),
                        fingerprint = requiredString(EXTRA_HOST_KEY_FINGERPRINT),
                    ),
                )
            }
        }
    }

    private fun Intent.tailscaleAuthKey(): String =
        getStringExtra(EXTRA_TAILSCALE_AUTH_KEY)
            ?: getStringExtra(EXTRA_TAILSCALE_AUTH_KEY_BASE64)?.let {
                String(Base64.getDecoder().decode(it), Charsets.UTF_8)
            }
            ?: error("Missing required smoke extra: $EXTRA_TAILSCALE_AUTH_KEY_BASE64")

    private data class SmokeHostKey(
        val algorithm: String,
        val publicKey: String,
        val fingerprint: String,
    )

    companion object {
        const val ACTION_RUN_BACKUP_SMOKE = "com.ttv20.rsyncbackup.debug.RUN_BACKUP_SMOKE"
        const val ACTION_SCHEDULE_CONSTRAINT_SMOKE = "com.ttv20.rsyncbackup.debug.SCHEDULE_CONSTRAINT_SMOKE"
        const val ACTION_TAILSCALE_FAILURE_SMOKE = "com.ttv20.rsyncbackup.debug.TAILSCALE_FAILURE_SMOKE"
        const val ACTION_TAILSCALE_LIVE_SMOKE = "com.ttv20.rsyncbackup.debug.TAILSCALE_LIVE_SMOKE"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_REMOTE_PATH = "remotePath"
        const val EXTRA_PRIVATE_KEY = "privateKey"
        const val EXTRA_PRIVATE_KEY_BASE64 = "privateKeyBase64"
        const val EXTRA_PRIVATE_KEY_PASSPHRASE = "privateKeyPassphrase"
        const val EXTRA_PUBLIC_KEY = "publicKey"
        const val EXTRA_PUBLIC_KEY_BASE64 = "publicKeyBase64"
        const val EXTRA_HOST_KEYS_BASE64 = "hostKeysBase64"
        const val EXTRA_HOST_KEY_ALGORITHM = "hostKeyAlgorithm"
        const val EXTRA_HOST_KEY_PUBLIC_KEY = "hostKeyPublicKey"
        const val EXTRA_HOST_KEY_FINGERPRINT = "hostKeyFingerprint"
        const val EXTRA_SETUP_PASSWORD = "setupPassword"
        const val EXTRA_SOURCE_TEXT = "sourceText"
        const val EXTRA_ADVANCED_ARGS = "advancedArgs"
        const val EXTRA_ADVANCED_ARGS_BASE64 = "advancedArgsBase64"
        const val EXTRA_SOURCE_FILE_COUNT = "sourceFileCount"
        const val EXTRA_SOURCE_FILE_BYTES = "sourceFileBytes"
        const val EXTRA_AUTO_CANCEL_AFTER_MS = "autoCancelAfterMs"
        const val EXTRA_SELECTED_SSID_CONSTRAINT = "selectedSsidConstraint"
        const val EXTRA_ALARM_DELAY_MINUTES = "alarmDelayMinutes"
        const val EXTRA_SCHEDULE_TYPE = "scheduleType"
        const val EXTRA_TAILSCALE_AUTH_KEY = "authKey"
        const val EXTRA_TAILSCALE_AUTH_KEY_BASE64 = "authKeyBase64"
        const val EXTRA_TAILSCALE_NODE_NAME = "nodeName"
        const val EXTRA_TAILSCALE_TEST_HOST = "testHost"
        const val EXTRA_TAILSCALE_TEST_PORT = "testPort"

        const val SERVER_ID = "debug-smoke-server"
        const val PROFILE_ID = "debug-smoke-profile"
        const val SCHEDULE_SERVER_ID = "debug-schedule-constraint-server"
        const val SCHEDULE_PROFILE_ID = "debug-schedule-constraint-profile"
        const val TAILSCALE_FAILURE_SERVER_ID = "debug-tailscale-failure-server"
        const val TAILSCALE_FAILURE_PROFILE_ID = "debug-tailscale-failure-profile"
        private const val PRIVATE_KEY_ALIAS = "debug-smoke-private-key"
        private const val PRIVATE_KEY_PASSPHRASE_ALIAS = "debug-smoke-private-key-passphrase"
    }
}
