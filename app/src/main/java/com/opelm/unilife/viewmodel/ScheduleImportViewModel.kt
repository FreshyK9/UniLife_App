package com.opelm.unilife.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opelm.unilife.data.ScheduleTemplateWeekEntity
import com.opelm.unilife.data.SubjectEntity
import com.opelm.unilife.importer.ParsedScheduleDraft
import com.opelm.unilife.importer.ScheduleScreenshotImportProcessor
import com.opelm.unilife.repository.ImportedScheduleClass
import com.opelm.unilife.repository.UniLifeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleImportViewModel(
    private val repository: UniLifeRepository,
    private val processor: ScheduleScreenshotImportProcessor,
    imageUri: String
) : ViewModel() {
    private val importState = MutableStateFlow(ScheduleImportInternalState(imageUri = imageUri))

    val templates = repository.observeTemplates().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val subjects = repository.observeSubjects().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val uiState: StateFlow<ScheduleImportUiState> = combine(importState, templates, subjects) { state, templates, subjects ->
        val selectedTemplateId = state.selectedTemplateId ?: templates.firstOrNull()?.id
        val entries = state.entries
        val pendingNameCount = entries.count { it.subjectId == null && it.detectedSubjectText.isBlank() }
        val autoCreateCount = entries.count { it.subjectId == null && it.detectedSubjectText.isNotBlank() }

        ScheduleImportUiState(
            imageUri = state.imageUri,
            isLoading = state.isLoading,
            error = state.error,
            ocrTextPreview = state.ocrTextPreview,
            templates = templates,
            subjects = subjects,
            selectedTemplateId = selectedTemplateId,
            replaceExisting = state.replaceExisting,
            entries = entries,
            pendingNameCount = pendingNameCount,
            autoCreateCount = autoCreateCount,
            canSave = !state.isLoading &&
                state.error == null &&
                templates.isNotEmpty() &&
                entries.isNotEmpty() &&
                pendingNameCount == 0 &&
                selectedTemplateId != null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScheduleImportUiState(imageUri = imageUri)
    )

    init {
        processImageIfNeeded()
    }

    var message = MutableStateFlow<String?>(null)
        private set

    private fun processImageIfNeeded() {
        if (importState.value.hasProcessed) return

        importState.value = importState.value.copy(isLoading = true, hasProcessed = true)
        viewModelScope.launch {
            try {
                val output = processor.process(Uri.parse(importState.value.imageUri))
                importState.value = importState.value.copy(
                    isLoading = false,
                    entries = output.drafts.mapIndexed { index, draft ->
                        draft.toEditableEntry(index.toLong(), subjects.value)
                    },
                    ocrTextPreview = output.document.text.take(1200).ifBlank { null },
                    error = when {
                        output.document.text.isBlank() -> "No text was detected in this screenshot."
                        output.drafts.isEmpty() -> "OCR ran, but no timetable entries could be inferred. You can still add entries manually."
                        else -> null
                    }
                )
            } catch (error: Exception) {
                importState.value = importState.value.copy(
                    isLoading = false,
                    error = error.message ?: "The screenshot could not be processed."
                )
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun selectTemplate(templateId: Long) {
        importState.value = importState.value.copy(selectedTemplateId = templateId)
    }

    fun setReplaceExisting(replaceExisting: Boolean) {
        importState.value = importState.value.copy(replaceExisting = replaceExisting)
    }

    fun updateEntry(updated: EditableImportEntry) {
        val normalizedEntry = updated.withSubjectStatusWarning()
        importState.value = importState.value.copy(
            entries = importState.value.entries.map { if (it.id == updated.id) normalizedEntry else it }
        )
    }

    fun deleteEntry(entryId: Long) {
        importState.value = importState.value.copy(
            entries = importState.value.entries.filterNot { it.id == entryId }
        )
    }

    fun addManualEntry() {
        val nextId = (importState.value.entries.maxOfOrNull { it.id } ?: -1L) + 1L
        val defaultSubjectId = subjects.value.firstOrNull()?.id
        importState.value = importState.value.copy(
            error = null,
            entries = importState.value.entries + EditableImportEntry(
                id = nextId,
                detectedSubjectText = "",
                subjectId = defaultSubjectId,
                dayOfWeek = 1,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60 + 30,
                location = "",
                note = "",
                confidence = 1f,
                warnings = emptyList()
            ).withSubjectStatusWarning()
        )
    }

    fun saveImport(onSuccess: () -> Unit) {
        val currentState = uiState.value
        when {
            currentState.templates.isEmpty() -> {
                message.value = "Create a week template before importing."
                return
            }
            currentState.selectedTemplateId == null -> {
                message.value = "Choose a target week template."
                return
            }
            currentState.entries.isEmpty() -> {
                message.value = "There are no entries to import."
                return
            }
            currentState.entries.any { it.subjectId == null && it.detectedSubjectText.isBlank() } -> {
                message.value = "Each class needs either an existing subject mapping or a subject name to create."
                return
            }
            currentState.entries.any { it.endMinutes <= it.startMinutes } -> {
                message.value = "Each class must end after it starts."
                return
            }
        }

        viewModelScope.launch {
            val createdSubjectIds = mutableMapOf<String, Long>()
            val classesToImport = currentState.entries.map { entry ->
                val subjectId = entry.subjectId ?: run {
                    val subjectName = entry.detectedSubjectText.trim()
                    val cacheKey = normalize(subjectName)
                    createdSubjectIds.getOrPut(cacheKey) {
                        repository.ensureSubjectForImport(
                            name = subjectName,
                            roomHint = entry.location
                        )
                    }
                }

                ImportedScheduleClass(
                    subjectId = subjectId,
                    dayOfWeek = entry.dayOfWeek,
                    startMinutes = entry.startMinutes,
                    endMinutes = entry.endMinutes,
                    location = entry.location,
                    note = entry.note
                )
            }

            repository.importScheduleClasses(
                templateWeekId = requireNotNull(currentState.selectedTemplateId),
                classes = classesToImport,
                replaceExisting = currentState.replaceExisting
            )
            message.value = "Imported ${currentState.entries.size} classes."
            onSuccess()
        }
    }

    private fun ParsedScheduleDraft.toEditableEntry(
        entryId: Long,
        subjects: List<SubjectEntity>
    ): EditableImportEntry {
        val suggestedSubjectId = suggestSubjectId(rawSubjectText, subjects)
        return EditableImportEntry(
            id = entryId,
            detectedSubjectText = rawSubjectText,
            subjectId = suggestedSubjectId,
            dayOfWeek = dayOfWeek,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            location = suggestedLocation,
            note = suggestedNote,
            confidence = confidence,
            warnings = warnings
        ).withSubjectStatusWarning()
    }

    private fun suggestSubjectId(text: String, subjects: List<SubjectEntity>): Long? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null

        val exact = subjects.firstOrNull { normalize(it.name) == normalized }
        if (exact != null) return exact.id
        return null
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

    private fun EditableImportEntry.withSubjectStatusWarning(): EditableImportEntry {
        val unresolvedWarning = "Enter a subject name or map this class to an existing subject before saving."
        val autoCreateWarning = "A new subject will be created from this text on import."
        val nextWarnings = warnings
            .filterNot { it == unresolvedWarning || it == autoCreateWarning }
            .toMutableList()

        when {
            subjectId != null -> Unit
            detectedSubjectText.isBlank() -> nextWarnings += unresolvedWarning
            else -> nextWarnings += autoCreateWarning
        }

        return copy(warnings = nextWarnings)
    }
}

data class ScheduleImportUiState(
    val imageUri: String,
    val isLoading: Boolean = false,
    val error: String? = null,
    val ocrTextPreview: String? = null,
    val templates: List<ScheduleTemplateWeekEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val selectedTemplateId: Long? = null,
    val replaceExisting: Boolean = false,
    val entries: List<EditableImportEntry> = emptyList(),
    val pendingNameCount: Int = 0,
    val autoCreateCount: Int = 0,
    val canSave: Boolean = false
)

data class EditableImportEntry(
    val id: Long,
    val detectedSubjectText: String,
    val subjectId: Long?,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val location: String,
    val note: String,
    val confidence: Float,
    val warnings: List<String>
)

private data class ScheduleImportInternalState(
    val imageUri: String,
    val isLoading: Boolean = false,
    val hasProcessed: Boolean = false,
    val error: String? = null,
    val ocrTextPreview: String? = null,
    val entries: List<EditableImportEntry> = emptyList(),
    val selectedTemplateId: Long? = null,
    val replaceExisting: Boolean = false
)
