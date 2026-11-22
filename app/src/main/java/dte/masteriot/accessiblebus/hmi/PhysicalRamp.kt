package dte.masteriot.accessiblebus.hmi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Physical ramp node (ESP32) driven from the HMI (contracts/mqtt.md: commands, node status and
 * actuator state). HmiScreen shows HmiViewModel.ramp in its ramp card and sends HmiViewModel.ramp(action).
 *
 * The position comes straight from the node's telemetry, with no session or sequence validation:
 * the Node-RED projection only covers the simulated ramps.
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

/** How the ramp card colours its title. The words carry the meaning; the colour only backs them. */
enum class RampTone { NORMAL, MOVING, ALERT }

/**
 * Everything the ramp card shows. The screen only draws these fields: it never decides from the
 * text, a timer or the position whether a button is enabled.
 *
 * [text] is the one-line summary (logs and accessibility). [title] is the state in a few words,
 * [detail] the two limit switches, [source] where the sample comes from, and [alarm] what is wrong
 * and why the controls are locked, or null when nothing is.
 */
data class RampView(
    val text: String,
    val title: String = "No data",
    val detail: String = "No sample from the ramp node yet",
    val source: String = "",
    val alarm: String? = null,
    val tone: RampTone = RampTone.NORMAL,
    val canDeploy: Boolean = false,
    val canStow: Boolean = false,
    val canStop: Boolean = false,
)

object PhysicalRamp {
    const val STALE_AFTER_MS = 15_000L
    private const val SCHEMA_VERSION = 3

    /**
     * Accepts only the samples of [nodeId]: the Node-RED simulator publishes on the same topic
     * (ramp-node-01) and its samples must not mix with the physical node's.
     */
    fun parseTelemetry(json: String, vehicleId: String, nodeId: String, nowMs: Long): RampReading? {
        val o = parse(json) ?: return null
        if (o.int("schemaVersion") != SCHEMA_VERSION) return null
        if (o.string("vehicleId") != vehicleId || o.string("nodeId") != nodeId) return null
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
        val source = if (reading.sourceMode == "PHYSICAL") "Physical sensor" else "Simulated sample"
        val ageS = (nowMs - reading.receivedAtMs) / 1000
        if (nowMs - reading.receivedAtMs > STALE_AFTER_MS) {
            return RampView(
                text = "Ramp: no recent data (last sample ${ageS}s ago)",
                title = "No live data",
                detail = "Last sample ${ageS}s ago",
                source = source,
                alarm = "No recent data from the ramp node. Controls locked.",
                tone = RampTone.ALERT,
            )
        }
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
            append(" · ").append(source.lowercase())
        }
        val title = when {
            contradictory -> "Sensor conflict"
            motion == "DEPLOYING" -> "Deploying…"
            motion == "STOWING" -> "Stowing…"
            reading.stowed -> "Stowed"
            reading.deployed -> "Deployed"
            else -> "Between limits"
        }
        val alarm = when {
            contradictory -> "Both limit switches ON. Controls locked."
            fault != null -> "Fault: $fault. Check the ramp area before retrying."
            else -> null
        }
        return RampView(
            text = text,
            title = title,
            detail = "Stowed limit ${onOff(reading.stowed)} · Deployed limit ${onOff(reading.deployed)}",
            source = source,
            alarm = alarm,
            tone = when {
                alarm != null -> RampTone.ALERT
                moving -> RampTone.MOVING
                else -> RampTone.NORMAL
            },
            canDeploy = !moving && !contradictory && !reading.deployed,
            canStow = !moving && !contradictory && !reading.stowed,
            canStop = moving,
        )
    }

    private fun onOff(on: Boolean) = if (on) "ON" else "OFF"

    private fun parse(json: String): JsonObject? =
        runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()

    private fun JsonObject.string(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    private fun JsonObject.bool(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
    private fun JsonObject.int(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
}
