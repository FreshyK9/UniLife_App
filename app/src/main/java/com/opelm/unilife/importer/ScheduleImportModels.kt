package com.opelm.unilife.importer

import android.graphics.Rect

data class OcrWord(
    val text: String,
    val boundingBox: Rect?
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val words: List<OcrWord>
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrLine>
)

data class OcrDocument(
    val text: String,
    val blocks: List<OcrBlock>
)

data class ParsedScheduleDraft(
    val rawSubjectText: String,
    val suggestedLocation: String,
    val suggestedNote: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val confidence: Float,
    val warnings: List<String>
)

data class ScheduleImportParseOutput(
    val document: OcrDocument,
    val drafts: List<ParsedScheduleDraft>
)
