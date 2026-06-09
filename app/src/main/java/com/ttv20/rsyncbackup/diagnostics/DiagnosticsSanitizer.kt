package com.ttv20.rsyncbackup.diagnostics

import com.ttv20.rsyncbackup.model.BackupProfile

private const val MAX_ATTRIBUTE_STRING_LENGTH = 256
private const val MAX_CRASH_STRING_LENGTH = 12_000

internal object DiagnosticsAttributes {
    const val PROFILE_ID = "profile_id"
    const val TARGET_ID = "target_id"
    const val BACKUP_PHASE = "backup_phase"
    const val TRIGGER_TYPE = "trigger_type"
    const val TARGET_MODE = "target_mode"
    const val ROUTE_USED = "route_used"
    const val DRY_RUN_ENABLED = "dry_run_enabled"
    const val DELETE_ENABLED = "delete_enabled"
    const val CONSTRAINT_RESULT = "constraint_result"
    const val CONSTRAINT_FAILURE_COUNT = "constraint_failure_count"
    const val CONSTRAINT_FAILURE_CODES = "constraint_failure_codes"
    const val FOREGROUND_SERVICE_FAILURE_REASON = "foreground_service_failure_reason"
    const val EXIT_CODE = "exit_code"
    const val RSYNC_EXIT_CODE = "rsync_exit_code"
    const val RUN_STATUS = "run_status"
    const val END_REASON = "end_reason"
    const val FAILURE_STAGE = "failure_stage"
    const val FAILURE_CATEGORY = "failure_category"
    const val RSYNC_WARNING_24 = "rsync_warning_24"
    const val BACKUP_TOTAL_FILES = "backup_total_files"
    const val BACKUP_TOTAL_BYTES = "backup_total_bytes"
    const val BACKUP_CHANGED_FILES = "backup_changed_files"
    const val BACKUP_CHANGED_BYTES = "backup_changed_bytes"
    const val BACKUP_DURATION_MS = "backup_duration_ms"
    const val BACKUP_DRY_RUN_DURATION_MS = "backup_dry_run_duration_ms"
    const val BACKUP_AVERAGE_BYTES_PER_SECOND = "backup_average_bytes_per_second"
    const val SCHEDULE_TYPE = "schedule_type"
    const val DELAY_MS = "delay_ms"
    const val PERMISSION_TYPE = "permission_type"
    const val RESULT = "result"
    const val PROFILE_COUNT = "profile_count"
    const val TARGET_COUNT = "target_count"
    const val NATIVE_MISSING_COMPONENTS = "native_missing_components"
    const val EXCEPTION_TYPE = "exception_type"
    const val SOURCE = "source"

    fun backupIdentity(profile: BackupProfile): Map<String, Any?> =
        backupIdentity(profileId = profile.id, targetId = profile.targetId)

    fun backupIdentity(profileId: String, targetId: String? = null): Map<String, Any?> =
        buildMap {
            put(PROFILE_ID, profileId)
            targetId?.let { put(TARGET_ID, it) }
        }
}

object DiagnosticsSanitizer {
    private val allowedAttributeKeys = setOf(
        DiagnosticsAttributes.PROFILE_ID,
        DiagnosticsAttributes.TARGET_ID,
        DiagnosticsAttributes.BACKUP_PHASE,
        DiagnosticsAttributes.TRIGGER_TYPE,
        DiagnosticsAttributes.TARGET_MODE,
        DiagnosticsAttributes.ROUTE_USED,
        DiagnosticsAttributes.DRY_RUN_ENABLED,
        DiagnosticsAttributes.DELETE_ENABLED,
        DiagnosticsAttributes.CONSTRAINT_RESULT,
        DiagnosticsAttributes.CONSTRAINT_FAILURE_COUNT,
        DiagnosticsAttributes.CONSTRAINT_FAILURE_CODES,
        DiagnosticsAttributes.FOREGROUND_SERVICE_FAILURE_REASON,
        DiagnosticsAttributes.EXIT_CODE,
        DiagnosticsAttributes.RSYNC_EXIT_CODE,
        DiagnosticsAttributes.RUN_STATUS,
        DiagnosticsAttributes.END_REASON,
        DiagnosticsAttributes.FAILURE_STAGE,
        DiagnosticsAttributes.FAILURE_CATEGORY,
        DiagnosticsAttributes.RSYNC_WARNING_24,
        DiagnosticsAttributes.BACKUP_TOTAL_FILES,
        DiagnosticsAttributes.BACKUP_TOTAL_BYTES,
        DiagnosticsAttributes.BACKUP_CHANGED_FILES,
        DiagnosticsAttributes.BACKUP_CHANGED_BYTES,
        DiagnosticsAttributes.BACKUP_DURATION_MS,
        DiagnosticsAttributes.BACKUP_DRY_RUN_DURATION_MS,
        DiagnosticsAttributes.BACKUP_AVERAGE_BYTES_PER_SECOND,
        DiagnosticsAttributes.SCHEDULE_TYPE,
        DiagnosticsAttributes.DELAY_MS,
        DiagnosticsAttributes.PERMISSION_TYPE,
        DiagnosticsAttributes.RESULT,
        DiagnosticsAttributes.PROFILE_COUNT,
        DiagnosticsAttributes.TARGET_COUNT,
        DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS,
        DiagnosticsAttributes.EXCEPTION_TYPE,
        DiagnosticsAttributes.SOURCE,
    )
    private val allowedNativeComponents = setOf("rsync", "ssh", "ssh-keygen", "ssh-keyscan", "scp", "sftp", "tsnet-nc")
    private val commandLinePattern = Regex("""(?i)\b(rsync|ssh|scp|sftp|ssh-keyscan|ssh-keygen)\b.*""")
    private val androidPathPattern = Regex("""(?i)(/storage/emulated/\d+|/sdcard|/mnt|/data/user/\d+|/data/data|/home)/[^\s"']*""")
    private val remoteSpecPattern = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9._-]+:[^\s"']*""")
    private val userAtHostPattern = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9._-]+""")
    private val ipv4Pattern = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val sensitiveAssignmentPattern = Regex(
        """(?i)\b(host|server|username|user|ssid|bssid|path|command|remote|source|target)\s*=\s*[^,\s}]+""",
    )

    fun sanitizeAttributes(attributes: Map<String, Any?>): Map<String, Any> =
        attributes
            .filterKeys { it in allowedAttributeKeys }
            .mapNotNull { (key, value) ->
                sanitizeAttributeValue(key, value)?.let { key to it }
            }
            .toMap()

    fun scrubCrashText(value: String?): String =
        scrub(value, MAX_CRASH_STRING_LENGTH)

    fun scrubAttributeText(value: String?): String =
        scrub(value, MAX_ATTRIBUTE_STRING_LENGTH)

    private fun sanitizeAttributeValue(key: String, value: Any?): Any? =
        when (value) {
            null -> null
            is Iterable<*> -> sanitizeIterableAttribute(key, value)
            is Boolean -> value
            is Int -> value
            is Long -> value
            is Double -> value
            is Float -> value.toDouble()
            is Short -> value.toInt()
            is Byte -> value.toInt()
            is String -> scrubAttributeText(value)
            is Enum<*> -> value.name.lowercase()
            else -> scrubAttributeText(value.toString())
        }

    private fun sanitizeIterableAttribute(key: String, value: Iterable<*>): String? {
        if (key != DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS) {
            return scrubAttributeText(value.joinToString(","))
        }
        return value
            .mapNotNull { it?.toString()?.trim() }
            .filter { it in allowedNativeComponents }
            .distinct()
            .joinToString(",")
            .takeIf { it.isNotBlank() }
    }

    private fun scrub(value: String?, maxLength: Int): String {
        val source = value.orEmpty()
        if (source.isBlank()) return ""
        val scrubbed = source
            .lineSequence()
            .joinToString("\n") { line ->
                if (commandLinePattern.containsMatchIn(line)) {
                    "[command omitted]"
                } else {
                    line
                        .replace(remoteSpecPattern, "[remote omitted]")
                        .replace(userAtHostPattern, "[user-host omitted]")
                        .replace(androidPathPattern, "[path omitted]")
                        .replace(ipv4Pattern, "[ip omitted]")
                        .replace(sensitiveAssignmentPattern) { match ->
                            "${match.groupValues[1]}=[omitted]"
                        }
                }
            }
        return if (scrubbed.length <= maxLength) {
            scrubbed
        } else {
            scrubbed.take(maxLength) + "...[truncated]"
        }
    }
}
