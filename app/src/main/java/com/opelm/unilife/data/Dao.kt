package com.opelm.unilife.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query(
        """
        SELECT s.id, s.name, s.room,
        (SELECT COUNT(*) FROM schedule_class_entries c WHERE c.subjectId = s.id) AS scheduleUsageCount,
        (SELECT COUNT(*) FROM tests t WHERE t.subjectId = s.id) AS testUsageCount
        FROM subjects s
        ORDER BY LOWER(s.name)
        """
    )
    fun observeSubjectsWithUsage(): Flow<List<SubjectWithUsage>>

    @Query("SELECT * FROM subjects ORDER BY LOWER(name)")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :subjectId")
    suspend fun deleteById(subjectId: Long)

    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    suspend fun getById(subjectId: Long): SubjectEntity?

    @Query(
        """
        SELECT s.id, s.name, s.room,
        (SELECT COUNT(*) FROM schedule_class_entries c WHERE c.subjectId = s.id) AS scheduleUsageCount,
        (SELECT COUNT(*) FROM tests t WHERE t.subjectId = s.id) AS testUsageCount
        FROM subjects s
        WHERE s.id = :subjectId
        LIMIT 1
        """
    )
    suspend fun getSubjectWithUsage(subjectId: Long): SubjectWithUsage?
}

@Dao
interface SubjectNoteDao {
    @Query("SELECT * FROM subject_notes WHERE subjectId = :subjectId ORDER BY updatedAtEpochMillis DESC")
    fun observeBySubject(subjectId: Long): Flow<List<SubjectNoteEntity>>

    @Query("SELECT * FROM subject_notes WHERE id = :noteId LIMIT 1")
    suspend fun getById(noteId: Long): SubjectNoteEntity?

    @Insert
    suspend fun insert(note: SubjectNoteEntity): Long

    @Update
    suspend fun update(note: SubjectNoteEntity)

    @Query("DELETE FROM subject_notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)
}

@Dao
interface ScheduleTemplateDao {
    @Query("SELECT * FROM schedule_template_weeks ORDER BY LOWER(name)")
    fun observeTemplates(): Flow<List<ScheduleTemplateWeekEntity>>

    @Insert
    suspend fun insert(template: ScheduleTemplateWeekEntity): Long

    @Update
    suspend fun update(template: ScheduleTemplateWeekEntity)

    @Query("DELETE FROM schedule_template_weeks WHERE id = :templateId")
    suspend fun deleteById(templateId: Long)

    @Query("SELECT * FROM schedule_template_weeks WHERE id = :templateId LIMIT 1")
    suspend fun getById(templateId: Long): ScheduleTemplateWeekEntity?
}

@Dao
interface ScheduleClassDao {
    @Query(
        """
        SELECT c.id, c.templateWeekId, c.subjectId, s.name AS subjectName, s.room AS subjectRoom, c.dayOfWeek,
               c.startMinutes, c.endMinutes, c.location, c.note
        FROM schedule_class_entries c
        INNER JOIN subjects s ON s.id = c.subjectId
        WHERE c.templateWeekId = :templateWeekId
        ORDER BY c.dayOfWeek, c.startMinutes, c.endMinutes
        """
    )
    fun observeClassesForTemplate(templateWeekId: Long): Flow<List<ClassWithSubject>>

    @Insert
    suspend fun insert(entry: ScheduleClassEntryEntity): Long

    @Update
    suspend fun update(entry: ScheduleClassEntryEntity)

    @Query("DELETE FROM schedule_class_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    @Query(
        """
        SELECT c.id, c.templateWeekId, c.subjectId, s.name AS subjectName, s.room AS subjectRoom, c.dayOfWeek,
               c.startMinutes, c.endMinutes, c.location, c.note
        FROM schedule_class_entries c
        INNER JOIN subjects s ON s.id = c.subjectId
        WHERE c.templateWeekId = :templateWeekId AND c.dayOfWeek = :dayOfWeek
        ORDER BY c.startMinutes, c.endMinutes
        """
    )
    suspend fun getClassesForTemplateAndDay(templateWeekId: Long, dayOfWeek: Int): List<ClassWithSubject>

    @Query("SELECT * FROM schedule_class_entries WHERE id = :entryId LIMIT 1")
    suspend fun getById(entryId: Long): ScheduleClassEntryEntity?

    @Query("SELECT * FROM schedule_class_entries WHERE subjectId = :subjectId")
    suspend fun getEntriesForSubject(subjectId: Long): List<ScheduleClassEntryEntity>
}

@Dao
interface ScheduleCycleDao {
    @Query(
        """
        SELECT i.id, i.position, i.templateWeekId, t.name AS templateName
        FROM schedule_cycle_items i
        INNER JOIN schedule_template_weeks t ON t.id = i.templateWeekId
        ORDER BY i.position
        """
    )
    fun observeCycleItems(): Flow<List<ScheduleCycleItemWithTemplate>>

    @Query("SELECT * FROM schedule_cycle_config WHERE id = 1 LIMIT 1")
    fun observeCycleConfig(): Flow<ScheduleCycleConfigEntity?>

    @Query(
        """
        SELECT i.id, i.position, i.templateWeekId, t.name AS templateName
        FROM schedule_cycle_items i
        INNER JOIN schedule_template_weeks t ON t.id = i.templateWeekId
        ORDER BY i.position
        """
    )
    suspend fun getCycleItems(): List<ScheduleCycleItemWithTemplate>

    @Query("SELECT * FROM schedule_cycle_config WHERE id = 1 LIMIT 1")
    suspend fun getCycleConfig(): ScheduleCycleConfigEntity?

    @Insert
    suspend fun insertCycleItem(item: ScheduleCycleItemEntity): Long

    @Query("DELETE FROM schedule_cycle_items")
    suspend fun clearCycleItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ScheduleCycleConfigEntity)

    @Transaction
    suspend fun replaceCycle(items: List<ScheduleCycleItemEntity>) {
        clearCycleItems()
        items.sortedBy { it.position }.forEach { insertCycleItem(it) }
    }
}

@Dao
interface TestDao {
    @Query(
        """
        SELECT t.id, t.subjectId, s.name AS subjectName, t.dateEpochDay, t.note
        FROM tests t
        INNER JOIN subjects s ON s.id = t.subjectId
        ORDER BY CASE WHEN t.dateEpochDay >= :todayEpochDay THEN 0 ELSE 1 END,
                 ABS(t.dateEpochDay - :todayEpochDay),
                 t.dateEpochDay
        """
    )
    fun observeTests(todayEpochDay: Long): Flow<List<TestWithSubject>>

    @Insert
    suspend fun insert(entry: TestEntryEntity): Long

    @Update
    suspend fun update(entry: TestEntryEntity)

    @Query("DELETE FROM tests WHERE id = :testId")
    suspend fun deleteById(testId: Long)

    @Query("SELECT * FROM tests WHERE id = :testId LIMIT 1")
    suspend fun getById(testId: Long): TestEntryEntity?

    @Query(
        """
        SELECT t.id, t.subjectId, s.name AS subjectName, t.dateEpochDay, t.note
        FROM tests t
        INNER JOIN subjects s ON s.id = t.subjectId
        WHERE t.dateEpochDay = :dateEpochDay
        ORDER BY LOWER(s.name), t.id
        """
    )
    suspend fun getTestsForDate(dateEpochDay: Long): List<TestWithSubject>
}
