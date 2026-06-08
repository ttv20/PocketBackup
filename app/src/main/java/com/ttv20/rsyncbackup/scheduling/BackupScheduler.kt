package com.ttv20.rsyncbackup.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.normalizedScheduleWeekDays
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class AlarmScheduleMode {
    EXACT_ALLOW_IDLE,
    BEST_EFFORT_ALLOW_IDLE,
}

object AlarmSchedulePolicy {
    fun modes(scheduleType: ScheduleType, exactAllowed: Boolean): Set<AlarmScheduleMode> =
        when {
            scheduleType == ScheduleType.DISABLED -> emptySet()
            exactAllowed -> setOf(
                AlarmScheduleMode.EXACT_ALLOW_IDLE,
                AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE,
            )
            else -> setOf(AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE)
        }
}

class BackupScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(profile: BackupProfile) {
        if (profile.schedule.type == ScheduleType.DISABLED) {
            cancel(profile.id)
            return
        }
        val triggerAt = ScheduleTriggerCalculator.nextTriggerMillis(profile.schedule)
        if (triggerAt == null) {
            cancel(profile.id)
            return
        }
        scheduleAt(profile, triggerAt)
    }

    fun scheduleAt(profile: BackupProfile, triggerAt: Long) {
        if (profile.schedule.type == ScheduleType.DISABLED) {
            cancel(profile.id)
            return
        }
        cancel(profile.id)

        AlarmSchedulePolicy.modes(profile.schedule.type, exactAlarmAllowed()).forEach { mode ->
            val intent = pendingIntent(profile.id, mode, triggerAt)
            when (mode) {
                AlarmScheduleMode.EXACT_ALLOW_IDLE ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
                AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        }
    }

    fun cancel(profileId: String) {
        AlarmScheduleMode.entries.forEach { mode ->
            alarmManager.cancel(pendingIntent(profileId, mode))
        }
    }

    private fun pendingIntent(
        profileId: String,
        mode: AlarmScheduleMode,
        triggerAt: Long? = null,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            "$profileId:${mode.name}".hashCode(),
            Intent(context, ScheduleReceiver::class.java)
                .setAction("com.ttv20.rsyncbackup.SCHEDULE_${mode.name}")
                .putExtra(ScheduleReceiver.EXTRA_PROFILE_ID, profileId)
                .apply {
                    if (triggerAt != null) {
                        putExtra(ScheduleReceiver.EXTRA_TRIGGER_AT_MILLIS, triggerAt)
                    }
                },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun exactAlarmAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}

object ScheduleTriggerCalculator {
    fun nextTriggerMillis(
        schedule: BackupSchedule,
        now: LocalDateTime? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (schedule.type == ScheduleType.DISABLED) return null
        val currentTime = runCatching { LocalTime.parse(schedule.timeLocal) }.getOrDefault(LocalTime.of(3, 0))
        val currentNow = now ?: LocalDateTime.now(zone)
        val trigger = when (schedule.type) {
            ScheduleType.DISABLED -> return null
            ScheduleType.EXACT_DAILY,
            ScheduleType.BEST_EFFORT_DAILY,
            -> nextDailyTrigger(currentTime, currentNow, zone)
            ScheduleType.WEEKLY -> nextWeeklyTrigger(schedule, currentTime, currentNow, zone) ?: return null
        }
        return trigger
    }

    private fun nextDailyTrigger(time: LocalTime, now: LocalDateTime, zone: ZoneId): Long {
        var trigger = now.toLocalDate().atTime(time)
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        return trigger.atZone(zone).toInstant().toEpochMilli()
    }

    private fun nextWeeklyTrigger(
        schedule: BackupSchedule,
        time: LocalTime,
        now: LocalDateTime,
        zone: ZoneId,
    ): Long? {
        val selectedDays = normalizedScheduleWeekDays(schedule.weeklyDays).toSet()
        if (selectedDays.isEmpty()) return null
        val today = now.toLocalDate()
        val trigger = (0L..7L)
            .asSequence()
            .map { today.plusDays(it).atTime(time) }
            .firstOrNull { candidate ->
                candidate.dayOfWeek.value in selectedDays && candidate.isAfter(now)
            }
            ?: return null
        return trigger.atZone(zone).toInstant().toEpochMilli()
    }
}
