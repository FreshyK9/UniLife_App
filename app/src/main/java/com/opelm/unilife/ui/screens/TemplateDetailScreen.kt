package com.opelm.unilife.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opelm.unilife.data.ClassWithSubject
import com.opelm.unilife.data.SubjectEntity
import com.opelm.unilife.ui.components.AppDialogScaffold
import com.opelm.unilife.ui.components.ConfirmDeleteDialog
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.ui.components.SimpleDropdownField
import com.opelm.unilife.ui.components.dayLabel
import com.opelm.unilife.viewmodel.TemplateDetailViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import androidx.compose.ui.text.input.KeyboardType

private val classTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val weekDays = (1..7).toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailScreen(
    viewModel: TemplateDetailViewModel,
    templateId: Long,
    onBack: () -> Unit
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val templateClasses by viewModel.templateClasses.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val templateName = templates.firstOrNull { it.id == templateId }?.name ?: "Week Template"
    var showClassEditor by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<ClassWithSubject?>(null) }
    var deletingClass by remember { mutableStateOf<ClassWithSubject?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(templateName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename template")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingClass = null
                showClassEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add class")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(templateName, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${templateClasses.size} classes across ${templateClasses.map { it.dayOfWeek }.distinct().size} active days in this week template.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (subjects.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Subjects required",
                        message = "Add subjects before creating classes in this template."
                    )
                }
            }

            weekDays.forEach { day ->
                val dayEntries = templateClasses.filter { it.dayOfWeek == day }
                item(key = "day-$day") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(dayLabel(day), style = MaterialTheme.typography.titleMedium)
                            if (dayEntries.isEmpty()) {
                                Text("No classes set for this day.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                dayEntries.forEach { entry ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(entry.subjectName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${LocalTime.of(entry.startMinutes / 60, entry.startMinutes % 60).format(classTimeFormatter)} - ${
                                                    LocalTime.of(entry.endMinutes / 60, entry.endMinutes % 60).format(classTimeFormatter)
                                                }",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            val displayLocation = entry.location.ifBlank { entry.subjectRoom }
                                            if (displayLocation.isNotBlank()) {
                                                Text("Room: $displayLocation", style = MaterialTheme.typography.bodyMedium)
                                            }
                                            if (entry.note.isNotBlank()) {
                                                Text(entry.note, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                IconButton(onClick = {
                                                    editingClass = entry
                                                    showClassEditor = true
                                                }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit class")
                                                }
                                                IconButton(onClick = { deletingClass = entry }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete class")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClassEditor) {
        ClassEditorDialog(
            subjects = subjects,
            existing = editingClass,
            onDismiss = { showClassEditor = false },
            onSave = { id, subjectId, day, start, end, location, note ->
                viewModel.saveClass(
                    entryId = id,
                    subjectId = subjectId,
                    dayOfWeek = day,
                    startMinutes = start?.let { it.hour * 60 + it.minute },
                    endMinutes = end?.let { it.hour * 60 + it.minute },
                    location = location,
                    note = note,
                    onSuccess = { showClassEditor = false }
                )
            }
        )
    }

    if (showRenameDialog) {
        RenameTemplateDialog(
            initialName = templateName,
            onDismiss = { showRenameDialog = false },
            onSave = {
                viewModel.saveTemplateName(it) {
                    showRenameDialog = false
                }
            }
        )
    }

    if (deletingClass != null) {
        ConfirmDeleteDialog(
            title = "Delete class",
            text = "Remove this class from the week template?",
            onDismiss = { deletingClass = null },
            onConfirm = {
                viewModel.deleteClass(deletingClass!!.id)
                deletingClass = null
            }
        )
    }
}

@Composable
private fun ClassEditorDialog(
    subjects: List<SubjectEntity>,
    existing: ClassWithSubject?,
    onDismiss: () -> Unit,
    onSave: (Long?, Long?, Int, LocalTime?, LocalTime?, String, String) -> Unit
) {
    var selectedSubjectId by remember(existing, subjects) {
        mutableStateOf(existing?.subjectId ?: subjects.firstOrNull()?.id)
    }
    var selectedDay by remember(existing) { mutableStateOf(existing?.dayOfWeek ?: 1) }
    var startTimeText by remember(existing) {
        mutableStateOf(existing?.let { LocalTime.of(it.startMinutes / 60, it.startMinutes % 60).format(classTimeFormatter) }.orEmpty())
    }
    var endTimeText by remember(existing) {
        mutableStateOf(existing?.let { LocalTime.of(it.endMinutes / 60, it.endMinutes % 60).format(classTimeFormatter) }.orEmpty())
    }
    var location by remember(existing) { mutableStateOf(existing?.location.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }

    AppDialogScaffold(
        title = if (existing == null) "Add class" else "Edit class",
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                existing?.id,
                selectedSubjectId,
                selectedDay,
                startTimeText.toLocalTimeOrNull(),
                endTimeText.toLocalTimeOrNull(),
                location,
                note
            )
        },
        confirmLabel = "Save"
    ) {
        SimpleDropdownField(
            label = "Subject",
            options = subjects.map { it.id to it.name },
            selectedId = selectedSubjectId,
            onSelected = { selectedSubjectId = it }
        )
        SimpleDropdownField(
            label = "Day",
            options = weekDays.map { it.toLong() to dayLabel(it) },
            selectedId = selectedDay.toLong(),
            onSelected = { selectedDay = it.toInt() }
        )
        OutlinedTextField(
            value = startTimeText,
            onValueChange = { startTimeText = it.filterTimeInput() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Start time") },
            placeholder = { Text("08:30") },
            supportingText = { Text("Enter time as HH:mm") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = endTimeText,
            onValueChange = { endTimeText = it.filterTimeInput() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("End time") },
            placeholder = { Text("10:00") },
            supportingText = { Text("Enter time as HH:mm") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Override room / location") },
            supportingText = { Text("Leave blank to use the subject room.") }
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            minLines = 2
        )
    }
}

private fun String.filterTimeInput(): String =
    filter { it.isDigit() || it == ':' }.take(5)

private fun String.toLocalTimeOrNull(): LocalTime? =
    try {
        LocalTime.parse(trim(), classTimeFormatter)
    } catch (_: DateTimeParseException) {
        null
    }

@Composable
private fun RenameTemplateDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AppDialogScaffold(
        title = "Rename template",
        onDismiss = onDismiss,
        onConfirm = { onSave(name) },
        confirmLabel = "Save"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Template name") }
        )
    }
}
