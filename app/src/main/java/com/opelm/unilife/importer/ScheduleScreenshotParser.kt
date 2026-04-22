package com.opelm.unilife.importer

import android.graphics.Rect
import kotlin.math.abs

class ScheduleScreenshotParser {
    private val dayAliases = mapOf(
        "monday" to 1,
        "mon" to 1,
        "poniedzialek" to 1,
        "pon" to 1,
        "tuesday" to 2,
        "tue" to 2,
        "tues" to 2,
        "wtorek" to 2,
        "wt" to 2,
        "wednesday" to 3,
        "wed" to 3,
        "sroda" to 3,
        "sr" to 3,
        "thursday" to 4,
        "thu" to 4,
        "thur" to 4,
        "czwartek" to 4,
        "czw" to 4,
        "friday" to 5,
        "fri" to 5,
        "piatek" to 5,
        "pt" to 5,
        "saturday" to 6,
        "sat" to 6,
        "sobota" to 6,
        "sob" to 6,
        "sunday" to 7,
        "sun" to 7,
        "niedziela" to 7,
        "niedz" to 7,
        "nd" to 7
    )

    fun parse(document: OcrDocument): List<ParsedScheduleDraft> {
        val lines = document.blocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() && it.boundingBox != null }
        if (lines.isEmpty()) return emptyList()

        val dayHeaders = detectDayHeaders(lines)
        if (dayHeaders.isEmpty()) return emptyList()

        val dayColumns = buildDayColumns(dayHeaders)
        val headerBottom = dayHeaders.maxOf { it.line.boundingBox?.bottom ?: 0 }
        val layout = detectLayout(lines, dayHeaders, headerBottom)

        val drafts = when (layout) {
            LayoutType.Grid -> parseGridTimetable(lines, dayColumns, headerBottom, dayHeaders)
            LayoutType.DayCards -> parseDayColumnCards(lines, dayColumns, headerBottom)
        }

        val finalDrafts = if (layout == LayoutType.DayCards && drafts.isEmpty()) {
            parseDayCardsFromText(document.text)
        } else {
            drafts
        }

        return finalDrafts
            .distinctBy {
                listOf(
                    it.dayOfWeek,
                    it.startMinutes,
                    normalize(it.rawSubjectText),
                    normalize(it.suggestedLocation)
                )
            }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }, { it.rawSubjectText.lowercase() }))
    }

    private fun parseGridTimetable(
        lines: List<OcrLine>,
        dayColumns: List<DayColumn>,
        headerBottom: Int,
        dayHeaders: List<DayHeader>
    ): List<ParsedScheduleDraft> {
        val firstDayLeftBoundary = dayHeaders.firstOrNull()?.line?.boundingBox?.left ?: Int.MIN_VALUE
        val explicitRangeEntries = mutableListOf<ParsedScheduleDraft>()
        val consumedLineIds = mutableSetOf<Int>()

        lines.forEachIndexed { index, line ->
            val bounds = line.boundingBox ?: return@forEachIndexed
            if (bounds.top <= headerBottom) return@forEachIndexed
            if (bounds.centerX() < firstDayLeftBoundary) return@forEachIndexed

            val day = findDayForLine(bounds, dayColumns) ?: return@forEachIndexed
            val timeMatch = extractTimeMatch(line.text) ?: return@forEachIndexed

            val remainingText = line.text
                .replace(timeMatch.rawText, " ")
                .replace("-", " ")
                .trim()
            val (subjectText, locationText, noteText) = splitDetectedText(
                listOf(remainingText).filter { it.isNotBlank() }
            )
            if (subjectText.isBlank()) return@forEachIndexed

            explicitRangeEntries += ParsedScheduleDraft(
                rawSubjectText = subjectText,
                suggestedLocation = locationText,
                suggestedNote = noteText,
                dayOfWeek = day,
                startMinutes = timeMatch.startMinutes,
                endMinutes = timeMatch.startMinutes + defaultDurationMinutes,
                confidence = 0.92f,
                warnings = listOf("End time was normalized to a default 90-minute class.")
            )
            consumedLineIds += index
        }

        val timeAxis = detectTimeAxis(lines, dayHeaders)
        val groupedEntries = if (timeAxis.isNotEmpty()) {
            inferEntriesFromGrid(
                lines = lines,
                dayColumns = dayColumns,
                headerBottom = headerBottom,
                firstDayLeftBoundary = firstDayLeftBoundary,
                consumedLineIds = consumedLineIds,
                timeAxis = timeAxis
            )
        } else {
            emptyList()
        }

        return explicitRangeEntries + groupedEntries
    }

    private fun parseDayColumnCards(
        lines: List<OcrLine>,
        dayColumns: List<DayColumn>,
        headerBottom: Int
    ): List<ParsedScheduleDraft> {
        val drafts = mutableListOf<ParsedScheduleDraft>()

        dayColumns.forEach { dayColumn ->
            val dayLines = lines
                .filter { line ->
                    val bounds = line.boundingBox ?: return@filter false
                    bounds.top > headerBottom && findDayForLine(bounds, dayColumns) == dayColumn.dayOfWeek
                }
                .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }

            val daySegments = dayLines.flatMap(::explodeCardLineIntoSegments)
                .sortedBy { it.top }

            var currentCardLines = mutableListOf<String>()
            var currentTimeRange: TimeRangeMatch? = null
            var currentTop = Int.MIN_VALUE

            fun flushCurrentCard() {
                val timeRange = currentTimeRange ?: return
                val contentLines = currentCardLines
                    .map { line -> line.replace(timeRange.rawText, " ").trim() }
                    .filter { it.isNotBlank() && !isStandaloneStatusLine(it) }
                val (subjectText, locationText, noteText) = splitDetectedText(contentLines)
                if (subjectText.isBlank()) return

                drafts += ParsedScheduleDraft(
                    rawSubjectText = subjectText,
                    suggestedLocation = locationText,
                    suggestedNote = noteText,
                    dayOfWeek = dayColumn.dayOfWeek,
                    startMinutes = timeRange.startMinutes,
                    endMinutes = timeRange.endMinutes,
                    confidence = 0.9f,
                    warnings = emptyList()
                )
            }

            daySegments.forEach segmentLoop@ { segment ->
                if (isStandaloneStatusLine(segment.text)) return@segmentLoop

                val segmentTimeRange = extractTimeRange(segment.text)
                val startsNewCard = segmentTimeRange != null ||
                    currentTimeRange == null ||
                    (currentTop != Int.MIN_VALUE && segment.top - currentTop > 26)

                if (startsNewCard) {
                    flushCurrentCard()
                    currentCardLines = mutableListOf(segment.text)
                    currentTimeRange = segmentTimeRange ?: extractTimeRangeFromContext(segment.text)
                    currentTop = segment.top
                } else {
                    currentCardLines += segment.text
                }
            }

            flushCurrentCard()
        }

        return drafts
    }

    private fun parseDayCardsFromText(fullText: String): List<ParsedScheduleDraft> {
        val lines = fullText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val drafts = mutableListOf<ParsedScheduleDraft>()
        var currentDayOfWeek: Int? = null
        var currentTimeRange: TimeRangeMatch? = null
        val currentContent = mutableListOf<String>()

        fun flushCurrent() {
            val day = currentDayOfWeek ?: return
            val timeRange = currentTimeRange ?: return
            val contentLines = currentContent.filterNot(::isStandaloneStatusLine)
            val (subjectText, locationText, noteText) = splitDetectedText(contentLines)
            if (subjectText.isBlank()) return

            drafts += ParsedScheduleDraft(
                rawSubjectText = subjectText,
                suggestedLocation = locationText,
                suggestedNote = noteText,
                dayOfWeek = day,
                startMinutes = timeRange.startMinutes,
                endMinutes = timeRange.endMinutes,
                confidence = 0.72f,
                warnings = listOf("Entry was parsed from OCR text order because layout grouping was uncertain.")
            )
        }

        lines.forEach { line ->
            val detectedDay = detectDayOfWeekFromText(line)
            if (detectedDay != null) {
                flushCurrent()
                currentDayOfWeek = detectedDay
                currentTimeRange = null
                currentContent.clear()
                return@forEach
            }

            if (dateOnlyRegex.matches(line)) {
                return@forEach
            }

            val timeRange = extractTimeRange(line)
            if (timeRange != null) {
                flushCurrent()
                currentTimeRange = timeRange
                currentContent.clear()
                currentContent += line
                return@forEach
            }

            if (currentTimeRange != null) {
                currentContent += line
            }
        }

        flushCurrent()
        return drafts
    }

    private fun detectLayout(
        lines: List<OcrLine>,
        dayHeaders: List<DayHeader>,
        headerBottom: Int
    ): LayoutType {
        val firstDayLeftBoundary = dayHeaders.firstOrNull()?.line?.boundingBox?.left ?: Int.MAX_VALUE
        val leftAxisTimes = lines.count { line ->
            val bounds = line.boundingBox ?: return@count false
            bounds.top > headerBottom &&
                bounds.centerX() < firstDayLeftBoundary &&
                extractSingleTime(line.text) != null
        }
        val inlineTimeRanges = lines.count { line ->
            val bounds = line.boundingBox ?: return@count false
            bounds.top > headerBottom &&
                bounds.centerX() >= firstDayLeftBoundary &&
                extractTimeRange(line.text) != null
        }

        return if (leftAxisTimes >= 3 && leftAxisTimes >= inlineTimeRanges) {
            LayoutType.Grid
        } else {
            LayoutType.DayCards
        }
    }

    private fun detectDayHeaders(lines: List<OcrLine>): List<DayHeader> {
        val candidates = lines.mapNotNull { line ->
            val normalized = normalize(line.text)
            val day = dayAliases[normalized]
                ?: dayAliases.entries.firstOrNull { normalized.contains(it.key) }?.value
            day?.let { DayHeader(dayOfWeek = it, line = line) }
        }.sortedBy { it.line.boundingBox?.top ?: Int.MAX_VALUE }

        if (candidates.isEmpty()) return emptyList()

        // Timetable headers usually sit on the same top row, so we keep only the
        // highest cluster of weekday labels and ignore matching text deeper in the grid.
        val topBand = (candidates.first().line.boundingBox?.top ?: 0) + 160
        return candidates
            .filter { (it.line.boundingBox?.top ?: Int.MAX_VALUE) <= topBand }
            .groupBy { it.dayOfWeek }
            .mapNotNull { (_, entries) -> entries.minByOrNull { it.line.boundingBox?.top ?: Int.MAX_VALUE } }
            .sortedBy { it.line.boundingBox?.centerX() ?: 0 }
    }

    private fun buildDayColumns(headers: List<DayHeader>): List<DayColumn> {
        if (headers.isEmpty()) return emptyList()
        return headers.mapIndexed { index, header ->
            val left = if (index == 0) {
                Int.MIN_VALUE
            } else {
                ((headers[index - 1].line.boundingBox?.centerX() ?: 0) + (header.line.boundingBox?.centerX() ?: 0)) / 2
            }
            val right = if (index == headers.lastIndex) {
                Int.MAX_VALUE
            } else {
                ((header.line.boundingBox?.centerX() ?: 0) + (headers[index + 1].line.boundingBox?.centerX() ?: 0)) / 2
            }
            DayColumn(header.dayOfWeek, left, right)
        }
    }

    private fun detectTimeAxis(lines: List<OcrLine>, dayHeaders: List<DayHeader>): List<TimeAxisMark> {
        val firstDayCenter = dayHeaders.firstOrNull()?.line?.boundingBox?.centerX() ?: Int.MAX_VALUE
        val candidates = lines.mapNotNull { line ->
            val time = extractSingleTime(line.text) ?: return@mapNotNull null
            val bounds = line.boundingBox ?: return@mapNotNull null
            val centerX = bounds.centerX()
            if (centerX >= firstDayCenter) return@mapNotNull null
            TimeAxisMark(minutes = time, centerY = bounds.centerY())
        }

        return candidates
            .sortedBy { it.centerY }
            .distinctBy { it.minutes }
    }

    private fun inferEntriesFromGrid(
        lines: List<OcrLine>,
        dayColumns: List<DayColumn>,
        headerBottom: Int,
        firstDayLeftBoundary: Int,
        consumedLineIds: Set<Int>,
        timeAxis: List<TimeAxisMark>
    ): List<ParsedScheduleDraft> {
        val groups = mutableMapOf<Pair<Int, Int>, MutableList<OcrLine>>()

        lines.forEachIndexed { index, line ->
            if (index in consumedLineIds) return@forEachIndexed

            val bounds = line.boundingBox ?: return@forEachIndexed
            if (bounds.top <= headerBottom) return@forEachIndexed
            if (bounds.centerX() < firstDayLeftBoundary) return@forEachIndexed
            if (extractSingleTime(line.text) != null || extractTimeMatch(line.text) != null) return@forEachIndexed
            if (isStandaloneStatusLine(line.text)) return@forEachIndexed

            val day = findDayForLine(bounds, dayColumns) ?: return@forEachIndexed
            val slotIndex = inferSlotIndex(bounds.centerY(), timeAxis) ?: return@forEachIndexed
            groups.getOrPut(day to slotIndex) { mutableListOf() }.add(line)
        }

        return groups.mapNotNull { (key, groupLines) ->
            val slotIndex = key.second
            val start = timeAxis.getOrNull(slotIndex)?.minutes ?: return@mapNotNull null
            val orderedTexts = groupLines
                .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                .map { it.text.trim() }
                .filter { it.isNotBlank() && !isStandaloneStatusLine(it) }
            val (subjectText, locationText, noteText) = splitDetectedText(orderedTexts)
            if (subjectText.isBlank()) return@mapNotNull null

            ParsedScheduleDraft(
                rawSubjectText = subjectText,
                suggestedLocation = locationText,
                suggestedNote = noteText,
                dayOfWeek = key.first,
                startMinutes = start,
                endMinutes = start + defaultDurationMinutes,
                confidence = 0.66f,
                warnings = listOf("Time was inferred from nearby timetable rows. End time was normalized to 90 minutes.")
            )
        }
    }

    private fun splitDetectedText(lines: List<String>): Triple<String, String, String> {
        if (lines.isEmpty()) return Triple("", "", "")

        val cleaned = lines
            .map { it.trim() }
            .filter { it.isNotBlank() && !isStandaloneStatusLine(it) }
        if (cleaned.isEmpty()) return Triple("", "", "")

        val classType = detectClassType(cleaned)

        val locationTokens = cleaned.flatMap { extractRoomTokens(it) }.distinct()
        val locationLine = cleaned.firstOrNull(::looksLikeLocation).orEmpty()
        val location = (locationTokens.firstOrNull() ?: locationLine).trim()

        val subjectCandidates = cleaned
            .map { stripRoomTokens(stripMetaDescriptors(it)).trim(' ', ',', '|', '-', '/') }
            .filter { it.isNotBlank() && !looksLikeLocation(it) && !isStandaloneStatusLine(it) }

        val baseSubjectLine = subjectCandidates.firstOrNull().orEmpty()
        val subjectLine = when {
            baseSubjectLine.isBlank() -> ""
            classType == null -> baseSubjectLine
            containsClassType(baseSubjectLine, classType) -> baseSubjectLine
            else -> "$baseSubjectLine ($classType)"
        }
        val note = subjectCandidates
            .drop(1)
            .joinToString(" | ")

        return Triple(subjectLine, location, note)
    }

    private fun detectClassType(lines: List<String>): String? {
        val normalizedLines = lines.map(::normalize)
        return when {
            normalizedLines.any { it.contains("tutorial") || it.contains("cwiczenia") } -> "Tutorial"
            normalizedLines.any { it.contains("lecture") || it.contains("wyklad") } -> "Lecture"
            else -> null
        }
    }

    private fun containsClassType(subjectText: String, classType: String): Boolean {
        val normalizedSubject = normalize(subjectText)
        val normalizedType = normalize(classType)
        return normalizedSubject.contains(normalizedType)
    }

    private fun stripMetaDescriptors(text: String): String =
        metaDescriptorRegex.replace(text, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    private fun extractRoomTokens(text: String): List<String> =
        roomTokenRegex.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()

    private fun stripRoomTokens(text: String): String =
        roomTokenRegex.replace(text, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    private fun looksLikeLocation(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.contains("room") || normalized.contains("sala") || normalized.contains("aud")) {
            return true
        }
        if (extractRoomTokens(text).isNotEmpty()) {
            return true
        }
        val compact = text.replace(" ", "")
        return compact.length <= 14 && compact.any { it.isDigit() } && compact.any { it.isLetter() }
    }

    private fun isStandaloneStatusLine(text: String): Boolean {
        val normalized = normalize(text)
        return normalized in ignoredStatusTokens
    }

    private fun detectDayOfWeekFromText(text: String): Int? {
        val normalized = normalize(text)
        return dayAliases[normalized]
            ?: dayAliases.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    private fun findDayForLine(bounds: Rect, dayColumns: List<DayColumn>): Int? =
        dayColumns.firstOrNull { bounds.centerX() >= it.leftBoundary && bounds.centerX() < it.rightBoundary }?.dayOfWeek

    private fun explodeCardLineIntoSegments(line: OcrLine): List<CardTextSegment> {
        val text = line.text.trim()
        if (text.isBlank()) return emptyList()

        val matches = timeRangeRegex.findAll(text).toList()
        if (matches.size <= 1) {
            return listOf(
                CardTextSegment(
                    text = text,
                    top = line.boundingBox?.top ?: Int.MAX_VALUE
                )
            )
        }

        val segments = mutableListOf<CardTextSegment>()
        matches.forEachIndexed { index, match ->
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val chunk = text.substring(match.range.first, nextStart).trim()
            if (chunk.isNotBlank()) {
                segments += CardTextSegment(
                    text = chunk,
                    top = (line.boundingBox?.top ?: Int.MAX_VALUE) + index
                )
            }
        }
        return segments
    }

    private fun extractTimeRangeFromContext(text: String): TimeRangeMatch? = extractTimeRange(text)

    private fun inferSlotIndex(centerY: Int, timeAxis: List<TimeAxisMark>): Int? {
        if (timeAxis.isEmpty()) return null

        // When class cells do not contain their own times, we map text blocks into the
        // nearest row interval derived from the left-side time axis.
        for (index in timeAxis.indices) {
            val top = if (index == 0) {
                Int.MIN_VALUE
            } else {
                (timeAxis[index - 1].centerY + timeAxis[index].centerY) / 2
            }
            val bottom = if (index == timeAxis.lastIndex) {
                Int.MAX_VALUE
            } else {
                (timeAxis[index].centerY + timeAxis[index + 1].centerY) / 2
            }
            if (centerY in top until bottom) {
                return index
            }
        }

        val nearest = timeAxis.minByOrNull { abs(it.centerY - centerY) } ?: return null
        return timeAxis.indexOf(nearest).takeIf { it >= 0 }
    }

    private fun extractSingleTime(text: String): Int? {
        val matches = timeRegex.findAll(text).toList()
        if (matches.size != 1) return null
        return parseTime(matches.first().value)
    }

    private fun extractTimeMatch(text: String): TimeMatch? {
        val range = extractTimeRange(text)
        if (range != null) {
            return TimeMatch(
                startMinutes = range.startMinutes,
                rawText = range.rawText
            )
        }

        val matches = timeRegex.findAll(text).toList()
        if (matches.isEmpty()) return null

        val startMinutes = parseTime(matches.first().value) ?: return null
        return TimeMatch(
            startMinutes = startMinutes,
            rawText = matches.first().value
        )
    }

    private fun extractTimeRange(text: String): TimeRangeMatch? {
        val rangeMatch = timeRangeRegex.find(text)
        if (rangeMatch != null) {
            val startMinutes = parseTime(rangeMatch.groupValues[1]) ?: return null
            val endMinutes = parseTime(rangeMatch.groupValues[4]) ?: return null
            if (endMinutes <= startMinutes) return null
            return TimeRangeMatch(
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                rawText = rangeMatch.value
            )
        }

        val matches = timeRegex.findAll(text).toList()
        if (matches.size < 2) return null
        val startMinutes = parseTime(matches.first().value) ?: return null
        val endMinutes = parseTime(matches.last().value) ?: return null
        if (endMinutes <= startMinutes) return null
        return TimeRangeMatch(
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            rawText = text.substring(matches.first().range.first, matches.last().range.last + 1)
        )
    }

    private fun parseTime(raw: String): Int? {
        val parts = raw.replace('.', ':').split(":")
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return hour * 60 + minute
    }

    private fun normalize(text: String): String = buildString(text.length) {
        text.lowercase().forEach { char ->
            append(
                when (char) {
                    '\u0105' -> 'a'
                    '\u0107' -> 'c'
                    '\u0119' -> 'e'
                    '\u0142' -> 'l'
                    '\u0144' -> 'n'
                    '\u00f3' -> 'o'
                    '\u015b' -> 's'
                    '\u017c', '\u017a' -> 'z'
                    else -> char
                }
            )
        }
    }.replace(Regex("[^a-z0-9]+"), "")

    private enum class LayoutType {
        Grid,
        DayCards
    }

    private data class DayHeader(
        val dayOfWeek: Int,
        val line: OcrLine
    )

    private data class DayColumn(
        val dayOfWeek: Int,
        val leftBoundary: Int,
        val rightBoundary: Int
    )

    private data class TimeAxisMark(
        val minutes: Int,
        val centerY: Int
    )

    private data class TimeMatch(
        val startMinutes: Int,
        val rawText: String
    )

    private data class TimeRangeMatch(
        val startMinutes: Int,
        val endMinutes: Int,
        val rawText: String
    )

    private data class CardTextSegment(
        val text: String,
        val top: Int
    )

    companion object {
        private const val defaultDurationMinutes = 90
        private val timeRegex = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
        private val timeRangeRegex = Regex("""\b(([01]?\d|2[0-3])[:.]([0-5]\d))\s*[-–]\s*(([01]?\d|2[0-3])[:.]([0-5]\d))\b""")
        private val dateOnlyRegex = Regex("""^\d{1,2}[./-]\d{1,2}$""")
        private val roomTokenRegex = Regex("""\b(?:[A-Za-z]/)?(?:Aula\s*)?[A-Za-z]?\s*/\s*\d{2,4}[A-Za-z]?\b|\bAula\s*\d{2,4}\b|\b[A-Za-z]\s*/\s*[A-Za-z]?\d{2,4}[A-Za-z]?\b""")
        private val metaDescriptorRegex = Regex("""\b(?:tutorial|lecture|wyklad|cwiczenia|exercise|presence|cancelled|classcancelled|notmarked)\b""", RegexOption.IGNORE_CASE)
        private val ignoredStatusTokens = setOf(
            "presence",
            "notmarked",
            "classcancelled",
            "cancelled",
            "nieoznaczono",
            "obecnosc"
        )
    }
}
