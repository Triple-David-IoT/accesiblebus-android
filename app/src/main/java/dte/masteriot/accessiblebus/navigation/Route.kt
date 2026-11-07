package dte.masteriot.accessiblebus.navigation

import dte.masteriot.accessiblebus.core.config.ConnectionProfile
sealed class Route(val route: String) {

    object Profile : Route("profile")

    object PassengerStops : Route("passenger/stops")

    object PassengerStopDetail : Route("passenger/stop-detail")

    object PassengerConfirm : Route("passenger/confirm")

    object PassengerTracking : Route("passenger/tracking")

    object DriverHmi : Route("driver/hmi")

    object Preferences : Route("preferences/{profile}") {
        fun createRoute(profile: ConnectionProfile): String {
            return "preferences/${profile.name}"
        }
    }
}