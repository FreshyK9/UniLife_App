package com.opelm.unilife.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val room: String
)

@Entity(
    tableName = "subject_notes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class SubjectNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val content: String,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "schedule_template_weeks")
data class ScheduleTemplateWeekEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "schedule_class_entries",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTemplateWeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateWeekId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("templateWeekId"), Index("subjectId")]
)
data class ScheduleClassEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateWeekId: Long,
    val subjectId: Long,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val location: String,
    val note: String
)

@Entity(
    tableName = "schedule_cycle_items",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTemplateWeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateWeekId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateWeekId")]
)
data class ScheduleCycleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val templateWeekId: Long
)

@Entity(tableName = "schedule_cycle_config")
data class ScheduleCycleConfigEntity(
    @PrimaryKey val id: Int = 1,
    val referenceDateEpochDay: Long,
    val referenceCyclePosition: Int
)

@Entity(
    tableName = "tests",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("subjectId")]
)
data class TestEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dateEpochDay: Long,
    val note: String
)
