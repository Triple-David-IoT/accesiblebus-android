package dte.masteriot.accessiblebus.navigation

import dte.masteriot.accessiblebus.core.config.ConnectionProfile
sealed class Route(val route: String) {

    object Profile : Route("profile")

    object PassengerStops : Route("passenger/stops")

    object PassengerStopDetail : Route("passenger/stop-detail/{stopCode}") {
        fun create(stopCode: String) = "passenger/stop-detail/$stopCode"
    }

    object PassengerConfirm : Route("passenger/confirm/{stopVisitId}") {
        fun create(stopVisitId: String) = "passenger/confirm/$stopVisitId"
    }

    object PassengerTracking : Route("passenger/tracking/{requestId}") {
        fun create(requestId: String) = "passenger/tracking/$requestId"
    }

    object DriverHmi : Route("driver/hmi")

    object Preferences : Route("preferences/{profile}") {
        fun createRoute(profile: ConnectionProfile): String {
            return "preferences/${profile.name}"
        }
    }
}