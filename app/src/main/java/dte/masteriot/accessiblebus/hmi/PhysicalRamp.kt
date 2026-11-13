package dte.masteriot.accessiblebus.hmi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Physical ramp node (ESP32) driven from the HMI (K2 proposal: commands, node status and
 * actuator state). The HMI screen is not wired yet: HmiViewModel.ramp and HmiViewModel.ramp(action)
 * are ready for it.
 *
 * Until the projection (B03/B13) exists, the position comes straight from the K2 telemetry, with
 * no session or sequence validation. When B13 lands, feed describe() from the projection instead;
 * the commands do not change.
 */

/** Last limit-switch sample. [receivedAtMs] is a monotonic clock reading. */
data class RampReading(
    val stowed: Boolean,
    val deployed: Boolean,
    val sourceMode: String,
    val receivedAtMs: Long,
)

/** Actuator state reported by the node on devices/{deviceId}/ramps/{nodeId}. */
data class RampActuator(val motion: String, val fault: String, val lastResult: String)

enum class RampAction { DEPLOY, STOW, STOP }

data class RampView(
    val text: String,
    val canDeploy: Boolean = false,
    val canStow: Boolean = false,
    val canStop: Boolean = false,
)

object PhysicalRamp {
    const val STALE_AFTER_MS = 15_000L
    private const val SCHEMA_VERSION = 3

    fun parseTelemetry(json: String, vehicleId: String, nowMs: Long): RampReading? {
        val o = parse(json) ?: return null
        if (o.int("schemaVersion") != SCHEMA_VERSION || o.string("vehicleId") != vehicleId) return null
        val stowed = o.bool("stowedLimit") ?: return null
        val deployed = o.bool("deployedLimit") ?: return null
        return RampReading(stowed, deployed, o.string("sourceMode") ?: "UNKNOWN", nowMs)
    }

    fun parseActuator(json: String): RampActuator? {
        val o = parse(json) ?: return null
        if (o.int("schemaVersion") != SCHEMA_VERSION) return null
        return RampActuator(
            motion = o.string("motion") ?: return null,
            fault = o.string("fault") ?: "NONE",
            lastResult = o.string("lastResult") ?: "NONE",
        )
    }

    fun commandPayload(action: RampAction, commandId: String): String =
        """{"schemaVersion":$SCHEMA_VERSION,"commandId":"$commandId","action":"${action.name}"}"""

    fun describe(reading: RampReading?, actuator: RampActuator?, nowMs: Long): RampView {
        if (reading == null) return RampView("Ramp: no data from the ramp node")
        val ageS = (nowMs - reading.receivedAtMs) / 1000
        if (nowMs - reading.receivedAtMs > STALE_AFTER_MS) {
            return RampView("Ramp: no recent data (last sample ${ageS}s ago)")
        }
        val source = if (reading.sourceMode == "PHYSICAL") "physical sensor" else "simulated sample"
        val motion = actuator?.motion
        val moving = motion == "DEPLOYING" || motion == "STOWING"
        val contradictory = reading.stowed && reading.deployed
        val position = when {
            contradictory -> "contradictory sensors"
            motion == "DEPLOYING" -> "deploying…"
            motion == "STOWING" -> "stowing…"
            reading.stowed -> "stowed"
            reading.deployed -> "deployed"
            else -> "intermediate"
        }
        val fault = actuator?.fault?.takeIf { it != "NONE" && actuator.motion == "FAULT" }
        val text = buildString {
            append("Ramp: ").append(position)
            if (fault != null) append(" · FAULT ").append(fault)
            append(" · ").append(source)
        }
        return RampView(
            text = text,
            canDeploy = !moving && !contradictory && !reading.deployed,
            canStow = !moving && !contradictory && !reading.stowed,
            canStop = moving,
        )
    }

    private fun parse(json: String): JsonObject? =
        runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()

    private fun JsonObject.string(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    private fun JsonObject.bool(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
    private fun JsonObject.int(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
}
