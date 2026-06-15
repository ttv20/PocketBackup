package com.ttv20.rsyncbackup.ui

import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DashboardScreenTest {
    @Test
    fun dashboardNextRunUsesEnabledScheduleWhenStatusHasNoNextRun() {
        val zone = ZoneId.of("UTC")
        val nextRun = dashboardNextRunAt(
            profiles = listOf(
                testProfile(
                    schedule = BackupSchedule(
                        type = ScheduleType.EXACT_DAILY,
                        timeLocal = "12:00",
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 8, 10, 0),
            zone = zone,
        )

        assertEquals("2026-06-08T12:00:00Z", nextRun)
    }

    @Test
    fun dashboardNextRunIgnoresDisabledSchedules() {
        val nextRun = dashboardNextRunAt(
            profiles = listOf(
                testProfile(schedule = BackupSchedule(type = ScheduleType.DISABLED)),
            ),
            now = LocalDateTime.of(2026, 6, 8, 10, 0),
            zone = ZoneId.of("UTC"),
        )

        assertNull(nextRun)
    }

    private fun testProfile(schedule: BackupSchedule): BackupProfile =
        BackupProfile(
            id = "profile-phone",
            name = "Phone",
            targetId = "target-home",
            remotePath = "/backup/phone",
            schedule = schedule,
            excludes = "",
        )
}
