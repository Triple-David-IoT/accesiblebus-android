package dte.masteriot.accessiblebus.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dte.masteriot.accessiblebus.ui.screens.PlaceholderScreen

// TODO: Replace with the actual navigation routes.(A)

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Route.Profile.route
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
            PlaceholderScreen(
                title = "Driver HMI",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.Preferences.route) {
            PlaceholderScreen(
                title = "Preferences",
                onBack = { navController.popBackStack() }
            )
        }
    }
}