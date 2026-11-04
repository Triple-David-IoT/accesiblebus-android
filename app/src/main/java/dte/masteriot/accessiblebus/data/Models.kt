package dte.masteriot.accessiblebus.data

import kotlinx.serialization.Serializable

@Serializable
data class Stop(val id: String, val name: String, val lat: Double, val lon: Double)

@Serializable
data class StopList(val stops: List<Stop>)

@Serializable
data class Arrival(
    val line: String,
    val destination: String,
    val etaSeconds: Int,
    val busId: String
)

@Serializable
data class StopArrivals(
    val stopId: String,
    val fetchedAt: String,
    val source: String,
    val arrivals: List<Arrival>
)

@Serializable
data class AssistanceRequest(
    val requestId: String,
    val stopId: String,
    val line: String,
    val busId: String,
    val state: String = "PENDING"
)