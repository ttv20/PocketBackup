package com.ttv20.rsyncbackup.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.backup.AndroidConstraintSnapshotReader
import com.ttv20.rsyncbackup.backup.BackupConstraintEvaluator
import com.ttv20.rsyncbackup.backup.BackupService
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

        scheduler.schedule(profile)
        val failures = BackupConstraintEvaluator.failures(
            profile = profile,
            snapshot = AndroidConstraintSnapshotReader(context).read(),
            selectedSsid = app.repository.state.value.settings.selectedSsid,
        )
        if (failures.isNotEmpty()) {
            BackupService.notifyConstraintWarning(context, profile, failures)
            return
        }

        runCatching {
            BackupService.startScheduled(context, profileId)
        }.onFailure { error ->
            if (error.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException") {
                BackupService.notifyScheduledStartBlocked(
                    context = context,
                    profile = profile,
                    reason = error.message ?: error.javaClass.simpleName,
                )
            } else {
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
    }
}
