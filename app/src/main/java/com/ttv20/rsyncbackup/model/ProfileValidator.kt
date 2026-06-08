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
        val target = state.targets.firstOrNull { it.id == profile.targetId }

        if (profile.name.isBlank()) {
            issues += ValidationIssue("profile_name_missing", "Profile name is required", Severity.ERROR)
        }
        if (profile.sourcePath.isBlank()) {
            issues += ValidationIssue("source_missing", "Source path is required", Severity.ERROR)
        }
        if (profile.remotePath.isBlank()) {
            issues += ValidationIssue("remote_path_missing", "Remote path is required", Severity.ERROR)
        }
        if (target == null) {
            issues += ValidationIssue("target_missing", "Selected target no longer exists", Severity.ERROR)
            return issues
        }

        if (target.user.isBlank()) {
            issues += ValidationIssue("target_user_missing", "Target user is required", Severity.ERROR)
        }
        if (target.port !in 1..65535) {
            issues += ValidationIssue("target_port_invalid", "Target port must be between 1 and 65535", Severity.ERROR)
        }
        if (profile.targetMode.requiresLan() && target.lanHost.isBlank()) {
            issues += ValidationIssue(
                "lan_host_missing",
                "This target mode needs a server address",
                Severity.ERROR,
            )
        }
        if (profile.targetMode.requiresTailscale()) {
            if (target.tailscaleHost.isNullOrBlank()) {
                issues += ValidationIssue(
                    "tailscale_host_missing",
                    "This target mode needs a Tailscale device",
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
        if (profile.constraints.selectedSsidOnly && profile.constraints.selectedSsid.isNullOrBlank()) {
            issues += ValidationIssue(
                "ssid_missing",
                "Selected WiFi network constraint needs a configured network",
                Severity.WARNING,
            )
        }
        if (profile.schedule.type == ScheduleType.WEEKLY && normalizedScheduleWeekDays(profile.schedule.weeklyDays).isEmpty()) {
            issues += ValidationIssue(
                "weekly_days_missing",
                "Choose at least one day for weekly schedule",
                Severity.ERROR,
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
