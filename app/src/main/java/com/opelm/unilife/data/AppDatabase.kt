package com.opelm.unilife.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        SubjectNoteEntity::class,
        ScheduleTemplateWeekEntity::class,
        ScheduleClassEntryEntity::class,
        ScheduleCycleItemEntity::class,
        ScheduleCycleConfigEntity::class,
        TestEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun subjectNoteDao(): SubjectNoteDao
    abstract fun scheduleTemplateDao(): ScheduleTemplateDao
    abstract fun scheduleClassDao(): ScheduleClassDao
    abstract fun scheduleCycleDao(): ScheduleCycleDao
    abstract fun testDao(): TestDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "unilife.db"
            ).fallbackToDestructiveMigration().build()
    }
}
