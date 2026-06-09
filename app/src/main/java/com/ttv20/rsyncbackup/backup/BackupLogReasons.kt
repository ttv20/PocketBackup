package com.ttv20.rsyncbackup.backup

import com.ttv20.rsyncbackup.model.BackupEndReason
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.ConstraintSettings
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.storage.AppRepository
import java.time.Instant
import java.util.UUID

internal fun AppRepository.recordConstraintBlockedBackup(
    profile: BackupProfile,
    failures: List<ConstraintFailure>,
    trigger: BackupRunTrigger,
    snapshot: ConstraintSnapshot? = null,
    now: String = Instant.now().toString(),
): BackupLog {
    val log = constraintBlockedBackupLog(profile, failures, trigger, snapshot, now)
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

internal fun AppRepository.recordForegroundServiceStartBlockedBackup(
    profile: BackupProfile,
    trigger: BackupRunTrigger,
    reason: String,
    now: String = Instant.now().toString(),
): BackupLog {
    val summary = if (trigger == BackupRunTrigger.AUTOMATIC) {
        "Scheduled backup skipped: Android foreground-service limit reached"
    } else {
        "Backup cancelled: Android foreground-service limit reached"
    }
    val log = BackupLog(
        id = UUID.randomUUID().toString(),
        profileId = profile.id,
        profileName = profile.name,
        startedAt = now,
        finishedAt = now,
        status = RunStatus.CANCELLED,
        trigger = trigger,
        endReason = BackupEndReason.ERROR,
        endReasonDetail = reason,
        summary = summary,
        raw = "Android could not start the data-sync foreground service for ${profile.name}: $reason",
    )
    appendLog(log)
    markProfile(profile.id, RunStatus.CANCELLED, summary, now)
    return log
}

internal fun AppRepository.recordForegroundServiceTimeoutBackup(
    profile: BackupProfile,
    trigger: BackupRunTrigger,
    now: String = Instant.now().toString(),
): BackupLog {
    val detail = "Android stopped the data-sync foreground service after its time limit was reached"
    val log = BackupLog(
        id = UUID.randomUUID().toString(),
        profileId = profile.id,
        profileName = profile.name,
        startedAt = profile.status.lastRunAt ?: now,
        finishedAt = now,
        status = RunStatus.CANCELLED,
        trigger = trigger,
        endReason = BackupEndReason.ERROR,
        endReasonDetail = detail,
        summary = "Backup cancelled: Android foreground-service time limit reached",
        raw = detail,
    )
    appendLog(log)
    markProfile(profile.id, RunStatus.CANCELLED, log.summary, now)
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
    snapshot: ConstraintSnapshot?,
    now: String,
): BackupLog {
    val endReason = failures.toBackupEndReason()
    val detail = constraintFailureDetail(failures)
    val summary = constraintBlockedSummary(failures, trigger, endReason)
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
        raw = constraintBlockedDetailText(profile, failures, trigger, snapshot),
    )
}

internal fun constraintFailureDetail(failures: List<ConstraintFailure>): String =
    failures.joinToString("; ") { it.message }

internal fun constraintBlockedDetailText(
    profile: BackupProfile,
    failures: List<ConstraintFailure>,
    trigger: BackupRunTrigger,
    snapshot: ConstraintSnapshot? = null,
): String = buildList {
    add("Profile: ${profile.name}")
    add("Trigger: ${trigger.logLabel()}")
    add("Reason: ${constraintFailureDetail(failures)}")
    add("")
    add("Constraints not met:")
    failures.forEach { failure ->
        add("- ${failure.message}")
    }
    add("")
    add("Enabled constraints:")
    addAll(profile.constraints.detailLines())
    if (snapshot != null) {
        add("")
        add("Current device state:")
        addAll(snapshot.detailLines())
    }
}.joinToString("\n")

private fun constraintBlockedSummary(
    failures: List<ConstraintFailure>,
    trigger: BackupRunTrigger,
    endReason: BackupEndReason,
): String {
    if (trigger == BackupRunTrigger.AUTOMATIC) {
        return "Scheduled backup skipped: ${failures.shortReason()}"
    }
    return when (endReason) {
        BackupEndReason.NO_NETWORK -> "Backup cancelled: no network"
        else -> "Backup cancelled: constraints not met"
    }
}

private fun List<ConstraintFailure>.shortReason(): String =
    when (size) {
        0 -> "constraints not met"
        1 -> single().message
        else -> "$size constraints not met"
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

private fun ConstraintSettings.detailLines(): List<String> =
    buildList {
        add("- Wi-Fi only: ${wifiOnly.yesNo()}")
        add("- Unmetered network only: ${unmeteredOnly.yesNo()}")
        add("- Charging only: ${chargingOnly.yesNo()}")
        add("- Battery must not be low: ${batteryNotLow.yesNo()}")
        val selectedSsidValue = selectedSsid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { " (${it.trim('"')})" }
            .orEmpty()
        add("- Selected Wi-Fi only: ${selectedSsidOnly.yesNo()}$selectedSsidValue")
    }

private fun ConstraintSnapshot.detailLines(): List<String> =
    listOf(
        "- Wi-Fi connected: ${hasWifiConnection.yesNo()}",
        "- Unmetered network: ${isUnmetered.yesNo()}",
        "- Charging: ${isCharging.yesNo()}",
        "- Battery low: ${isBatteryLow.yesNo()}",
        "- Current Wi-Fi: ${ssid?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: "unknown"}",
    )

private fun BackupRunTrigger.logLabel(): String =
    when (this) {
        BackupRunTrigger.MANUAL -> "Manual"
        BackupRunTrigger.AUTOMATIC -> "Scheduled"
    }

private fun Boolean.yesNo(): String = if (this) "yes" else "no"

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
