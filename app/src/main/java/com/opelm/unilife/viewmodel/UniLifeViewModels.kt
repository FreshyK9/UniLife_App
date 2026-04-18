package com.opelm.unilife.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.opelm.unilife.data.ClassWithSubject
import com.opelm.unilife.data.ScheduleCycleConfigEntity
import com.opelm.unilife.data.ScheduleCycleItemWithTemplate
import com.opelm.unilife.data.SchedulePreview
import com.opelm.unilife.data.ScheduleTemplateWeekEntity
import com.opelm.unilife.data.SubjectEntity
import com.opelm.unilife.data.SubjectNoteEntity
import com.opelm.unilife.data.SubjectWithUsage
import com.opelm.unilife.data.TestWithSubject
import com.opelm.unilife.repository.DeleteSubjectResult
import com.opelm.unilife.repository.UniLifeRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val repository: UniLifeRepository
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<ScheduleUiState> =
        combine(selectedDate, repository.observeDataVersion()) { date, _ ->
            repository.buildSchedulePreview(date)
        }.map { preview ->
            preview.toUiState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SchedulePreview(
                date = LocalDate.now(),
                templateName = null,
                classes = emptyList(),
                isConfigured = false,
                emptyReason = "Loading schedule..."
            ).toUiState()
        )

    fun goToPreviousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun goToNextDay() {
        selectedDate.value = selectedDate.value.plusDays(1)
    }

    fun goToToday() {
        selectedDate.value = LocalDate.now()
    }

    private fun SchedulePreview.toUiState() = ScheduleUiState(
        date = date,
        templateName = templateName,
        classes = classes,
        isConfigured = isConfigured,
        emptyReason = emptyReason
    )
}

data class ScheduleUiState(
    val date: LocalDate,
    val templateName: String?,
    val classes: List<com.opelm.unilife.data.ScheduleDayClass>,
    val isConfigured: Boolean,
    val emptyReason: String?
)

class ScheduleSetupViewModel(
    private val repository: UniLifeRepository
) : ViewModel() {
    val templates = repository.observeTemplates().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val cycleItems = repository.observeCycleItems().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val cycleConfig = repository.observeCycleConfig().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )
    val subjects = repository.observeSubjects().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var message = MutableStateFlow<String?>(null)
        private set

    fun saveTemplate(name: String, templateId: Long? = null) {
        viewModelScope.launch {
            if (name.isBlank()) {
                message.value = "Template name cannot be empty."
                return@launch
            }
            if (templateId == null) repository.addTemplate(name) else repository.updateTemplate(templateId, name)
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            repository.deleteTemplate(templateId)
        }
    }

    fun saveCycle(templateIds: List<Long>) {
        viewModelScope.launch {
            repository.replaceCycle(templateIds)
            if (templateIds.isEmpty()) {
                message.value = "Add at least one template to the cycle."
            }
        }
    }

    fun saveReferenceDate(referenceDate: LocalDate, cyclePosition: Int) {
        viewModelScope.launch {
            repository.saveCycleConfig(referenceDate, cyclePosition)
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

class TemplateDetailViewModel(
    private val repository: UniLifeRepository,
    private val templateId: Long
) : ViewModel() {
    val templates = repository.observeTemplates().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val templateClasses = repository.observeTemplateClasses(templateId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val subjects = repository.observeSubjects().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var message = MutableStateFlow<String?>(null)
        private set

    fun saveTemplateName(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                message.value = "Template name cannot be empty."
                return@launch
            }
            repository.updateTemplate(templateId, name)
        }
    }

    fun saveClass(
        entryId: Long?,
        subjectId: Long?,
        dayOfWeek: Int,
        startMinutes: Int?,
        endMinutes: Int?,
        location: String,
        note: String
    ) {
        viewModelScope.launch {
            when {
                subjects.value.isEmpty() -> message.value = "Add subjects first before creating classes."
                subjectId == null -> message.value = "Choose a subject for the class."
                startMinutes == null || endMinutes == null -> message.value = "Choose valid start and end times."
                endMinutes <= startMinutes -> message.value = "End time must be after start time."
                else -> repository.saveClass(
                    entryId = entryId,
                    templateWeekId = templateId,
                    subjectId = subjectId,
                    dayOfWeek = dayOfWeek,
                    startMinutes = startMinutes,
                    endMinutes = endMinutes,
                    location = location,
                    note = note
                )
            }
        }
    }

    fun deleteClass(entryId: Long) {
        viewModelScope.launch {
            repository.deleteClass(entryId)
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

class TestsViewModel(
    private val repository: UniLifeRepository
) : ViewModel() {
    val subjects = repository.observeSubjects().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val tests = repository.observeTests(LocalDate.now()).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var message = MutableStateFlow<String?>(null)
        private set

    fun saveTest(id: Long?, subjectId: Long?, date: LocalDate?, note: String) {
        viewModelScope.launch {
            when {
                subjects.value.isEmpty() -> message.value = "Add subjects first before creating tests."
                subjectId == null -> message.value = "Choose a subject."
                date == null -> message.value = "Pick a date."
                note.isBlank() -> message.value = "Add a short description for the test."
                else -> repository.addOrUpdateTest(id, subjectId, date, note)
            }
        }
    }

    fun deleteTest(testId: Long) {
        viewModelScope.launch {
            repository.deleteTest(testId)
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

class SubjectsViewModel(
    private val repository: UniLifeRepository
) : ViewModel() {
    val subjects = repository.observeSubjectsWithUsage().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var message = MutableStateFlow<String?>(null)
        private set

    fun saveSubject(id: Long?, name: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                message.value = "Subject name cannot be empty."
                return@launch
            }
            if (id == null) repository.addSubject(name) else repository.updateSubject(id, name)
        }
    }

    fun deleteSubject(subjectId: Long) {
        viewModelScope.launch {
            when (val result = repository.tryDeleteSubject(subjectId)) {
                DeleteSubjectResult.NotFound -> message.value = "Subject not found."
                DeleteSubjectResult.Success -> Unit
                is DeleteSubjectResult.Blocked -> {
                    val classText = if (result.classCount > 0) "${result.classCount} class entries" else null
                    val testText = if (result.testCount > 0) "${result.testCount} tests" else null
                    message.value = "This subject is still used by ${listOfNotNull(classText, testText).joinToString(" and ")}."
                }
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

class SubjectDetailViewModel(
    private val repository: UniLifeRepository,
    private val subjectId: Long
) : ViewModel() {
    val subjects = repository.observeSubjects().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val notes = repository.observeNotes(subjectId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var message = MutableStateFlow<String?>(null)
        private set

    fun saveNote(noteId: Long?, title: String, content: String) {
        viewModelScope.launch {
            when {
                title.isBlank() -> message.value = "Note title cannot be empty."
                content.isBlank() -> message.value = "Note content cannot be empty."
                noteId == null -> repository.addNote(subjectId, title, content)
                else -> repository.updateNote(noteId, title, content)
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> simpleViewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
