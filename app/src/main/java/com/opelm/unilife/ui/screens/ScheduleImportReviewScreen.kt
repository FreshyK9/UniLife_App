package com.opelm.unilife.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opelm.unilife.ui.components.AppDialogScaffold
import com.opelm.unilife.ui.components.AppPillButton
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.ui.components.SimpleDropdownField
import com.opelm.unilife.ui.components.TimeField
import com.opelm.unilife.ui.components.dayLabel
import com.opelm.unilife.viewmodel.EditableImportEntry
import com.opelm.unilife.viewmodel.ScheduleImportViewModel
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleImportReviewScreen(
    viewModel: ScheduleImportViewModel,
    onBack: () -> Unit,
    onImportFinished: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingEntry by remember { mutableStateOf<EditableImportEntry?>(null) }

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
                title = { Text("Import Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::addManualEntry) {
                Icon(Icons.Default.Add, contentDescription = "Add class manually")
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
                ImportSummaryCard(
                    imageUri = state.imageUri,
                    pendingNameCount = state.pendingNameCount,
                    autoCreateCount = state.autoCreateCount,
                    templateOptions = state.templates.map { it.id to it.name },
                    selectedTemplateId = state.selectedTemplateId,
                    replaceExisting = state.replaceExisting,
                    onTemplateSelected = viewModel::selectTemplate,
                    onReplaceExistingChanged = viewModel::setReplaceExisting,
                    canSave = state.canSave,
                    onSave = { viewModel.saveImport(onImportFinished) }
                )
            }

            when {
                state.isLoading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Running OCR and preparing a draft timetable...")
                            }
                        }
                    }
                }
                state.subjects.isEmpty() -> {
                    item {
                        EmptyStateCard(
                            title = "No subjects yet",
                            message = "This import can create new subjects automatically from the detected OCR names."
                        )
                    }
                }
                state.error != null -> {
                    item {
                        EmptyStateCard(
                            title = "Import needs review",
                            message = state.error ?: ""
                        )
                    }
                    state.ocrTextPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                        item { OcrTextPreviewCard(preview) }
                    }
                }
            }

            if (!state.isLoading && state.entries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No draft classes yet",
                        message = "Add missing classes manually or try a clearer timetable screenshot."
                    )
                }
            }

            items(state.entries, key = { it.id }) { entry ->
                ImportEntryCard(
                    entry = entry,
                    subjects = state.subjects.map { it.id to it.name },
                    onEdit = { editingEntry = entry },
                    onDelete = { viewModel.deleteEntry(entry.id) }
                )
            }
        }
    }

    if (editingEntry != null) {
        ImportEntryEditorDialog(
            entry = editingEntry!!,
            subjectOptions = state.subjects.map { it.id to it.name },
            onDismiss = { editingEntry = null },
            onSave = {
                viewModel.updateEntry(it)
                editingEntry = null
            }
        )
    }
}

@Composable
private fun ImportSummaryCard(
    imageUri: String,
    pendingNameCount: Int,
    autoCreateCount: Int,
    templateOptions: List<Pair<Long, String>>,
    selectedTemplateId: Long?,
    replaceExisting: Boolean,
    onTemplateSelected: (Long) -> Unit,
    onReplaceExistingChanged: (Boolean) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Review OCR draft", style = MaterialTheme.typography.titleLarge)
            Text(
                "Nothing is saved automatically. Review every detected class before importing.",
                style = MaterialTheme.typography.bodyMedium
            )
            ScreenshotPreview(imageUri = imageUri)
            if (templateOptions.isEmpty()) {
                Text("Create a week template first, then return to import this screenshot.")
            } else {
                SimpleDropdownField(
                    label = "Import into week template",
                    options = templateOptions,
                    selectedId = selectedTemplateId,
                    onSelected = onTemplateSelected
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Replace existing classes in this template")
                Checkbox(
                    checked = replaceExisting,
                    onCheckedChange = onReplaceExistingChanged
                )
            }
            if (autoCreateCount > 0) {
                Text(
                    text = "$autoCreateCount entries will create new subjects automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (pendingNameCount > 0) {
                Text(
                    text = "$pendingNameCount entries still need a subject name or mapping.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            AppPillButton(
                label = "Confirm import",
                onClick = onSave,
                enabled = canSave
            )
        }
    }
}

@Composable
private fun ImportEntryCard(
    entry: EditableImportEntry,
    subjects: List<Pair<Long, String>>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mappedSubjectName = subjects.firstOrNull { it.first == entry.subjectId }?.second
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = mappedSubjectName ?: entry.detectedSubjectText.ifBlank { "Unnamed subject" },
                style = MaterialTheme.typography.titleMedium
            )
            if (entry.detectedSubjectText.isNotBlank()) {
                Text("OCR text: ${entry.detectedSubjectText}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${dayLabel(entry.dayOfWeek)} | ${entry.startTimeLabel()} - ${entry.endTimeLabel()}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (entry.location.isNotBlank()) {
                Text("Room: ${entry.location}", style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.warnings.isNotEmpty()) {
                Text(
                    entry.warnings.joinToString(" "),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Parser confidence: ${(entry.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit imported class")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete imported class")
                }
            }
        }
    }
}

@Composable
private fun ImportEntryEditorDialog(
    entry: EditableImportEntry,
    subjectOptions: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onSave: (EditableImportEntry) -> Unit
) {
    var detectedSubjectText by remember(entry) { mutableStateOf(entry.detectedSubjectText) }
    var selectedSubjectId by remember(entry) { mutableStateOf(entry.subjectId) }
    var selectedDay by remember(entry) { mutableStateOf(entry.dayOfWeek) }
    var startTime by remember(entry) {
        mutableStateOf(LocalTime.of(entry.startMinutes / 60, entry.startMinutes % 60))
    }
    var endTime by remember(entry) {
        mutableStateOf(LocalTime.of(entry.endMinutes / 60, entry.endMinutes % 60))
    }
    var location by remember(entry) { mutableStateOf(entry.location) }
    var note by remember(entry) { mutableStateOf(entry.note) }

    AppDialogScaffold(
        title = "Review class",
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                entry.copy(
                    detectedSubjectText = detectedSubjectText,
                    subjectId = selectedSubjectId,
                    dayOfWeek = selectedDay,
                    startMinutes = startTime.hour * 60 + startTime.minute,
                    endMinutes = endTime.hour * 60 + endTime.minute,
                    location = location,
                    note = note
                )
            )
        },
        confirmLabel = "Save"
    ) {
        OutlinedTextField(
            value = detectedSubjectText,
            onValueChange = { detectedSubjectText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Subject text") }
        )
        SimpleDropdownField(
            label = "Map to existing subject",
            options = subjectOptions,
            selectedId = selectedSubjectId,
            onSelected = { selectedSubjectId = it }
        )
        SimpleDropdownField(
            label = "Day",
            options = (1L..7L).map { it to dayLabel(it.toInt()) },
            selectedId = selectedDay.toLong(),
            onSelected = { selectedDay = it.toInt() }
        )
        TimeField(
            label = "Start time",
            value = startTime,
            onValueChange = { startTime = it }
        )
        TimeField(
            label = "End time",
            value = endTime,
            onValueChange = { endTime = it }
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Room / location") }
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

@Composable
private fun OcrTextPreviewCard(preview: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("OCR text preview", style = MaterialTheme.typography.titleMedium)
            Text(preview, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScreenshotPreview(imageUri: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, imageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(imageUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Selected screenshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

private fun EditableImportEntry.startTimeLabel(): String =
    "%02d:%02d".format(startMinutes / 60, startMinutes % 60)

private fun EditableImportEntry.endTimeLabel(): String =
    "%02d:%02d".format(endMinutes / 60, endMinutes % 60)
