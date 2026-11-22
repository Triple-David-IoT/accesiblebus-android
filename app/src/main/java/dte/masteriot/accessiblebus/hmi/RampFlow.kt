package dte.masteriot.accessiblebus.hmi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 * Does the projection of the ramp (vehicles/{id}/ramp/state, written by the Node-RED
 * ingestion) still flow? Node-RED decides the quality of the sample; the HMI only checks that
 * new projections keep arriving. A retained projection alone never makes the ramp VALID.
 */

/** One projection message. */
data class RampProjection(val runId: String, val sequence: Long, val position: String, val quality: String)

/** A projection and the monotonic clock reading (elapsedRealtime) when it arrived. */
data class RampStamp(val projection: RampProjection, val receivedAtMs: Long)

/** What the HMI shows. [confirmed] is true only when the flow is alive and VALID. */
data class RampFlowView(val text: String, val confirmed: Boolean = false)

object RampFlow {
    const val STALE_AFTER_MS = 10_000L
    const val MIN_GAP_MS = 2_000L
    const val VALID = "VALID"
    const val STALE = "STALE"
    const val INVALID = "INVALID"
    private const val SCHEMA_VERSION = 3

    fun parseProjection(json: String, vehicleId: String): RampProjection? {
        val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        if (o.int("schemaVersion") != SCHEMA_VERSION) return null
        if (o.string("vehicleId") != vehicleId) return null
        return RampProjection(
            runId = o.string("projectionRunId") ?: return null,
            sequence = o.long("projectionSequence") ?: return null,
            position = o.string("position") ?: return null,
            quality = o.string("quality") ?: return null,
        )
    }
}

/** Immutable: every projection returns a new state, so it is safe to use from a flow. */
data class RampFlowState(
    val runId: String? = null,
    val reference: RampStamp? = null,
    val last: RampStamp? = null,
    val confirmed: Boolean = false,
) {
    fun accept(stamp: RampStamp): RampFlowState {
        val p = stamp.projection
        val previous = last
        // First projection, or a new run: only a reference, never VALID.
        if (previous == null || runId != p.runId) return RampFlowState(p.runId, stamp, stamp, false)
        // A repeated or older sequence neither confirms nor keeps the flow alive.
        if (p.sequence <= previous.projection.sequence) return this
        // Too long since the previous one (client paused, flow stopped): start again from this one.
        if (stamp.receivedAtMs - previous.receivedAtMs > RampFlow.STALE_AFTER_MS) {
            return RampFlowState(p.runId, stamp, stamp, false)
        }
        // STALE or INVALID degrade at once.
        if (p.quality != RampFlow.VALID) return RampFlowState(p.runId, stamp, stamp, false)
        if (confirmed) return copy(last = stamp)
        val ref = reference ?: stamp
        if (ref.projection.quality == RampFlow.VALID && stamp.receivedAtMs - ref.receivedAtMs >= RampFlow.MIN_GAP_MS) {
            return copy(last = stamp, confirmed = true)
        }
        // Still waiting: keep a VALID reference, otherwise this projection becomes the reference.
        return copy(reference = if (ref.projection.quality == RampFlow.VALID) ref else stamp, last = stamp)
    }

    fun view(nowMs: Long): RampFlowView {
        val latest = last ?: return RampFlowView("Ramp: unknown · Waiting for the ramp flow")
        val silentMs = nowMs - latest.receivedAtMs
        if (silentMs > RampFlow.STALE_AFTER_MS) return RampFlowView("Ramp: unknown · No flow for ${silentMs / 1000}s")
        val p = latest.projection
        return when {
            p.quality == RampFlow.STALE -> RampFlowView("Ramp: unknown · Sensor data stale")
            p.quality == RampFlow.INVALID -> RampFlowView("Ramp: unknown · Contradictory sensors")
            !confirmed -> RampFlowView("Ramp: unknown · Confirming the flow…")
            else -> RampFlowView("Ramp: ${positionWord(p.position)} · Flow active · Simulated sample", confirmed = true)
        }
    }

    private fun positionWord(position: String) = when (position) {
        "STOWED" -> "stowed"
        "DEPLOYED" -> "deployed"
        "INTERMEDIATE" -> "intermediate"
        else -> "unknown"
    }
}

private fun JsonObject.string(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
private fun JsonObject.int(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
private fun JsonObject.long(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }