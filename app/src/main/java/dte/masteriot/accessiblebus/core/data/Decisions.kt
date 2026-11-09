package dte.masteriot.accessiblebus.core.data

import android.content.Context
import dte.masteriot.accessiblebus.core.net.RequestState

/** A decision on one of the user's requests, announced once by voice and vibration (K5). */
data class DecisionEvent(
    val requestId: String,
    val version: Int,
    val state: RequestState,
    val reason: String?,
)

/** Receives the decisions. The AnnouncementCoordinator (C) implements it. */
fun interface DecisionAnnouncer {
    fun announce(decision: DecisionEvent)
}

/** States that come from the driver or the trip, never from the passenger's own action. */
val DECISION_STATES = setOf(RequestState.ACCEPTED, RequestState.REJECTED, RequestState.COMPLETED, RequestState.EXPIRED)

/**
 * Decisions already announced, as "requestId:version". Kept on disk so that reopening the app
 * or receiving the same state from /sync and MQTT never repeats an announcement.
 */
interface AnnouncedDecisions {
    /** Adds the key and returns true if it was not there yet. */
    fun add(key: String): Boolean
}

class PreferencesAnnouncedDecisions(context: Context) : AnnouncedDecisions {

    private val preferences = context.getSharedPreferences("announced_decisions", Context.MODE_PRIVATE)

    @Synchronized
    override fun add(key: String): Boolean {
        val keys = preferences.getStringSet(KEYS, emptySet()).orEmpty()
        if (key in keys) return false
        // Only recent decisions matter; keep the set small.
        val kept = (keys.sorted().takeLast(MAX - 1) + key).toSet()
        preferences.edit().putStringSet(KEYS, kept).commit()
        return true
    }

    private companion object {
        const val KEYS = "keys"
        const val MAX = 200
    }
}
