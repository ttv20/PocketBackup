package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.storage.AppRepository
import java.time.Instant
import java.util.UUID

internal fun AppRepository.recordConstraintBlockedBackup(
    profile: BackupProfile,
    failures: List<ConstraintFailure>,
    trigger: BackupRunTrigger,
    now: String = Instant.now().toString(),
): BackupLog {
    val log = constraintBlockedBackupLog(profile, failures, trigger, now)
    appendLog(log)
    markProfile(profile.id, RunStatus.CANCELLED, log.summary, now)
    return log
}

internal fun AppRepository.recordScheduledStartBlockedBackup(
    profile: BackupProfile,
    reason: String,
    now: String = Instant.now().toString(),
): BackupLog {
    val summary = "Backup cancelled: scheduled start was deferred by Android"
    val log = BackupLog(
        id = UUID.randomUUID().toString(),
        profileId = profile.id,
        profileName = profile.name,
        startedAt = now,
        finishedAt = now,
        status = RunStatus.CANCELLED,
        trigger = BackupRunTrigger.AUTOMATIC,
        endReason = BackupEndReason.ERROR,
        endReasonDetail = reason,
        summary = summary,
        raw = reason,
    )
    appendLog(log)
    markProfile(profile.id, RunStatus.CANCELLED, summary, now)
    return log
}

internal fun backupCrashLog(
    profileId: String,
    profileName: String,
    trigger: BackupRunTrigger,
    startedAt: String,
    error: Exception,
    failedAt: String = Instant.now().toString(),
): BackupLog {
    val detail = error.message ?: error.javaClass.simpleName
    val summary = "Backup failed: $detail"
    return BackupLog(
        id = UUID.randomUUID().toString(),
        profileId = profileId,
        profileName = profileName,
        startedAt = startedAt,
        finishedAt = failedAt,
        status = RunStatus.FAILED,
        trigger = trigger,
        endReason = BackupEndReason.CRASH,
        endReasonDetail = detail,
        summary = summary,
        raw = error.stackTraceToString(),
    )
}

internal fun BackupStopReason.toBackupEndReason(): BackupEndReason =
    when (this) {
        BackupStopReason.CANCELLED -> BackupEndReason.USER_CANCELLED
        BackupStopReason.FORCE_STOPPED -> BackupEndReason.FORCE_STOPPED
    }

internal fun BackupStopReason.toBackupEndReasonDetail(): String =
    when (this) {
        BackupStopReason.CANCELLED -> "User requested cancellation"
        BackupStopReason.FORCE_STOPPED -> "Backup process was force-stopped"
    }

internal fun errorBackupEndReason(summary: String, raw: String = ""): BackupEndReason {
    val text = "$summary\n$raw".lowercase()
    return if (looksLikeNoNetwork(text)) BackupEndReason.NO_NETWORK else BackupEndReason.ERROR
}

private fun constraintBlockedBackupLog(
    profile: BackupProfile,
    failures: List<ConstraintFailure>,
    trigger: BackupRunTrigger,
    now: String,
): BackupLog {
    val endReason = failures.toBackupEndReason()
    val detail = failures.joinToString("; ") { it.message }
    val summary = when (endReason) {
        BackupEndReason.NO_NETWORK -> "Backup cancelled: no network"
        else -> "Backup cancelled: constraints not met"
    }
    return BackupLog(
        id = UUID.randomUUID().toString(),
        profileId = profile.id,
        profileName = profile.name,
        startedAt = now,
        finishedAt = now,
        status = RunStatus.CANCELLED,
        trigger = trigger,
        endReason = endReason,
        endReasonDetail = detail,
        summary = summary,
        raw = detail,
    )
}

private fun List<ConstraintFailure>.toBackupEndReason(): BackupEndReason {
    val networkCodes = setOf(
        "wifi_only",
        "unmetered_only",
        "ssid_not_configured",
        "ssid_unavailable",
        "ssid_mismatch",
    )
    return if (any { it.code in networkCodes }) {
        BackupEndReason.NO_NETWORK
    } else {
        BackupEndReason.CONSTRAINTS_NOT_MET
    }
}

private fun looksLikeNoNetwork(text: String): Boolean =
    listOf(
        "no network",
        "network is unreachable",
        "no route to host",
        "host is unreachable",
        "connection timed out",
        "could not resolve",
        "temporary failure in name resolution",
        "no usable route",
    ).any(text::contains)
