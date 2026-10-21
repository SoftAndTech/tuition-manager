package com.softandtech.tuitionmanager.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
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
import com.softandtech.tuitionmanager.ui.common.PlaceholderScreen
import com.softandtech.tuitionmanager.ui.students.StudentsScreen
import kotlinx.serialization.Serializable

@Serializable object DashboardRoute
@Serializable object StudentsRoute
@Serializable object BatchesRoute
@Serializable object SettingsRoute

private data class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(DashboardRoute, R.string.nav_dashboard, Icons.Filled.Home),
    TopLevelDestination(StudentsRoute, R.string.nav_students, Icons.Filled.Person),
    TopLevelDestination(BatchesRoute, R.string.nav_batches, Icons.Filled.List),
    TopLevelDestination(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<DashboardRoute> { PlaceholderScreen(R.string.nav_dashboard) }
            composable<StudentsRoute> { StudentsScreen() }
            composable<BatchesRoute> { PlaceholderScreen(R.string.nav_batches) }
            composable<SettingsRoute> { PlaceholderScreen(R.string.nav_settings) }
        }
    }
}
