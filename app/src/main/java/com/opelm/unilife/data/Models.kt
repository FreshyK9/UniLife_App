package com.opelm.unilife.data

import java.time.LocalDate
import java.time.LocalTime

data class SubjectWithUsage(
    val id: Long,
    val name: String,
    val room: String,
    val scheduleUsageCount: Int,
    val testUsageCount: Int
)

data class ClassWithSubject(
    val id: Long,
    val templateWeekId: Long,
    val subjectId: Long,
    val subjectName: String,
    val subjectRoom: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val location: String,
    val note: String
)

data class TestWithSubject(
    val id: Long,
    val subjectId: Long,
    val subjectName: String,
    val dateEpochDay: Long,
    val note: String
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}

data class ScheduleCycleItemWithTemplate(
    val id: Long,
    val position: Int,
    val templateWeekId: Long,
    val templateName: String
)

data class ScheduleDayClass(
    val id: Long,
    val subjectId: Long,
    val subjectName: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String,
    val note: String,
    val testNote: String?
)

data class ScheduleReminderTest(
    val id: Long,
    val subjectName: String,
    val note: String
)

data class SchedulePreview(
    val date: LocalDate,
    val templateName: String?,
    val classes: List<ScheduleDayClass>,
    val tomorrowTests: List<ScheduleReminderTest>,
    val isConfigured: Boolean,
    val emptyReason: String?
)
