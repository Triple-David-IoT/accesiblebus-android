package dte.masteriot.accessiblebus.announcements

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import dte.masteriot.accessiblebus.core.config.VoiceMode
import dte.masteriot.accessiblebus.core.data.AnnouncedDecisions
import dte.masteriot.accessiblebus.core.data.DecisionAnnouncer
import dte.masteriot.accessiblebus.core.data.DecisionEvent
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.config.AppConfigRepository
import dte.masteriot.accessiblebus.sensors.ContextEstimator
import dte.masteriot.accessiblebus.sensors.PhoneContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnnouncementCoordinator(
    private val configRepository: AppConfigRepository,
    private val contextEstimator: ContextEstimator,
    private val availability: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val scope: CoroutineScope,
    context: Context,
    /** Decisions already announced, on disk. */
    private val announced: AnnouncedDecisions,
) : DecisionAnnouncer {

    private val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private val speech = AnnouncementSpeech(context)
    private val incoming = Channel<DecisionEvent>(Channel.UNLIMITED)

    private val pending = ArrayDeque<DecisionEvent>()

    private val pendingMutex = Mutex()
    private val processingMutex = Mutex()

    private val feedback = AnnouncementFeedback(context)
    private val uiEventsChannel =
        Channel<AnnouncementUiEvent>(Channel.UNLIMITED)

    val uiEvents = uiEventsChannel.receiveAsFlow()

    init {
        // Receives the decisions from the repository.
        scope.launch {
            for (decision in incoming) {
                pendingMutex.withLock {
                    // Only the newest decision of a request is worth announcing when the app is back.
                    pending.removeAll { it.requestId == decision.requestId && !it.isTest }
                    pending.addLast(decision)
                }

                processPending()
            }
        }

        // When the app is available again,
        // tries to process the held decisions.
        scope.launch {
            processPending()
            availability.collect { isAvailable ->
                if (isAvailable) {
                    processPending()
                } else {
                    speech.stopCurrent()
                }
            }
        }
    }

    override fun announce(decision: DecisionEvent) {
        incoming.trySend(decision)
    }

    fun announceTest() {
        announce(
            DecisionEvent(
                requestId = "voice-test",
                version = 0,
                state = RequestState.ACCEPTED,
                reason = null,
                isTest = true
            )
        )
    }

    private suspend fun processPending() {
        processingMutex.withLock {
            while (availability.value) {
                val decision = pendingMutex.withLock {
                    pending.firstOrNull()
                } ?: return

                val processed = processDecision(decision)

                if (!processed) {
                    return
                }

                pendingMutex.withLock {
                    if (pending.firstOrNull() == decision) {
                        pending.removeFirst()
                    }
                }
            }
        }
    }

    /**
     * Returns true if the decision has been processed.
     * Returns false if it must stay pending.
     */
    private suspend fun processDecision(
        decision: DecisionEvent
    ): Boolean {

        if (!availability.value) return false

        val preferences =
            configRepository.accessibilityPreferences().first()

        val message = messageFor(decision) ?: return true

        if (!availability.value) return false

        if (decision.isTest) {
            speech.speak(message)
            return true
        }

        val mode = preferences.voiceMode
        val covered = mode == VoiceMode.AUTO &&
            contextEstimator.estimate().also { Log.d("Announcements", "AUTO context estimated: $it") } == PhoneContext.COVERED
        // With TalkBack on, the message goes on screen for TalkBack to read, instead of speaking over it.
        val voice = (mode == VoiceMode.ALWAYS || covered) && !accessibility.isTouchExplorationEnabled

        if (!availability.value) return false

        // Marked when delivered, not when detected: a decision held while the app was hidden
        // survives the process being killed, and /sync brings it back.
        if (!announced.add("${decision.requestId}:${decision.version}")) return true

        if (mode != VoiceMode.ALWAYS) feedback.vibrate(preferences.vibrationEnabled)
        if (voice) {
            if (!speech.speak(message)) Log.w("Announcements", "Voice announcement could not be completed")
        } else {
            uiEventsChannel.trySend(AnnouncementUiEvent(message))
        }

        return true
    }

    private fun messageFor(decision: DecisionEvent): String? {

        // Message used by the "Test voice" button.
        if (decision.isTest) {
            return "Voice announcements are working."
        }

        return when (decision.state) {

            RequestState.ACCEPTED ->
                "The driver has accepted your request."

            RequestState.REJECTED ->
                "Your request could not be accepted."

            RequestState.EXPIRED -> when (decision.reason) {

                "STOP_PASSED" ->
                    "The stop has already passed without confirmation; request closed."

                "TRIP_ENDED" ->
                    "The trip has already ended."

                else -> null
            }

            RequestState.COMPLETED -> null

            else -> null
        }
    }
}