package com.ttv20.rsyncbackup.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

@Serializable
data class ExportDocument(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val settings: GlobalSettings,
    val sshPublicKey: String? = null,
    val tailscale: ExportTailscaleMetadata,
    val targets: List<TargetRecord>,
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

    fun decode(text: String): ExportDocument {
        val document = json.decodeFromString(ExportDocument.serializer(), text)
        return document.copy(
            profiles = document.profiles.withLegacySelectedSsid(legacySelectedSsid(text)),
        )
    }

    fun decodeAppState(text: String): AppState {
        val state = json.decodeFromString(AppState.serializer(), text)
        return state.copy(
            profiles = state.profiles.withLegacySelectedSsid(legacySelectedSsid(text)),
        )
    }

    private fun legacySelectedSsid(text: String): String? =
        runCatching {
            json.parseToJsonElement(text)
                .jsonObject["settings"]
                ?.jsonObject
                ?.get("selectedSsid")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.cleanSsid()
        }.getOrNull()

    private fun List<BackupProfile>.withLegacySelectedSsid(legacySelectedSsid: String?): List<BackupProfile> {
        val ssid = legacySelectedSsid?.cleanSsid() ?: return this
        return map { profile ->
            if (profile.constraints.selectedSsidOnly && profile.constraints.selectedSsid.isNullOrBlank()) {
                profile.copy(constraints = profile.constraints.copy(selectedSsid = ssid))
            } else {
                profile
            }
        }
    }
}

private fun String.cleanSsid(): String? =
    trim()
        .trim('"')
        .takeIf { it.isNotBlank() }

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
        targets = targets,
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
        targets = document.targets,
        profiles = document.profiles.map { profile ->
            profile.copy(status = ProfileStatus(lastMessage = "Imported ${document.exportedAt}"))
        },
        trustedHostFingerprints = document.trustedHostFingerprints,
        logs = logs,
        queue = BackupQueueState(),
        runProgress = RunProgressState(),
    )
