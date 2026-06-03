package com.ttv20.rsyncbackup.model

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AppState(
    val settings: GlobalSettings = GlobalSettings(),
    val sshKeySettings: GlobalSshKeySettings = GlobalSshKeySettings(),
    val tailscale: TailscaleStateMetadata = TailscaleStateMetadata(),
    val servers: List<ServerRecord> = emptyList(),
    val profiles: List<BackupProfile> = emptyList(),
    val trustedHostFingerprints: List<TrustedHostFingerprint> = emptyList(),
    val logs: List<BackupLog> = emptyList(),
    val queue: BackupQueueState = BackupQueueState(),
    val runProgress: RunProgressState = RunProgressState(),
)

@Serializable
data class GlobalSettings(
    val phoneHostname: String = "android-phone",
    val logRetentionLimit: Int = 20,
    val selectedSsid: String? = null,
    val exactAlarmFallbackEnabled: Boolean = true,
    val allFilesAccessRequested: Boolean = true,
    val batteryOptimizationExemptionRequested: Boolean = true,
)

@Serializable
data class GlobalSshKeySettings(
    val keyType: String = "ed25519",
    val publicKey: String? = null,
    val privateKeySecretAlias: String? = null,
    val customPrivateKeyLabel: String? = null,
    val passphraseSecretAlias: String? = null,
    val generatedAt: String? = null,
)

@Serializable
data class TailscaleStateMetadata(
    val isConfigured: Boolean = false,
    val nodeName: String = "android-rsync",
    val stateSecretAlias: String? = null,
    val lastLoginAt: String? = null,
    val lastReachabilityTestAt: String? = null,
    val lastError: String? = null,
    val keyExpiryAdviceAcknowledged: Boolean = false,
)

@Serializable
data class ServerRecord(
    val id: String,
    val name: String,
    val user: String,
    val lanHost: String,
    val tailscaleHost: String? = null,
    val port: Int = 22,
    val defaultRemotePath: String,
    val fingerprintGroupId: String = id,
)

@Serializable
data class TrustedHostFingerprint(
    val id: String,
    val serverId: String,
    val hostnames: List<String>,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    val publicKey: String? = null,
    val confirmedAt: String,
)

@Serializable
data class BackupProfile(
    val id: String,
    val name: String,
    val sourcePath: String = "/storage/emulated/0",
    val serverId: String,
    val remotePath: String,
    val targetMode: TargetMode = TargetMode.LAN_FIRST_TAILSCALE_FALLBACK,
    val schedule: BackupSchedule = BackupSchedule(),
    val constraints: ConstraintSettings = ConstraintSettings(),
    val remoteSafety: RemoteSafetySettings = RemoteSafetySettings(),
    val deleteEnabled: Boolean = true,
    val excludes: String,
    val advancedArgs: String = "",
    val status: ProfileStatus = ProfileStatus(),
)

@Serializable
enum class TargetMode {
    LAN_ONLY,
    LAN_FIRST_TAILSCALE_FALLBACK,
    TAILSCALE_FIRST_LAN_FALLBACK,
    TAILSCALE_ONLY,
}

@Serializable
data class BackupSchedule(
    val type: ScheduleType = ScheduleType.DISABLED,
    val timeLocal: String = "03:00",
)

@Serializable
enum class ScheduleType {
    DISABLED,
    EXACT_DAILY,
    BEST_EFFORT_DAILY,
}

@Serializable
data class ConstraintSettings(
    val wifiOnly: Boolean = false,
    val unmeteredOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val batteryNotLow: Boolean = true,
    val selectedSsidOnly: Boolean = false,
    val manualOverrideAllowed: Boolean = true,
)

@Serializable
data class RemoteSafetySettings(
    val createDirectoryIfMissing: Boolean = true,
    val allowUnmarkedNonEmptyTarget: Boolean = false,
)

@Serializable
data class ProfileStatus(
    val lastRunAt: String? = null,
    val lastSuccessAt: String? = null,
    val lastStatus: RunStatus = RunStatus.NEVER_RUN,
    val lastMessage: String? = null,
    val nextRunAt: String? = null,
)

@Serializable
enum class RunStatus {
    NEVER_RUN,
    QUEUED,
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    CANCELLED,
}

@Serializable
data class BackupQueueState(
    val runningProfileId: String? = null,
    val queuedProfileIds: List<String> = emptyList(),
    val runningTrigger: BackupRunTrigger? = null,
    val queuedTriggers: Map<String, BackupRunTrigger> = emptyMap(),
)

@Serializable
data class RunProgressState(
    val profileId: String? = null,
    val profileName: String? = null,
    val phase: RunProgressPhase = RunProgressPhase.IDLE,
    val message: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val filesDiscovered: Long? = null,
    val filesTransferred: Long? = null,
    val progressPercent: Int? = null,
    val bytesTransferred: String? = null,
    val bytesTransferredRaw: Long? = null,
    val speed: String? = null,
    val averageBytesPerSecond: Long? = null,
    val recentAverageBytesPerSecond: Long? = null,
    val duration: String? = null,
    val currentFile: String? = null,
    val finalStats: Map<String, String> = emptyMap(),
    val recentOutput: List<String> = emptyList(),
)

@Serializable
enum class RunProgressPhase {
    IDLE,
    PREPARING,
    RUNNING_RSYNC,
    UPLOADING_STATUS,
    CANCELLING,
    FORCE_STOPPING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class BackupLog(
    val id: String,
    val profileId: String,
    val profileName: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val status: RunStatus,
    val trigger: BackupRunTrigger = BackupRunTrigger.MANUAL,
    val endReason: BackupEndReason? = null,
    val endReasonDetail: String? = null,
    val exitCode: Int? = null,
    val targetHostUsed: String? = null,
    val summary: String = "",
    val raw: String = "",
)

@Serializable
enum class BackupRunTrigger {
    MANUAL,
    AUTOMATIC,
}

@Serializable
enum class BackupEndReason {
    USER_CANCELLED,
    FORCE_STOPPED,
    NO_NETWORK,
    CONSTRAINTS_NOT_MET,
    CRASH,
    ERROR,
}

object InitialData {
    const val DEFAULT_SERVER_ID = "server-home"
    const val DEFAULT_PROFILE_ID = "profile-phone"

    fun appState(defaultExcludes: String, now: String = Instant.now().toString()): AppState {
        val server = ServerRecord(
            id = DEFAULT_SERVER_ID,
            name = "Home backup server",
            user = "ttv20",
            lanHost = "192.168.3.200",
            tailscaleHost = null,
            port = 22,
            defaultRemotePath = "/mnt/backup/phone",
        )
        val profile = BackupProfile(
            id = DEFAULT_PROFILE_ID,
            name = "Phone shared storage",
            sourcePath = "/storage/emulated/0",
            serverId = server.id,
            remotePath = server.defaultRemotePath,
            targetMode = TargetMode.LAN_ONLY,
            excludes = defaultExcludes.trimEnd(),
            status = ProfileStatus(lastMessage = "Created $now"),
        )
        return AppState(
            servers = listOf(server),
            profiles = listOf(profile),
        )
    }
}

fun TargetMode.requiresTailscale(): Boolean = when (this) {
    TargetMode.LAN_ONLY -> false
    TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> true
    TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> true
    TargetMode.TAILSCALE_ONLY -> true
}

fun TargetMode.routeOrder(): List<Route> = when (this) {
    TargetMode.LAN_ONLY -> listOf(Route.LAN)
    TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> listOf(Route.LAN, Route.TAILSCALE)
    TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> listOf(Route.TAILSCALE, Route.LAN)
    TargetMode.TAILSCALE_ONLY -> listOf(Route.TAILSCALE)
}

enum class Route {
    LAN,
    TAILSCALE,
}
