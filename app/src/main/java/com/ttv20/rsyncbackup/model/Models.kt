package com.ttv20.rsyncbackup.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_PHONE_HOSTNAME = "android-phone"
private const val LEGACY_TAILSCALE_NODE_NAME = "android-rsync"
private const val DEFAULT_TAILSCALE_NODE_NAME = "$DEFAULT_PHONE_HOSTNAME-rsync"
private const val DEFAULT_SSH_KEY_NAME = "$DEFAULT_PHONE_HOSTNAME-pocketbackup"

@Serializable
data class AppState(
    val settings: GlobalSettings = GlobalSettings(),
    val sshKeySettings: GlobalSshKeySettings = GlobalSshKeySettings(),
    val tailscale: TailscaleStateMetadata = TailscaleStateMetadata(),
    val targets: List<TargetRecord> = emptyList(),
    val profiles: List<BackupProfile> = emptyList(),
    val trustedHostFingerprints: List<TrustedHostFingerprint> = emptyList(),
    val logs: List<BackupLog> = emptyList(),
    val queue: BackupQueueState = BackupQueueState(),
    val runProgress: RunProgressState = RunProgressState(),
)

@Serializable
data class GlobalSettings(
    val phoneHostname: String = DEFAULT_PHONE_HOSTNAME,
    val logRetentionLimit: Int = 20,
    val exactAlarmFallbackEnabled: Boolean = true,
    val allFilesAccessRequested: Boolean = true,
    val batteryOptimizationExemptionRequested: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val onboardingCompletedAt: String? = null,
    val onboardingSkippedAt: String? = null,
    val onboardingLastStep: String? = null,
)

@Serializable
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

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
    val nodeName: String = LEGACY_TAILSCALE_NODE_NAME,
    val stateSecretAlias: String? = null,
    val lastLoginAt: String? = null,
    val lastReachabilityTestAt: String? = null,
    val lastError: String? = null,
    val keyExpiryAdviceAcknowledged: Boolean = false,
)

@Serializable
data class TargetRecord(
    val id: String,
    val name: String,
    val user: String,
    val lanHost: String,
    val tailscaleHost: String? = null,
    val port: Int = 22,
    val defaultRemotePath: String = "",
    val sshKeySettings: GlobalSshKeySettings? = null,
    val fingerprintGroupId: String = id,
    val publicKeyInstalledAt: String? = null,
    val keyOnlyLoginVerifiedAt: String? = null,
)

@Serializable
data class TrustedHostFingerprint(
    val id: String,
    val targetId: String,
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
    val targetId: String,
    val remotePath: String,
    val targetMode: TargetMode = TargetMode.LAN_FIRST_TAILSCALE_FALLBACK,
    val schedule: BackupSchedule = BackupSchedule(),
    val constraints: ConstraintSettings = ConstraintSettings(),
    val remoteSafety: RemoteSafetySettings = RemoteSafetySettings(),
    val deleteEnabled: Boolean = true,
    val dryRunBeforeBackup: Boolean = true,
    val excludes: String,
    val advancedArgs: String = "",
    val remoteSafetyReviewedAt: String? = null,
    val status: ProfileStatus = ProfileStatus(),
)

@Serializable
enum class TargetMode {
    LAN_ONLY,
    LAN_FIRST_TAILSCALE_FALLBACK,
    TAILSCALE_FIRST_LAN_FALLBACK,
    TAILSCALE_ONLY,
}

fun suggestedTailscaleNodeName(phoneHostname: String): String =
    "${safeDeviceName(phoneHostname)}-rsync"

fun suggestedSshKeyName(phoneHostname: String): String =
    "${safeDeviceName(phoneHostname)}-pocketbackup"

fun publicKeyWithSshKeyName(publicKey: String, keyName: String): String {
    val fields = publicKey.trim().split(Regex("\\s+"), limit = 3)
    if (fields.size < 2) return publicKey
    return "${fields[0]} ${fields[1]} ${normalizedSshKeyName(keyName)}"
}

private fun safeDeviceName(phoneHostname: String): String =
    phoneHostname
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9-]+"), "-")
        .trim('-')
        .ifBlank { DEFAULT_PHONE_HOSTNAME }

private fun normalizedSshKeyName(keyName: String): String =
    keyName.trim().replace(Regex("\\s+"), "-").ifBlank { DEFAULT_SSH_KEY_NAME }

fun effectiveTailscaleNodeName(state: AppState): String {
    val suggested = suggestedTailscaleNodeName(state.settings.phoneHostname)
    val stored = state.tailscale.nodeName.trim()
    return when {
        state.tailscale.isConfigured -> stored.ifBlank { suggested }
        state.tailscale.shouldUseSuggestedNodeName(state.settings.phoneHostname) -> suggested
        else -> stored
    }
}

fun AppState.withUpdatedSettings(settings: GlobalSettings): AppState =
    copy(
        settings = settings,
        sshKeySettings = sshKeySettings.withSuggestedSshKeyName(settings.phoneHostname),
        tailscale = tailscale.withSuggestedNodeName(
            previousPhoneHostname = this.settings.phoneHostname,
            phoneHostname = settings.phoneHostname,
        ),
    )

fun AppState.withDetectedPhoneHostname(deviceHostname: String?): AppState {
    val detected = deviceHostname?.trim().orEmpty()
    val current = settings.phoneHostname.trim()
    val nextSettings = if (detected.isNotBlank() && (current.isBlank() || current == DEFAULT_PHONE_HOSTNAME)) {
        settings.copy(phoneHostname = detected)
    } else {
        settings
    }
    return withUpdatedSettings(nextSettings)
}

private fun TailscaleStateMetadata.withSuggestedNodeName(
    previousPhoneHostname: String,
    phoneHostname: String,
): TailscaleStateMetadata {
    if (isConfigured) return this
    return if (shouldUseSuggestedNodeName(previousPhoneHostname)) {
        copy(nodeName = suggestedTailscaleNodeName(phoneHostname))
    } else {
        this
    }
}

private fun TailscaleStateMetadata.shouldUseSuggestedNodeName(phoneHostname: String): Boolean {
    val stored = nodeName.trim()
    return stored.isBlank() ||
        stored == LEGACY_TAILSCALE_NODE_NAME ||
        stored == DEFAULT_TAILSCALE_NODE_NAME ||
        stored == suggestedTailscaleNodeName(phoneHostname)
}

private fun GlobalSshKeySettings.withSuggestedSshKeyName(phoneHostname: String): GlobalSshKeySettings {
    val currentPublicKey = publicKey ?: return this
    if (customPrivateKeyLabel != null) return this
    val namedPublicKey = publicKeyWithSshKeyName(currentPublicKey, suggestedSshKeyName(phoneHostname))
    return if (namedPublicKey == currentPublicKey) this else copy(publicKey = namedPublicKey)
}

@Serializable
data class BackupSchedule(
    val type: ScheduleType = ScheduleType.DISABLED,
    val timeLocal: String = "03:00",
    val weeklyDays: List<Int> = emptyList(),
)

@Serializable
enum class ScheduleType {
    DISABLED,
    EXACT_DAILY,
    BEST_EFFORT_DAILY,
    WEEKLY,
}

fun allScheduleWeekDays(): List<Int> = (1..7).toList()

fun normalizedScheduleWeekDays(days: List<Int>): List<Int> =
    days
        .filter { it in 1..7 }
        .distinct()
        .sorted()

@Serializable
data class ConstraintSettings(
    val wifiOnly: Boolean = false,
    val unmeteredOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val batteryNotLow: Boolean = true,
    val selectedSsidOnly: Boolean = false,
    val selectedSsid: String? = null,
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
    val plannedTransferBytesRaw: Long? = null,
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
    DRY_RUN,
    RUNNING_RSYNC,
    UPLOADING_STATUS,
    CANCELLING,
    FORCE_STOPPING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

fun RunProgressState.transferProgressPercent(): Int? =
    transferProgressPercent(bytesTransferredRaw, plannedTransferBytesRaw)

fun transferProgressPercent(bytesTransferredRaw: Long?, plannedTransferBytesRaw: Long?): Int? {
    val total = plannedTransferBytesRaw?.takeIf { it > 0L } ?: return null
    val transferred = (bytesTransferredRaw ?: 0L).coerceIn(0L, total)
    return ((transferred.toDouble() / total.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
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
    const val DEFAULT_TARGET_ID = "target-home"
    const val DEFAULT_PROFILE_ID = "profile-phone"

    fun appState(defaultExcludes: String, now: String = Instant.now().toString()): AppState {
        return AppState()
    }
}

fun TargetRecord.resolvedSshKeySettings(globalSettings: GlobalSshKeySettings): GlobalSshKeySettings =
    sshKeySettings?.takeIf { it.privateKeySecretAlias != null || it.publicKey != null } ?: globalSettings

fun TargetMode.requiresTailscale(): Boolean = when (this) {
    TargetMode.LAN_ONLY -> false
    TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> true
    TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> true
    TargetMode.TAILSCALE_ONLY -> true
}

fun TargetMode.requiresLan(): Boolean = when (this) {
    TargetMode.LAN_ONLY -> true
    TargetMode.LAN_FIRST_TAILSCALE_FALLBACK -> true
    TargetMode.TAILSCALE_FIRST_LAN_FALLBACK -> true
    TargetMode.TAILSCALE_ONLY -> false
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
