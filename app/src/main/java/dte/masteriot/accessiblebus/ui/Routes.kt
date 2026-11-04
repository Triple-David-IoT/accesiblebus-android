package dte.masteriot.accessiblebus.ui

sealed class Routes(val route: String) {
    object HomeScreen : Routes("Home")
    object DetailsScreen : Routes("Details")
}
