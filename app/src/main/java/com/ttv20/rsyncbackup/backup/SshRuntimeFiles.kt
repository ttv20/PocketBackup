package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.TargetRecord
import com.ttv20.rsyncbackup.model.TrustedHostFingerprint
import java.util.Base64

object SshRuntimeFiles {
    fun privateKeyText(secretBytes: ByteArray): String {
        val asText = secretBytes.toString(Charsets.UTF_8)
        if (asText.trimStart().startsWith("-----BEGIN ")) {
            return asText.trimEnd() + "\n"
        }

        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII))
            .encodeToString(secretBytes)
        return "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----\n"
    }

    fun knownHostsText(
        target: TargetRecord,
        trustedHostFingerprints: List<TrustedHostFingerprint>,
    ): String {
        val targetIds = setOf(target.id, target.fingerprintGroupId)
        val entries = trustedHostFingerprints
            .asSequence()
            .filter { it.targetId in targetIds }
            .mapNotNull { entry ->
                val publicKey = publicKeyBlob(entry.publicKey) ?: return@mapNotNull null
                val hosts = entry.hostnames
                    .ifEmpty { defaultHostnames(target) }
                    .map { knownHostPattern(it, entry.port) }
                    .distinct()
                    .joinToString(",")
                if (hosts.isBlank()) {
                    null
                } else {
                    "$hosts ${entry.algorithm} $publicKey"
                }
            }
            .toList()
        return if (entries.isEmpty()) "" else entries.joinToString(separator = "\n", postfix = "\n")
    }

    private fun defaultHostnames(target: TargetRecord): List<String> =
        listOfNotNull(target.lanHost, target.tailscaleHost)

    private fun knownHostPattern(hostname: String, port: Int): String {
        val host = hostname.trim()
        if (host.isBlank()) return host
        if (host.startsWith("[") || port == 22) return host
        return "[$host]:$port"
    }

    private fun publicKeyBlob(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(Regex("\\s+"))
        return if (parts.size >= 2 && parts[0].startsWith("ssh-")) parts[1] else parts.first()
    }
}
