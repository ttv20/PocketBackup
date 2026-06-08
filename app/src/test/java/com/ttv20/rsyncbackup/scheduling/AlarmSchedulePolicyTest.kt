package com.ttv20.rsyncbackup.scheduling

import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmSchedulePolicyTest {
    @Test
    fun dailyUsesExactAndBestEffortAlarmsWhenExactIsAllowed() {
        assertEquals(
            setOf(AlarmScheduleMode.EXACT_ALLOW_IDLE, AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE),
            AlarmSchedulePolicy.modes(ScheduleType.EXACT_DAILY, exactAllowed = true),
        )
    }

    @Test
    fun dailyUsesBestEffortAlarmWhenExactIsDenied() {
        assertEquals(
            setOf(AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE),
            AlarmSchedulePolicy.modes(ScheduleType.EXACT_DAILY, exactAllowed = false),
        )
    }

    @Test
    fun legacyBestEffortDailyAlsoUsesBothAlarmsWhenExactIsAllowed() {
        assertEquals(
            setOf(AlarmScheduleMode.EXACT_ALLOW_IDLE, AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE),
            AlarmSchedulePolicy.modes(ScheduleType.BEST_EFFORT_DAILY, exactAllowed = true),
        )
    }

    @Test
    fun weeklyUsesExactAndBestEffortAlarmsWhenExactIsAllowed() {
        assertEquals(
            setOf(AlarmScheduleMode.EXACT_ALLOW_IDLE, AlarmScheduleMode.BEST_EFFORT_ALLOW_IDLE),
            AlarmSchedulePolicy.modes(ScheduleType.WEEKLY, exactAllowed = true),
        )
    }

    @Test
    fun disabledDoesNotScheduleAlarms() {
        assertEquals(
            emptySet<AlarmScheduleMode>(),
            AlarmSchedulePolicy.modes(ScheduleType.DISABLED, exactAllowed = true),
        )
    }

    @Test
    fun weeklyTriggerUsesTodayWhenSelectedTimeIsStillAhead() {
        val zone = ZoneId.of("UTC")

        val trigger = ScheduleTriggerCalculator.nextTriggerMillis(
            schedule = BackupSchedule(
                type = ScheduleType.WEEKLY,
                timeLocal = "12:00",
                weeklyDays = listOf(1),
            ),
            now = LocalDateTime.of(2026, 6, 8, 10, 0),
            zone = zone,
        )

        assertEquals(
            LocalDateTime.of(2026, 6, 8, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun weeklyTriggerMovesToNextWeekWhenTodayTimePassed() {
        val zone = ZoneId.of("UTC")

        val trigger = ScheduleTriggerCalculator.nextTriggerMillis(
            schedule = BackupSchedule(
                type = ScheduleType.WEEKLY,
                timeLocal = "09:00",
                weeklyDays = listOf(1),
            ),
            now = LocalDateTime.of(2026, 6, 8, 10, 0),
            zone = zone,
        )

        assertEquals(
            LocalDateTime.of(2026, 6, 15, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun weeklyTriggerSkipsToNextSelectedDay() {
        val zone = ZoneId.of("UTC")

        val trigger = ScheduleTriggerCalculator.nextTriggerMillis(
            schedule = BackupSchedule(
                type = ScheduleType.WEEKLY,
                timeLocal = "03:00",
                weeklyDays = listOf(3),
            ),
            now = LocalDateTime.of(2026, 6, 8, 10, 0),
            zone = zone,
        )

        assertEquals(
            LocalDateTime.of(2026, 6, 10, 3, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun weeklyTriggerWithNoDaysDoesNotSchedule() {
        val trigger = ScheduleTriggerCalculator.nextTriggerMillis(
            schedule = BackupSchedule(
                type = ScheduleType.WEEKLY,
                timeLocal = "03:00",
                weeklyDays = emptyList(),
            ),
            now = LocalDateTime.of(2026, 6, 8, 10, 0),
            zone = ZoneId.of("UTC"),
        )

        assertNull(trigger)
    }
}
