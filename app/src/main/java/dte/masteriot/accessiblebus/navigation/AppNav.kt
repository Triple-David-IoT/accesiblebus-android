package dte.masteriot.accessiblebus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dte.masteriot.accessiblebus.AccessibleBusApplication
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.ui.screens.PlaceholderScreen
import dte.masteriot.accessiblebus.ui.screens.PreferencesScreen
import dte.masteriot.accessiblebus.ui.screens.ProfileScreen
import dte.masteriot.accessiblebus.ui.screens.hmi.HmiScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// TODO: Replace placeholders with the actual navigation routes.(A)

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    val container =
        (context.applicationContext as AccessibleBusApplication).container

    val configRepository = container.appConfigRepository
    val mqttRepository = container.repository

    val coroutineScope = rememberCoroutineScope()

    // Opens Preferences with the active connection profile.
    val openPreferences: () -> Unit = {
        coroutineScope.launch {
            val profile = configRepository.activeProfile().first()
            navController.navigate(Route.Preferences.createRoute(profile))
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Profile.route
    ) {
        composable(Route.Profile.route) {
            ProfileScreen(
                onPassengerSelected = {
                    navController.navigate(Route.PassengerStops.route)
                },
                onDriverSelected = {
                    navController.navigate(Route.DriverHmi.route)
                }
            )
        }

        composable(Route.PassengerStops.route) {
            PlaceholderScreen(
                title = "Passenger Stops",
                onBack = { navController.popBackStack() },
                onPreferences = openPreferences
            )
        }

        composable(Route.PassengerStopDetail.route) {
            PlaceholderScreen(
                title = "Stop Details",
                onBack = { navController.popBackStack() },
                onPreferences = openPreferences
            )
        }

        composable(Route.PassengerConfirm.route) {
            PlaceholderScreen(
                title = "Confirm Journey",
                onBack = { navController.popBackStack() },
                onPreferences = openPreferences
            )
        }

        composable(Route.PassengerTracking.route) {
            PlaceholderScreen(
                title = "Passenger Tracking",
                onBack = { navController.popBackStack() },
                onPreferences = openPreferences
            )
        }

        composable(Route.DriverHmi.route) {
            HmiScreen()
        }

        composable(
            route = Route.Preferences.route,
            arguments = listOf(
                navArgument("profile") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val profileName =
                backStackEntry.arguments?.getString("profile")

            val profile = ConnectionProfile.valueOf(
                profileName ?: ConnectionProfile.LABORATORY.name
            )

            PreferencesScreen(
                profile = profile,
                repository = configRepository,
                mqttRepository = mqttRepository,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
