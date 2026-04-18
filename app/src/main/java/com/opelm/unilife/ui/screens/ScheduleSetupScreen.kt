package com.opelm.unilife.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opelm.unilife.data.ScheduleCycleItemWithTemplate
import com.opelm.unilife.data.ScheduleTemplateWeekEntity
import com.opelm.unilife.ui.components.AppDialogScaffold
import com.opelm.unilife.ui.components.ConfirmDeleteDialog
import com.opelm.unilife.ui.components.DateField
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.ui.components.SimpleDropdownField
import com.opelm.unilife.viewmodel.ScheduleSetupViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val setupDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSetupScreen(
    viewModel: ScheduleSetupViewModel,
    onOpenTemplate: (Long) -> Unit
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val cycleItems by viewModel.cycleItems.collectAsStateWithLifecycle()
    val cycleConfig by viewModel.cycleConfig.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ScheduleTemplateWeekEntity?>(null) }
    var deletingTemplate by remember { mutableStateOf<ScheduleTemplateWeekEntity?>(null) }
    var selectedTemplateToAppend by remember(templates) { mutableStateOf(templates.firstOrNull()?.id) }
    val editableCycle = remember { mutableStateListOf<Long>() }

    LaunchedEffect(cycleItems) {
        editableCycle.clear()
        editableCycle.addAll(cycleItems.map { it.templateWeekId })
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Schedule Setup") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTemplate = null
                showTemplateEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add template")
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Week Templates", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Create reusable week layouts first. Each class must link to an existing subject.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (templates.isEmpty()) {
                            Text("No templates yet.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            itemsIndexed(templates, key = { _, item -> item.id }) { _, template ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTemplate(template.id) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(template.name, style = MaterialTheme.typography.titleMedium)
                        Text("Tap to manage classes for this week template.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = {
                                editingTemplate = template
                                showTemplateEditor = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit template")
                            }
                            IconButton(onClick = { deletingTemplate = template }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete template")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Cycle Order", style = MaterialTheme.typography.titleLarge)
                        if (templates.isEmpty()) {
                            Text("Create templates before building a repeating cycle.")
                        } else {
                            SimpleDropdownField(
                                label = "Append template",
                                options = templates.map { it.id to it.name },
                                selectedId = selectedTemplateToAppend,
                                onSelected = { selectedTemplateToAppend = it }
                            )
                            TextButton(onClick = {
                                selectedTemplateToAppend?.let { editableCycle.add(it) }
                            }) {
                                Text("Add to cycle")
                            }
                        }

                        if (editableCycle.isEmpty()) {
                            Text("No cycle order saved yet.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            editableCycle.forEachIndexed { index, templateId ->
                                val templateName = templates.firstOrNull { it.id == templateId }?.name ?: "Missing template"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${index + 1}. $templateName", modifier = Modifier.weight(1f))
                                    Row {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val current = editableCycle[index]
                                                    editableCycle[index] = editableCycle[index - 1]
                                                    editableCycle[index - 1] = current
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < editableCycle.lastIndex) {
                                                    val current = editableCycle[index]
                                                    editableCycle[index] = editableCycle[index + 1]
                                                    editableCycle[index + 1] = current
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                                        }
                                        IconButton(onClick = { editableCycle.removeAt(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove from cycle")
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { viewModel.saveCycle(editableCycle.toList()) }) {
                                Text("Save cycle")
                            }
                        }
                    }
                }
            }

            item {
                ReferenceSetupCard(
                    cycleItems = cycleItems,
                    cycleConfig = cycleConfig,
                    onSave = viewModel::saveReferenceDate
                )
            }

            if (subjects.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Subjects still needed",
                        message = "Classes cannot be added until subjects exist."
                    )
                }
            }
        }
    }

    if (showTemplateEditor) {
        TemplateEditorDialog(
            existing = editingTemplate,
            onDismiss = { showTemplateEditor = false },
            onSave = { id, name ->
                viewModel.saveTemplate(name, id)
                showTemplateEditor = false
            }
        )
    }

    if (deletingTemplate != null) {
        ConfirmDeleteDialog(
            title = "Delete template",
            text = "This removes the template and all classes inside it. Any cycle items pointing to it will also disappear.",
            onDismiss = { deletingTemplate = null },
            onConfirm = {
                viewModel.deleteTemplate(deletingTemplate!!.id)
                deletingTemplate = null
            }
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    existing: ScheduleTemplateWeekEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, String) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    AppDialogScaffold(
        title = if (existing == null) "Add week template" else "Edit week template",
        onDismiss = onDismiss,
        onConfirm = { onSave(existing?.id, name) },
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

@Composable
private fun ReferenceSetupCard(
    cycleItems: List<ScheduleCycleItemWithTemplate>,
    cycleConfig: com.opelm.unilife.data.ScheduleCycleConfigEntity?,
    onSave: (LocalDate, Int) -> Unit
) {
    var referenceDate by remember(cycleConfig) {
        mutableStateOf(cycleConfig?.let { LocalDate.ofEpochDay(it.referenceDateEpochDay) } ?: LocalDate.now())
    }
    var referencePosition by remember(cycleConfig, cycleItems) {
        mutableStateOf(
            cycleConfig?.referenceCyclePosition?.takeIf { it in cycleItems.indices } ?: 0
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Reference Start", style = MaterialTheme.typography.titleLarge)
            if (cycleItems.isEmpty()) {
                Text("Save a cycle order first.")
            } else {
                DateField(
                    label = "Reference date",
                    value = referenceDate,
                    onValueChange = { referenceDate = it }
                )
                SimpleDropdownField(
                    label = "Template active on that date",
                    options = cycleItems.mapIndexed { index, item ->
                        index.toLong() to "${index + 1}. ${item.templateName}"
                    },
                    selectedId = referencePosition.toLong(),
                    onSelected = { referencePosition = it.toInt() }
                )
                if (cycleConfig != null) {
                    Text(
                        "Current reference: ${LocalDate.ofEpochDay(cycleConfig.referenceDateEpochDay).format(setupDateFormatter)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { onSave(referenceDate, referencePosition) }) {
                    Text("Save reference")
                }
            }
        }
    }
}
