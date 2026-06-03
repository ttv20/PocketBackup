package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.ServerRecord
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
        server: ServerRecord,
        trustedHostFingerprints: List<TrustedHostFingerprint>,
    ): String {
        val serverIds = setOf(server.id, server.fingerprintGroupId)
        val entries = trustedHostFingerprints
            .asSequence()
            .filter { it.serverId in serverIds }
            .mapNotNull { entry ->
                val publicKey = publicKeyBlob(entry.publicKey) ?: return@mapNotNull null
                val hosts = entry.hostnames
                    .ifEmpty { defaultHostnames(server) }
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

    private fun defaultHostnames(server: ServerRecord): List<String> =
        listOfNotNull(server.lanHost, server.tailscaleHost)

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
