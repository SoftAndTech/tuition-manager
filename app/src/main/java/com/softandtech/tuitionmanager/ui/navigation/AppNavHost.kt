package com.softandtech.tuitionmanager.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.ui.attendance.AttendanceScreen
import com.softandtech.tuitionmanager.ui.batches.BatchDetailScreen
import com.softandtech.tuitionmanager.ui.batches.BatchEditScreen
import com.softandtech.tuitionmanager.ui.batches.BatchesScreen
import com.softandtech.tuitionmanager.ui.home.HomeScreen
import com.softandtech.tuitionmanager.ui.settings.SettingsScreen
import com.softandtech.tuitionmanager.ui.students.StudentDetailScreen
import com.softandtech.tuitionmanager.ui.students.StudentEditScreen
import com.softandtech.tuitionmanager.ui.students.StudentsScreen
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Route argument meaning "create a new record". Room ids start at 1. */
const val NEW_ID = 0L

@Serializable object DashboardRoute
@Serializable object StudentsRoute
@Serializable object BatchesRoute
@Serializable object SettingsRoute

@Serializable data class StudentDetailRoute(val studentId: Long)
@Serializable data class StudentEditRoute(val studentId: Long = NEW_ID)
@Serializable data class BatchDetailRoute(val batchId: Long)
@Serializable data class BatchEditRoute(val batchId: Long = NEW_ID)
/** [epochDay] is the register's date (LocalDate.toEpochDay), defaulting to today. */
@Serializable data class AttendanceRoute(val batchId: Long, val epochDay: Long = LocalDate.now().toEpochDay())

private data class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(DashboardRoute, R.string.nav_dashboard, Icons.Filled.Home),
    TopLevelDestination(StudentsRoute, R.string.nav_students, Icons.Filled.Person),
    TopLevelDestination(BatchesRoute, R.string.nav_batches, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevel = topLevelDestinations.any { currentDestination?.hasRoute(it.route::class) == true }

    Scaffold(
        // Each screen has its own Scaffold + top bar, which handles the status bar inset
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute(dest.route::class) == true,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable<DashboardRoute> {
                HomeScreen(
                    onStudentClick = { navController.navigate(StudentDetailRoute(it)) },
                    onTakeAttendance = { navController.navigate(AttendanceRoute(it)) },
                    onOpenSettings = {
                        navController.navigate(SettingsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<SettingsRoute> { SettingsScreen() }

            composable<StudentsRoute> {
                StudentsScreen(
                    onStudentClick = { navController.navigate(StudentDetailRoute(it)) },
                    onAddStudent = { navController.navigate(StudentEditRoute()) },
                )
            }
            composable<StudentDetailRoute> {
                StudentDetailScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(StudentEditRoute(it)) },
                )
            }
            composable<StudentEditRoute> {
                StudentEditScreen(
                    onBack = navController::popBackStack,
                    onSaved = { id, isNew ->
                        if (isNew) {
                            // Show the new student's page; Back then returns to the list, not the form
                            navController.navigate(StudentDetailRoute(id)) {
                                popUpTo<StudentEditRoute> { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }

            composable<BatchesRoute> {
                BatchesScreen(
                    onBatchClick = { navController.navigate(BatchDetailRoute(it)) },
                    onAddBatch = { navController.navigate(BatchEditRoute()) },
                )
            }
            composable<BatchDetailRoute> {
                BatchDetailScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(BatchEditRoute(it)) },
                    onStudentClick = { navController.navigate(StudentDetailRoute(it)) },
                    onTakeAttendance = { navController.navigate(AttendanceRoute(it)) },
                )
            }
            composable<AttendanceRoute> {
                AttendanceScreen(onBack = navController::popBackStack)
            }
            composable<BatchEditRoute> {
                BatchEditScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                    onArchived = { navController.popBackStack(BatchesRoute, inclusive = false) },
                )
            }
        }
    }
}
