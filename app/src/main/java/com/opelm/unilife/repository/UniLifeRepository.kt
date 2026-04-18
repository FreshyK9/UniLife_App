package com.opelm.unilife.repository

import com.opelm.unilife.data.ClassWithSubject
import com.opelm.unilife.data.ScheduleClassDao
import com.opelm.unilife.data.ScheduleClassEntryEntity
import com.opelm.unilife.data.ScheduleCycleConfigEntity
import com.opelm.unilife.data.ScheduleCycleDao
import com.opelm.unilife.data.ScheduleCycleItemEntity
import com.opelm.unilife.data.ScheduleCycleItemWithTemplate
import com.opelm.unilife.data.ScheduleDayClass
import com.opelm.unilife.data.SchedulePreview
import com.opelm.unilife.data.ScheduleTemplateDao
import com.opelm.unilife.data.ScheduleTemplateWeekEntity
import com.opelm.unilife.data.SubjectDao
import com.opelm.unilife.data.SubjectEntity
import com.opelm.unilife.data.SubjectNoteDao
import com.opelm.unilife.data.SubjectNoteEntity
import com.opelm.unilife.data.TestDao
import com.opelm.unilife.data.TestEntryEntity
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UniLifeRepository(
    private val subjectDao: SubjectDao,
    private val noteDao: SubjectNoteDao,
    private val templateDao: ScheduleTemplateDao,
    private val classDao: ScheduleClassDao,
    private val cycleDao: ScheduleCycleDao,
    private val testDao: TestDao
) {
    private val changeVersion = MutableStateFlow(0)

    fun observeSubjectsWithUsage() = subjectDao.observeSubjectsWithUsage()
    fun observeSubjects() = subjectDao.observeSubjects()
    fun observeNotes(subjectId: Long) = noteDao.observeBySubject(subjectId)
    fun observeTemplates() = templateDao.observeTemplates()
    fun observeTemplateClasses(templateId: Long) = classDao.observeClassesForTemplate(templateId)
    fun observeCycleItems() = cycleDao.observeCycleItems()
    fun observeCycleConfig() = cycleDao.observeCycleConfig()
    fun observeTests(today: LocalDate) = testDao.observeTests(today.toEpochDay())
    fun observeDataVersion() = changeVersion.asStateFlow()

    suspend fun addSubject(name: String) {
        subjectDao.insert(SubjectEntity(name = name.trim()))
        bumpVersion()
    }

    suspend fun updateSubject(subjectId: Long, name: String) {
        val current = subjectDao.getById(subjectId) ?: return
        subjectDao.update(current.copy(name = name.trim()))
        bumpVersion()
    }

    suspend fun tryDeleteSubject(subjectId: Long): DeleteSubjectResult {
        val usage = subjectDao.getSubjectWithUsage(subjectId) ?: return DeleteSubjectResult.NotFound
        if (usage.scheduleUsageCount > 0 || usage.testUsageCount > 0) {
            return DeleteSubjectResult.Blocked(usage.scheduleUsageCount, usage.testUsageCount)
        }
        if (subjectDao.getById(subjectId) != null) {
            subjectDao.deleteById(subjectId)
            bumpVersion()
        }
        return DeleteSubjectResult.Success
    }

    suspend fun addNote(subjectId: Long, title: String, content: String) {
        noteDao.insert(
            SubjectNoteEntity(
                subjectId = subjectId,
                title = title.trim(),
                content = content.trim(),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
        bumpVersion()
    }

    suspend fun updateNote(noteId: Long, title: String, content: String) {
        val note = noteDao.getById(noteId) ?: return
        noteDao.update(
            note.copy(
                title = title.trim(),
                content = content.trim(),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
        bumpVersion()
    }

    suspend fun deleteNote(noteId: Long) {
        noteDao.deleteById(noteId)
        bumpVersion()
    }

    suspend fun addTemplate(name: String) {
        templateDao.insert(ScheduleTemplateWeekEntity(name = name.trim()))
        bumpVersion()
    }

    suspend fun updateTemplate(templateId: Long, name: String) {
        val current = templateDao.getById(templateId) ?: return
        templateDao.update(current.copy(name = name.trim()))
        bumpVersion()
    }

    suspend fun deleteTemplate(templateId: Long) {
        templateDao.deleteById(templateId)
        bumpVersion()
    }

    suspend fun saveClass(
        entryId: Long?,
        templateWeekId: Long,
        subjectId: Long,
        dayOfWeek: Int,
        startMinutes: Int,
        endMinutes: Int,
        location: String,
        note: String
    ) {
        val entity = ScheduleClassEntryEntity(
            id = entryId ?: 0,
            templateWeekId = templateWeekId,
            subjectId = subjectId,
            dayOfWeek = dayOfWeek,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            location = location.trim(),
            note = note.trim()
        )
        if (entryId == null) classDao.insert(entity) else classDao.update(entity)
        bumpVersion()
    }

    suspend fun deleteClass(entryId: Long) {
        classDao.deleteById(entryId)
        bumpVersion()
    }

    suspend fun replaceCycle(templateIds: List<Long>) {
        cycleDao.replaceCycle(
            templateIds.mapIndexed { index, templateId ->
                ScheduleCycleItemEntity(position = index, templateWeekId = templateId)
            }
        )
        bumpVersion()
    }

    suspend fun saveCycleConfig(referenceDate: LocalDate, referencePosition: Int) {
        cycleDao.upsertConfig(
            ScheduleCycleConfigEntity(
                referenceDateEpochDay = referenceDate.toEpochDay(),
                referenceCyclePosition = referencePosition
            )
        )
        bumpVersion()
    }

    suspend fun addOrUpdateTest(id: Long?, subjectId: Long, date: LocalDate, note: String) {
        val entity = TestEntryEntity(
            id = id ?: 0,
            subjectId = subjectId,
            dateEpochDay = date.toEpochDay(),
            note = note.trim()
        )
        if (id == null) testDao.insert(entity) else testDao.update(entity)
        bumpVersion()
    }

    suspend fun deleteTest(testId: Long) {
        testDao.deleteById(testId)
        bumpVersion()
    }

    suspend fun buildSchedulePreview(date: LocalDate): SchedulePreview {
        val cycleItems = cycleDao.getCycleItems()
        val config = cycleDao.getCycleConfig()
        if (cycleItems.isEmpty()) {
            return SchedulePreview(
                date = date,
                templateName = null,
                classes = emptyList(),
                isConfigured = false,
                emptyReason = "Create week templates and a cycle first."
            )
        }
        if (config == null) {
            return SchedulePreview(
                date = date,
                templateName = null,
                classes = emptyList(),
                isConfigured = false,
                emptyReason = "Pick a reference start date to activate the schedule."
            )
        }

        val cycleIndex = calculateCycleIndex(date, cycleItems, config)
        val template = cycleItems.getOrNull(cycleIndex)
        if (template == null) {
            return SchedulePreview(
                date = date,
                templateName = null,
                classes = emptyList(),
                isConfigured = false,
                emptyReason = "The saved cycle configuration no longer matches the template list."
            )
        }

        val testsBySubject = testDao.getTestsForDate(date.toEpochDay())
            .groupBy { it.subjectId }
        val classes = classDao.getClassesForTemplateAndDay(template.templateWeekId, date.dayOfWeek.value)
            .map { classEntry ->
                classEntry.toScheduleDayClass(
                    testNote = testsBySubject[classEntry.subjectId]
                        ?.joinToString(separator = " | ") { it.note }
                )
            }

        return SchedulePreview(
            date = date,
            templateName = template.templateName,
            classes = classes,
            isConfigured = true,
            emptyReason = if (classes.isEmpty()) "No classes planned for this day." else null
        )
    }

    suspend fun getScheduledDatesForSubject(
        subjectId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Set<LocalDate> {
        if (endDate.isBefore(startDate)) return emptySet()

        val cycleItems = cycleDao.getCycleItems()
        val config = cycleDao.getCycleConfig() ?: return emptySet()
        if (cycleItems.isEmpty()) return emptySet()

        val entriesByTemplate = classDao.getEntriesForSubject(subjectId)
            .groupBy { it.templateWeekId }
            .mapValues { (_, entries) -> entries.map { it.dayOfWeek }.toSet() }
        if (entriesByTemplate.isEmpty()) return emptySet()

        val dates = mutableSetOf<LocalDate>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val cycleIndex = calculateCycleIndex(current, cycleItems, config)
            val template = cycleItems.getOrNull(cycleIndex)
            val activeDays = template?.let { entriesByTemplate[it.templateWeekId] }
            if (activeDays?.contains(current.dayOfWeek.value) == true) {
                dates += current
            }
            current = current.plusDays(1)
        }
        return dates
    }

    private fun calculateCycleIndex(
        date: LocalDate,
        cycleItems: List<ScheduleCycleItemWithTemplate>,
        config: ScheduleCycleConfigEntity
    ): Int {
        val diffDays = date.toEpochDay() - config.referenceDateEpochDay
        val diffWeeks = floorDiv(diffDays, 7L).toInt()
        val rawIndex = config.referenceCyclePosition + diffWeeks
        val size = cycleItems.size
        return ((rawIndex % size) + size) % size
    }

    private fun floorDiv(left: Long, right: Long): Long {
        var result = left / right
        if ((left xor right) < 0 && result * right != left) {
            result -= 1
        }
        return result
    }

    private fun ClassWithSubject.toScheduleDayClass(testNote: String?): ScheduleDayClass =
        ScheduleDayClass(
            id = id,
            subjectId = subjectId,
            subjectName = subjectName,
            startTime = LocalTime.of(startMinutes / 60, startMinutes % 60),
            endTime = LocalTime.of(endMinutes / 60, endMinutes % 60),
            location = location,
            note = note,
            testNote = testNote
        )

    private fun bumpVersion() {
        changeVersion.value = changeVersion.value + 1
    }
}

sealed interface DeleteSubjectResult {
    data object Success : DeleteSubjectResult
    data object NotFound : DeleteSubjectResult
    data class Blocked(val classCount: Int, val testCount: Int) : DeleteSubjectResult
}
