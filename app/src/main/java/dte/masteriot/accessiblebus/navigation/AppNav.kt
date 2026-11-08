package dte.masteriot.accessiblebus.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dte.masteriot.accessiblebus.ui.screens.PlaceholderScreen
import dte.masteriot.accessiblebus.ui.screens.hmi.HmiScreen

// TODO: Replace placeholders with the actual navigation routes.(A)

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Route.DriverHmi.route
    ) {
        composable(Route.Profile.route) {
            PlaceholderScreen(
                title = "Profile",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PassengerStops.route) {
            PlaceholderScreen(
                title = "Passenger Stops",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PassengerStopDetail.route) {
            PlaceholderScreen(
                title = "Stop Details",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PassengerConfirm.route) {
            PlaceholderScreen(
                title = "Confirm Journey",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PassengerTracking.route) {
            PlaceholderScreen(
                title = "Passenger Tracking",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.DriverHmi.route) {
            HmiScreen()
        }

        composable(Route.Preferences.route) {
            PlaceholderScreen(
                title = "Preferences",
                onBack = { navController.popBackStack() }
            )
        }
    }
}