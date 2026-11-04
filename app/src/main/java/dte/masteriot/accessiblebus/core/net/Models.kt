package dte.masteriot.accessiblebus.core.net.models

import kotlinx.serialization.Serializable
@Serializable
data class StopDto(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)