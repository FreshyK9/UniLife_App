package com.opelm.unilife.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opelm.unilife.repository.UniLifeRepository
import com.opelm.unilife.ui.screens.ScheduleScreen
import com.opelm.unilife.ui.screens.ScheduleSetupScreen
import com.opelm.unilife.ui.screens.SubjectDetailScreen
import com.opelm.unilife.ui.screens.SubjectsScreen
import com.opelm.unilife.ui.screens.TemplateDetailScreen
import com.opelm.unilife.ui.screens.TestsScreen
import com.opelm.unilife.viewmodel.ScheduleSetupViewModel
import com.opelm.unilife.viewmodel.ScheduleViewModel
import com.opelm.unilife.viewmodel.SubjectDetailViewModel
import com.opelm.unilife.viewmodel.SubjectsViewModel
import com.opelm.unilife.viewmodel.TemplateDetailViewModel
import com.opelm.unilife.viewmodel.TestsViewModel
import com.opelm.unilife.viewmodel.simpleViewModelFactory

sealed class AppDestination(val route: String, val label: String) {
    data object Schedule : AppDestination("schedule", "Schedule")
    data object Setup : AppDestination("setup", "Setup")
    data object Tests : AppDestination("tests", "Tests")
    data object Subjects : AppDestination("subjects", "Subjects")
    data object SubjectDetail : AppDestination("subject/{subjectId}", "Subject")
    data object TemplateDetail : AppDestination("template/{templateId}", "Template")
}

private data class BottomItem(
    val destination: AppDestination,
    val icon: @Composable () -> Unit
)

@Composable
fun UniLifeApp(repository: UniLifeRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val bottomItems = listOf(
        BottomItem(AppDestination.Schedule) { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
        BottomItem(AppDestination.Setup) { Icon(Icons.Default.EditCalendar, contentDescription = null) },
        BottomItem(AppDestination.Tests) { Icon(Icons.Default.Book, contentDescription = null) },
        BottomItem(AppDestination.Subjects) { Icon(Icons.Default.School, contentDescription = null) }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp
            ) {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == item.destination.route } == true,
                        onClick = {
                            navController.navigate(item.destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = item.icon,
                        label = { Text(item.destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Schedule.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppDestination.Schedule.route) {
                val vm: ScheduleViewModel = viewModel(factory = simpleViewModelFactory {
                    ScheduleViewModel(repository)
                })
                ScheduleScreen(viewModel = vm)
            }
            composable(AppDestination.Setup.route) {
                val vm: ScheduleSetupViewModel = viewModel(factory = simpleViewModelFactory {
                    ScheduleSetupViewModel(repository)
                })
                ScheduleSetupScreen(
                    viewModel = vm,
                    onOpenTemplate = { templateId ->
                        navController.navigate("template/$templateId")
                    }
                )
            }
            composable(AppDestination.Tests.route) {
                val vm: TestsViewModel = viewModel(factory = simpleViewModelFactory {
                    TestsViewModel(repository)
                })
                TestsScreen(viewModel = vm)
            }
            composable(AppDestination.Subjects.route) {
                val vm: SubjectsViewModel = viewModel(factory = simpleViewModelFactory {
                    SubjectsViewModel(repository)
                })
                SubjectsScreen(
                    viewModel = vm,
                    onOpenSubject = { subjectId ->
                        navController.navigate("subject/$subjectId")
                    }
                )
            }
            composable(AppDestination.SubjectDetail.route) { entry ->
                val subjectId = entry.arguments?.getString("subjectId")?.toLongOrNull() ?: return@composable
                val vm: SubjectDetailViewModel = viewModel(
                    key = "subject-$subjectId",
                    factory = simpleViewModelFactory {
                        SubjectDetailViewModel(repository, subjectId)
                    }
                )
                SubjectDetailScreen(
                    viewModel = vm,
                    subjectId = subjectId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppDestination.TemplateDetail.route) { entry ->
                val templateId = entry.arguments?.getString("templateId")?.toLongOrNull() ?: return@composable
                val vm: TemplateDetailViewModel = viewModel(
                    key = "template-$templateId",
                    factory = simpleViewModelFactory {
                        TemplateDetailViewModel(repository, templateId)
                    }
                )
                TemplateDetailScreen(
                    viewModel = vm,
                    templateId = templateId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
