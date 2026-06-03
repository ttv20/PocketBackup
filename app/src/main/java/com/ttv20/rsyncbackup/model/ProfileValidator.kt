package com.ttv20.rsyncbackup.model

data class ValidationIssue(
    val code: String,
    val message: String,
    val severity: Severity = Severity.WARNING,
)

enum class Severity {
    INFO,
    WARNING,
    ERROR,
}

object ProfileValidator {
    fun validate(profile: BackupProfile, state: AppState): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val server = state.servers.firstOrNull { it.id == profile.serverId }

        if (profile.name.isBlank()) {
            issues += ValidationIssue("profile_name_missing", "Profile name is required", Severity.ERROR)
        }
        if (profile.sourcePath.isBlank()) {
            issues += ValidationIssue("source_missing", "Source path is required", Severity.ERROR)
        }
        if (profile.remotePath.isBlank()) {
            issues += ValidationIssue("remote_path_missing", "Remote path is required", Severity.ERROR)
        }
        if (server == null) {
            issues += ValidationIssue("server_missing", "Selected server no longer exists", Severity.ERROR)
            return issues
        }

        if (server.user.isBlank() || server.lanHost.isBlank()) {
            issues += ValidationIssue("server_incomplete", "Server user and LAN host are required", Severity.ERROR)
        }
        if (server.port !in 1..65535) {
            issues += ValidationIssue("server_port_invalid", "Server port must be between 1 and 65535", Severity.ERROR)
        }
        if (profile.targetMode.requiresTailscale()) {
            if (server.tailscaleHost.isNullOrBlank()) {
                issues += ValidationIssue(
                    "tailscale_host_missing",
                    "This target mode needs a Tailscale host",
                    Severity.ERROR,
                )
            }
            if (!state.tailscale.isConfigured) {
                issues += ValidationIssue(
                    "tailscale_not_configured",
                    "Tailscale is not configured for this target mode",
                    Severity.WARNING,
                )
            }
        }
        if (profile.constraints.selectedSsidOnly && state.settings.selectedSsid.isNullOrBlank()) {
            issues += ValidationIssue(
                "ssid_missing",
                "Selected SSID constraint needs a configured SSID",
                Severity.WARNING,
            )
        }
        if (profile.advancedArgs.isNotBlank()) {
            runCatching { ShellArgs.split(profile.advancedArgs) }
                .onFailure {
                    issues += ValidationIssue(
                        "advanced_args_invalid",
                        "Advanced rsync args have unmatched quotes",
                        Severity.ERROR,
                    )
                }
        }

        return issues
    }

    fun saveWarnings(profile: BackupProfile, state: AppState): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val remotePath = profile.remotePath.trim()
        val server = state.servers.firstOrNull { it.id == profile.serverId }
        if (profile.deleteEnabled && remotePathLooksBroad(remotePath)) {
            issues += ValidationIssue(
                "remote_path_broad_delete",
                "Remote path looks too broad for a delete-enabled backup",
                Severity.WARNING,
            )
        }
        if (profile.deleteEnabled && server != null && !remotePathWithinDefault(remotePath, server.defaultRemotePath)) {
            issues += ValidationIssue(
                "remote_path_outside_server_default",
                "Remote path differs from the selected server default; confirm it is a dedicated backup directory",
                Severity.WARNING,
            )
        }
        return issues
    }

    private fun remotePathLooksBroad(remotePath: String): Boolean {
        if (remotePath.isBlank()) return false
        val trimmed = remotePath.trimEnd('/')
        if (trimmed in setOf("/", ".", "~")) return true
        val segments = trimmed
            .removePrefix("~/")
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        return segments.size <= 1
    }

    private fun remotePathWithinDefault(remotePath: String, defaultPath: String): Boolean {
        val normalizedRemote = remotePath.trimEnd('/')
        val normalizedDefault = defaultPath.trimEnd('/')
        if (normalizedRemote.isBlank() || normalizedDefault.isBlank()) return true
        return normalizedRemote == normalizedDefault || normalizedRemote.startsWith("$normalizedDefault/")
    }
}

object ShellArgs {
    fun split(input: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        for (char in input) {
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }
                char == '\\' -> escaping = true
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '\'' || char == '"' -> quote = char
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        args += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }

        if (escaping) current.append('\\')
        require(quote == null) { "Unterminated quote" }
        if (current.isNotEmpty()) args += current.toString()
        return args
    }
}
