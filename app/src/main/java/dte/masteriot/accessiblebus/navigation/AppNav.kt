package dte.masteriot.accessiblebus.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dte.masteriot.accessiblebus.ui.passenger.ConfirmScreen
import dte.masteriot.accessiblebus.ui.passenger.StopsScreen
import dte.masteriot.accessiblebus.ui.passenger.TrackingScreen
import dte.masteriot.accessiblebus.ui.screens.PlaceholderScreen

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
            PlaceholderScreen(
                title = "Profile",
                onBack = { navController.popBackStack() },
                links = listOf("Passenger" to { navController.navigate(Route.PassengerStops.route) })
            )
        }

        composable(Route.PassengerStops.route) {
            StopsScreen(onStop = { navController.navigate(Route.PassengerConfirm.create(it)) })
        }

        composable(Route.PassengerStopDetail.route) {
            PlaceholderScreen(
                title = "Stop Details",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PassengerConfirm.route) { entry ->
            ConfirmScreen(
                stopVisitId = entry.arguments?.getString("stopVisitId").orEmpty(),
                onTracking = { requestId ->
                    navController.navigate(Route.PassengerTracking.create(requestId)) {
                        popUpTo(Route.PassengerStops.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PassengerTracking.route) { entry ->
            TrackingScreen(
                requestId = entry.arguments?.getString("requestId").orEmpty(),
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