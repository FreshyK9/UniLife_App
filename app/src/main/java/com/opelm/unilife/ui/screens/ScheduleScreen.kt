package com.opelm.unilife.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opelm.unilife.ui.components.EmptyStateCard
import com.opelm.unilife.viewmodel.ScheduleViewModel
import java.time.format.DateTimeFormatter

private val scheduleDateFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
private val scheduleTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text("Schedule") })
        }
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.date.format(scheduleDateFormatter),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Week template: ${state.templateName ?: "Not configured"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = viewModel::goToPreviousDay) { Text("Previous day") }
                            TextButton(onClick = viewModel::goToToday) { Text("Today") }
                            TextButton(onClick = viewModel::goToNextDay) { Text("Next day") }
                        }
                    }
                }
            }

            if (state.classes.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = if (state.isConfigured) "Nothing scheduled" else "Schedule not ready",
                        message = state.emptyReason ?: "No classes found for this day."
                    )
                }
            } else {
                items(state.classes, key = { it.id }) { classItem ->
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
                            Text(classItem.subjectName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${classItem.startTime.format(scheduleTimeFormatter)} - ${classItem.endTime.format(scheduleTimeFormatter)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (classItem.location.isNotBlank()) {
                                Text("Room: ${classItem.location}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (classItem.note.isNotBlank()) {
                                Text(classItem.note, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!classItem.testNote.isNullOrBlank()) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Text(
                                    text = "Test today",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = classItem.testNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
