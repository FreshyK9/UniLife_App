package com.opelm.unilife.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.opelm.unilife.data.SubjectEntity
import com.opelm.unilife.data.TestWithSubject
import com.opelm.unilife.ui.components.AppDialogScaffold
import com.opelm.unilife.ui.components.DateField
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.ui.components.SimpleDropdownField
import com.opelm.unilife.viewmodel.TestsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val testDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(viewModel: TestsViewModel) {
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingTest by remember { mutableStateOf<TestWithSubject?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingTest by remember { mutableStateOf<TestWithSubject?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Test Tracker") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTest = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add test")
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
                        title = "Subjects required",
                        message = "Add at least one subject before creating tests."
                    )
                }
            } else if (tests.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No tests yet",
                        message = "Track your next kolosy and exams here."
                    )
                }
            } else {
                items(tests, key = { it.id }) { test ->
                    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(test.subjectName, style = MaterialTheme.typography.titleMedium)
                            Text(test.date.format(testDateFormatter), style = MaterialTheme.typography.bodyMedium)
                            Text(test.note, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = {
                                    editingTest = test
                                    showEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit test")
                                }
                                IconButton(onClick = { deletingTest = test }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete test")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        TestEditorDialog(
            subjects = subjects,
            existing = editingTest,
            onDismiss = { showEditor = false },
            onSave = { id, subjectId, date, note ->
                viewModel.saveTest(id, subjectId, date, note)
                showEditor = false
            }
        )
    }

    if (deletingTest != null) {
        com.opelm.unilife.ui.components.ConfirmDeleteDialog(
            title = "Delete test",
            text = "Remove this test entry?",
            onDismiss = { deletingTest = null },
            onConfirm = {
                viewModel.deleteTest(deletingTest!!.id)
                deletingTest = null
            }
        )
    }
}

@Composable
private fun TestEditorDialog(
    subjects: List<SubjectEntity>,
    existing: TestWithSubject?,
    onDismiss: () -> Unit,
    onSave: (Long?, Long?, LocalDate?, String) -> Unit
) {
    var selectedSubjectId by remember(existing, subjects) {
        mutableStateOf(existing?.subjectId ?: subjects.firstOrNull()?.id)
    }
    var selectedDate by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }

    AppDialogScaffold(
        title = if (existing == null) "Add test" else "Edit test",
        onDismiss = onDismiss,
        onConfirm = { onSave(existing?.id, selectedSubjectId, selectedDate, note) },
        confirmLabel = "Save"
    ) {
        SimpleDropdownField(
            label = "Subject",
            options = subjects.map { it.id to it.name },
            selectedId = selectedSubjectId,
            onSelected = { selectedSubjectId = it }
        )
        DateField(
            label = "Date",
            value = selectedDate,
            onValueChange = { selectedDate = it }
        )
        androidx.compose.material3.OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
            minLines = 3
        )
    }
}
