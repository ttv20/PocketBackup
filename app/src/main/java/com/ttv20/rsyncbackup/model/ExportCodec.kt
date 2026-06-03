package com.ttv20.rsyncbackup.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class ExportDocument(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val settings: GlobalSettings,
    val sshPublicKey: String? = null,
    val tailscale: ExportTailscaleMetadata,
    val servers: List<ServerRecord>,
    val profiles: List<BackupProfile>,
    val trustedHostFingerprints: List<TrustedHostFingerprint>,
)

@Serializable
data class ExportTailscaleMetadata(
    val isConfigured: Boolean,
    val nodeName: String,
    val lastLoginAt: String? = null,
    val lastReachabilityTestAt: String? = null,
    val keyExpiryAdviceAcknowledged: Boolean = false,
)

object ExportCodec {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(document: ExportDocument): String = json.encodeToString(document)

    fun decode(text: String): ExportDocument =
        json.decodeFromString(ExportDocument.serializer(), text)
}

fun AppState.toExportDocument(now: String = Instant.now().toString()): ExportDocument =
    ExportDocument(
        exportedAt = now,
        settings = settings,
        sshPublicKey = sshKeySettings.publicKey,
        tailscale = ExportTailscaleMetadata(
            isConfigured = tailscale.isConfigured,
            nodeName = tailscale.nodeName,
            lastLoginAt = tailscale.lastLoginAt,
            lastReachabilityTestAt = tailscale.lastReachabilityTestAt,
            keyExpiryAdviceAcknowledged = tailscale.keyExpiryAdviceAcknowledged,
        ),
        servers = servers,
        profiles = profiles,
        trustedHostFingerprints = trustedHostFingerprints,
    )

fun AppState.withImportedConfiguration(document: ExportDocument): AppState =
    copy(
        settings = document.settings,
        sshKeySettings = GlobalSshKeySettings(publicKey = document.sshPublicKey),
        tailscale = TailscaleStateMetadata(
            isConfigured = false,
            nodeName = document.tailscale.nodeName,
            keyExpiryAdviceAcknowledged = document.tailscale.keyExpiryAdviceAcknowledged,
        ),
        servers = document.servers,
        profiles = document.profiles.map { profile ->
            profile.copy(status = ProfileStatus(lastMessage = "Imported ${document.exportedAt}"))
        },
        trustedHostFingerprints = document.trustedHostFingerprints,
        logs = logs,
        queue = BackupQueueState(),
        runProgress = RunProgressState(),
    )
