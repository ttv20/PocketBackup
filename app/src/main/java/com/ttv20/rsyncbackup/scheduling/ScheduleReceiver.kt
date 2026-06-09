package com.ttv20.rsyncbackup.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.backup.AndroidConstraintSnapshotReader
import com.ttv20.rsyncbackup.backup.BackupConstraintEvaluator
import com.ttv20.rsyncbackup.backup.BackupService
import com.ttv20.rsyncbackup.backup.recordConstraintBlockedBackup
import com.ttv20.rsyncbackup.backup.recordScheduledStartBlockedBackup
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.ScheduleType

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val scheduledFor = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, 0L)
        val app = context.applicationContext as? RsyncBackupApplication ?: return
        val profile = app.repository.state.value.profiles.firstOrNull { it.id == profileId }
        if (profile == null || profile.schedule.type == ScheduleType.DISABLED) {
            BackupScheduler(context).cancel(profileId)
            return
        }

        val scheduler = BackupScheduler(context)
        val now = System.currentTimeMillis()
        if (scheduledFor > 0L && now + EARLY_TRIGGER_TOLERANCE_MS < scheduledFor) {
            scheduler.scheduleAt(profile, scheduledFor)
            return
        }
        if (hasAlreadyHandled(context, profileId, scheduledFor)) {
            return
        }

        val scheduleDelayMillis = if (scheduledFor > 0L) now - scheduledFor else null
        val scheduleAttributes = mapOf(
            DiagnosticsAttributes.TRIGGER_TYPE to BackupRunTrigger.AUTOMATIC.name.lowercase(),
            DiagnosticsAttributes.SCHEDULE_TYPE to profile.schedule.type.name.lowercase(),
            DiagnosticsAttributes.DELAY_MS to scheduleDelayMillis?.takeIf { it >= 0L },
        ) + DiagnosticsAttributes.backupIdentity(profile)
        if (scheduleDelayMillis != null && scheduleDelayMillis > LATE_TRIGGER_TOLERANCE_MS) {
            app.diagnostics.trackEvent("schedule_alarm_late", scheduleAttributes)
        }
        app.diagnostics.trackEvent(
            "schedule_alarm_received",
            scheduleAttributes,
        )
        scheduler.schedule(profile)
        val snapshot = AndroidConstraintSnapshotReader(context).read()
        val failures = BackupConstraintEvaluator.failures(
            profile = profile,
            snapshot = snapshot,
        )
        if (failures.isNotEmpty()) {
            val log = app.repository.recordConstraintBlockedBackup(
                profile = profile,
                failures = failures,
                trigger = BackupRunTrigger.AUTOMATIC,
                snapshot = snapshot,
            )
            app.diagnostics.trackEvent(
                "constraint_blocked",
                mapOf(
                    DiagnosticsAttributes.TRIGGER_TYPE to BackupRunTrigger.AUTOMATIC.name.lowercase(),
                    DiagnosticsAttributes.CONSTRAINT_RESULT to failures.joinToString(",") { it.code },
                    DiagnosticsAttributes.CONSTRAINT_FAILURE_COUNT to failures.size,
                    DiagnosticsAttributes.CONSTRAINT_FAILURE_CODES to failures.joinToString(",") { it.code },
                    DiagnosticsAttributes.SCHEDULE_TYPE to profile.schedule.type.name.lowercase(),
                ) + DiagnosticsAttributes.backupIdentity(profile),
            )
            app.diagnostics.trackBackupLog(log, profile)
            BackupService.notifyScheduledConstraintWarning(context, profile, failures, snapshot)
            return
        }
        app.diagnostics.trackEvent(
            "constraint_check_passed",
            mapOf(
                DiagnosticsAttributes.TRIGGER_TYPE to BackupRunTrigger.AUTOMATIC.name.lowercase(),
                DiagnosticsAttributes.SCHEDULE_TYPE to profile.schedule.type.name.lowercase(),
            ) + DiagnosticsAttributes.backupIdentity(profile),
        )

        runCatching {
            BackupService.startScheduled(context, profileId)
        }.onFailure { error ->
            if (error.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException") {
                app.diagnostics.trackEvent(
                    "foreground_service_limit_reached",
                    mapOf(
                        DiagnosticsAttributes.TRIGGER_TYPE to BackupRunTrigger.AUTOMATIC.name.lowercase(),
                        DiagnosticsAttributes.FOREGROUND_SERVICE_FAILURE_REASON to (error.message ?: error.javaClass.simpleName),
                    ) + DiagnosticsAttributes.backupIdentity(profile),
                )
                app.diagnostics.trackHandledException(
                    error,
                    mapOf(
                        DiagnosticsAttributes.SOURCE to "schedule_receiver",
                        DiagnosticsAttributes.TRIGGER_TYPE to BackupRunTrigger.AUTOMATIC.name.lowercase(),
                    ) + DiagnosticsAttributes.backupIdentity(profile),
                )
                val log = app.repository.recordScheduledStartBlockedBackup(
                    profile = profile,
                    reason = error.message ?: error.javaClass.simpleName,
                )
                app.diagnostics.trackBackupLog(log, profile)
                BackupService.notifyScheduledStartBlocked(
                    context = context,
                    profile = profile,
                    reason = error.message ?: error.javaClass.simpleName,
                )
            } else {
                app.diagnostics.trackHandledException(
                    error,
                    mapOf(
                        DiagnosticsAttributes.SOURCE to "schedule_receiver",
                        DiagnosticsAttributes.TRIGGER_TYPE to BackupRunTrigger.AUTOMATIC.name.lowercase(),
                    ) + DiagnosticsAttributes.backupIdentity(profile),
                )
                throw RuntimeException(error)
            }
        }
    }

    private fun hasAlreadyHandled(context: Context, profileId: String, scheduledFor: Long): Boolean {
        if (scheduledFor <= 0L) return false
        val prefs = context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)
        val key = "handled:$profileId"
        synchronized(ScheduleReceiver::class.java) {
            if (prefs.getLong(key, 0L) == scheduledFor) {
                return true
            }
            prefs.edit().putLong(key, scheduledFor).apply()
            return false
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_TRIGGER_AT_MILLIS = "triggerAtMillis"
        private const val SCHEDULE_PREFS = "scheduled-backups"
        private const val EARLY_TRIGGER_TOLERANCE_MS = 15_000L
        private const val LATE_TRIGGER_TOLERANCE_MS = 60_000L
    }
}
