@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttv20.rsyncbackup.model.BackupSchedule
import com.ttv20.rsyncbackup.model.ScheduleType
import com.ttv20.rsyncbackup.model.allScheduleWeekDays
import com.ttv20.rsyncbackup.model.normalizedScheduleWeekDays
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

internal fun orderedScheduleDays(locale: Locale = Locale.getDefault()): List<DayOfWeek> {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    return (0L..6L).map { firstDay.plus(it) }
}

internal fun scheduleDayLabel(
    day: DayOfWeek,
    locale: Locale = Locale.getDefault(),
    textStyle: TextStyle = TextStyle.SHORT,
): String =
    day.getDisplayName(textStyle, locale)

internal fun weeklyScheduleSummary(days: List<Int>, locale: Locale = Locale.getDefault()): String {
    val selectedDays = normalizedScheduleWeekDays(days)
    if (selectedDays.isEmpty()) return "no days"
    if (selectedDays.size == 7) return "all days"
    return orderedScheduleDays(locale)
        .filter { it.value in selectedDays }
        .joinToString(", ") { scheduleDayLabel(it, locale) }
}

@Composable
internal fun ScheduleEditor(schedule: BackupSchedule, onChange: (BackupSchedule) -> Unit) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val dailySelected = schedule.type == ScheduleType.EXACT_DAILY || schedule.type == ScheduleType.BEST_EFFORT_DAILY
    val weeklyDays = normalizedScheduleWeekDays(schedule.weeklyDays)
    Selector {
        ScheduleChoiceButton(
            selected = schedule.type == ScheduleType.DISABLED,
            onClick = { onChange(schedule.copy(type = ScheduleType.DISABLED)) },
            label = "Disabled",
        )
        ScheduleChoiceButton(
            selected = dailySelected,
            onClick = { onChange(schedule.copy(type = ScheduleType.EXACT_DAILY)) },
            label = "Daily",
        )
        ScheduleChoiceButton(
            selected = schedule.type == ScheduleType.WEEKLY,
            onClick = {
                onChange(
                    schedule.copy(
                        type = ScheduleType.WEEKLY,
                        weeklyDays = weeklyDays.ifEmpty { allScheduleWeekDays() },
                    ),
                )
            },
            label = "Weekly",
        )
    }
    if (schedule.type == ScheduleType.WEEKLY) {
        val selectedDays = weeklyDays.ifEmpty { allScheduleWeekDays() }
        WeeklyDayToggleRow(
            selectedDays = selectedDays,
            locale = locale,
            onToggle = { dayValue ->
                val nextDays = if (dayValue in selectedDays) {
                    selectedDays - dayValue
                } else {
                    selectedDays + dayValue
                }
                if (nextDays.isNotEmpty()) {
                    onChange(schedule.copy(weeklyDays = normalizedScheduleWeekDays(nextDays)))
                }
            },
        )
    }
    OutlinedButton(
        onClick = {
            showScheduleTimePicker(context, schedule.timeLocal) { selectedTime ->
                val nextType = if (schedule.type == ScheduleType.DISABLED) ScheduleType.EXACT_DAILY else schedule.type
                onChange(
                    schedule.copy(
                        type = nextType,
                        timeLocal = selectedTime,
                        weeklyDays = if (nextType == ScheduleType.WEEKLY) {
                            weeklyDays.ifEmpty { allScheduleWeekDays() }
                        } else {
                            schedule.weeklyDays
                        },
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Schedule time ${normalizedScheduleTime(schedule.timeLocal)}")
    }
}

@Composable
internal fun WeeklyDayToggleRow(
    selectedDays: List<Int>,
    locale: Locale,
    onToggle: (Int) -> Unit,
) {
    val days = orderedScheduleDays(locale)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labels = scheduleDayToggleLabels(days, locale, maxWidth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            days.forEachIndexed { index, day ->
                val dayValue = day.value
                WeeklyDayToggle(
                    label = labels[index],
                    selected = dayValue in selectedDays,
                    onClick = { onToggle(dayValue) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun WeeklyDayToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun scheduleDayToggleLabels(days: List<DayOfWeek>, locale: Locale, maxWidth: androidx.compose.ui.unit.Dp): List<String> {
    val shortLabels = days.map { scheduleDayLabel(it, locale, TextStyle.SHORT) }
    val longestShortLabel = shortLabels.maxOfOrNull { it.length } ?: 0
    val narrowNeeded = maxWidth < when {
        longestShortLabel <= 2 -> 300.dp
        longestShortLabel <= 3 -> 340.dp
        longestShortLabel <= 4 -> 390.dp
        else -> 440.dp
    }
    return if (narrowNeeded) {
        days.map { scheduleDayLabel(it, locale, TextStyle.NARROW) }
    } else {
        shortLabels
    }
}

@Composable
internal fun ScheduleChoiceButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}

internal fun showScheduleTimePicker(
    context: Context,
    timeLocal: String,
    onTimeSelected: (String) -> Unit,
) {
    val currentTime = parsedScheduleTime(timeLocal)
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            onTimeSelected(formatScheduleTime(hourOfDay, minute))
        },
        currentTime.hour,
        currentTime.minute,
        DateFormat.is24HourFormat(context),
    ).show()
}

internal fun parsedScheduleTime(timeLocal: String): LocalTime =
    runCatching { LocalTime.parse(timeLocal) }.getOrDefault(LocalTime.of(3, 0))

internal fun normalizedScheduleTime(timeLocal: String): String {
    val time = parsedScheduleTime(timeLocal)
    return formatScheduleTime(time.hour, time.minute)
}

internal fun formatScheduleTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(Locale.US, hour, minute)
