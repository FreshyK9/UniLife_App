package com.opelm.unilife.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
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
import com.opelm.unilife.data.SubjectWithUsage
import com.opelm.unilife.ui.components.AppDialogScaffold
import com.opelm.unilife.ui.components.ConfirmDeleteDialog
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.viewmodel.SubjectsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    viewModel: SubjectsViewModel,
    onOpenSubject: (Long) -> Unit
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingSubject by remember { mutableStateOf<SubjectWithUsage?>(null) }
    var deletingSubject by remember { mutableStateOf<SubjectWithUsage?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Subjects") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingSubject = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add subject")
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
            if (subjects.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No subjects yet",
                        message = "Create subjects first. Schedule classes, tests, and notes all link back to them."
                    )
                }
            } else {
                items(subjects, key = { it.id }) { subject ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSubject(subject.id) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(subject.name, style = MaterialTheme.typography.titleMedium)
                            val usageText = buildList {
                                if (subject.scheduleUsageCount > 0) add("${subject.scheduleUsageCount} classes")
                                if (subject.testUsageCount > 0) add("${subject.testUsageCount} tests")
                            }.ifEmpty { listOf("No linked schedule or tests yet") }.joinToString(" | ")
                            Text(usageText, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = {
                                    editingSubject = subject
                                    showEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit subject")
                                }
                                IconButton(onClick = { deletingSubject = subject }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete subject")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        SubjectEditorDialog(
            existing = editingSubject,
            onDismiss = { showEditor = false },
            onSave = { id, name ->
                viewModel.saveSubject(id, name)
                showEditor = false
            }
        )
    }

    if (deletingSubject != null) {
        ConfirmDeleteDialog(
            title = "Delete subject",
            text = "Notes will be removed too. If this subject is still linked to schedule classes or tests, deletion will be blocked.",
            onDismiss = { deletingSubject = null },
            onConfirm = {
                viewModel.deleteSubject(deletingSubject!!.id)
                deletingSubject = null
            }
        )
    }
}

@Composable
private fun SubjectEditorDialog(
    existing: SubjectWithUsage?,
    onDismiss: () -> Unit,
    onSave: (Long?, String) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    AppDialogScaffold(
        title = if (existing == null) "Add subject" else "Edit subject",
        onDismiss = onDismiss,
        onConfirm = { onSave(existing?.id, name) },
        confirmLabel = "Save"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Subject name") }
        )
    }
}
