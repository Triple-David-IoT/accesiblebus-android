package dte.masteriot.accessiblebus.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.ui.screens.PlaceholderScreen
import dte.masteriot.accessiblebus.ui.screens.ProfileScreen

// TODO: Replace placeholders with the actual navigation routes.(A)

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Route.Profile.route
    ) {
        composable(Route.Profile.route) {
            ProfileScreen(
                onSelectProfile = { profile ->
                    navController.navigate(
                        Route.Preferences.createRoute(profile)
                    )
                }
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

        composable(
            route = Route.Preferences.route,
            arguments = listOf(
                navArgument("profile") {
                    type = NavType.EnumType(ConnectionProfile::class.java)
                }
            )
        ) { backStackEntry ->

            val profileName =
                backStackEntry.arguments?.getString("profile")

            val profile = ConnectionProfile.valueOf(
                profileName ?: ConnectionProfile.LABORATORY.name
            )

            PlaceholderScreen(
                title = "Preferences: ${profile.name}",
                onBack = { navController.popBackStack() }
            )
        }
    }
}