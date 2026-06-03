package com.ttv20.rsyncbackup.scheduling

import com.ttv20.rsyncbackup.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun disabledDoesNotScheduleAlarms() {
        assertEquals(
            emptySet<AlarmScheduleMode>(),
            AlarmSchedulePolicy.modes(ScheduleType.DISABLED, exactAllowed = true),
        )
    }
}
