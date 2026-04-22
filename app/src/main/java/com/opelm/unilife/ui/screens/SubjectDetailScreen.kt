package com.opelm.unilife.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.opelm.unilife.data.SubjectNoteEntity
import com.opelm.unilife.ui.components.AppDialogScaffold
import com.opelm.unilife.ui.components.ConfirmDeleteDialog
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.viewmodel.SubjectDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val noteUpdatedFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    viewModel: SubjectDetailViewModel,
    subjectId: Long,
    onBack: () -> Unit
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val subject = subjects.firstOrNull { it.id == subjectId }
    val subjectName = subject?.name ?: "Subject"
    val snackbarHostState = remember { SnackbarHostState() }
    var editingNote by remember { mutableStateOf<SubjectNoteEntity?>(null) }
    var deletingNote by remember { mutableStateOf<SubjectNoteEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

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
                title = { Text(subjectName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingNote = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Text(subjectName, style = MaterialTheme.typography.headlineSmall)
                        subject?.room?.takeIf { it.isNotBlank() }?.let { room ->
                            Text("Room: $room", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            if (notes.isEmpty()) "No notes yet. Use this space for lecture summaries, revision prompts, and quick reminders."
                            else "${notes.size} notes stored for this subject.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (notes.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No notes yet",
                        message = "Keep lecture summaries, homework reminders, or revision notes inside this subject."
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Instant.ofEpochMilli(note.updatedAtEpochMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .format(noteUpdatedFormatter),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(note.content, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = {
                                    editingNote = note
                                    showEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit note")
                                }
                                IconButton(onClick = { deletingNote = note }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete note")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        NoteEditorDialog(
            existing = editingNote,
            onDismiss = { showEditor = false },
            onSave = { noteId, title, content ->
                viewModel.saveNote(noteId, title, content)
                showEditor = false
            }
        )
    }

    if (deletingNote != null) {
        ConfirmDeleteDialog(
            title = "Delete note",
            text = "Remove this note permanently?",
            onDismiss = { deletingNote = null },
            onConfirm = {
                viewModel.deleteNote(deletingNote!!.id)
                deletingNote = null
            }
        )
    }
}

@Composable
private fun NoteEditorDialog(
    existing: SubjectNoteEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String) -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember(existing) { mutableStateOf(existing?.content.orEmpty()) }

    AppDialogScaffold(
        title = if (existing == null) "Add note" else "Edit note",
        onDismiss = onDismiss,
        onConfirm = { onSave(existing?.id, title, content) },
        confirmLabel = "Save"
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title") }
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Content") },
            minLines = 6
        )
    }
}
