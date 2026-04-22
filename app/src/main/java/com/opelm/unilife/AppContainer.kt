package com.opelm.unilife

import android.content.Context
import com.opelm.unilife.data.AppDatabase
import com.opelm.unilife.importer.ScheduleScreenshotImportProcessor
import com.opelm.unilife.importer.ScheduleScreenshotParser
import com.opelm.unilife.repository.UniLifeRepository

class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    val repository = UniLifeRepository(
        subjectDao = database.subjectDao(),
        noteDao = database.subjectNoteDao(),
        templateDao = database.scheduleTemplateDao(),
        classDao = database.scheduleClassDao(),
        cycleDao = database.scheduleCycleDao(),
        testDao = database.testDao()
    )
    val scheduleImportProcessor = ScheduleScreenshotImportProcessor(
        context = context.applicationContext,
        parser = ScheduleScreenshotParser()
    )
}
