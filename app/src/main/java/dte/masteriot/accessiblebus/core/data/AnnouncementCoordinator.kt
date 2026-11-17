package dte.masteriot.accessiblebus.core.data

import android.content.Context
import dte.masteriot.accessiblebus.core.config.VoiceMode
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.data.AppConfigRepository
import dte.masteriot.accessiblebus.sensors.ContextEstimator
import dte.masteriot.accessiblebus.sensors.PhoneContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnnouncementCoordinator(
    private val configRepository: AppConfigRepository,
    private val contextEstimator: ContextEstimator,
    private val availability: kotlinx.coroutines.flow.StateFlow<Boolean>,
    private val scope: CoroutineScope,
    context: Context
) : DecisionAnnouncer {

    private val speech = AnnouncementSpeech(context)
    private val incoming = Channel<DecisionEvent>(Channel.UNLIMITED)

    private val pending = ArrayDeque<DecisionEvent>()

    private val pendingMutex = Mutex()
    private val processingMutex = Mutex()

    private val feedback = AnnouncementFeedback(context)
    private val _uiEvents = MutableSharedFlow<AnnouncementUiEvent>(
        extraBufferCapacity = 10
    )

    val uiEvents: SharedFlow<AnnouncementUiEvent> = _uiEvents.asSharedFlow()

    init {
        // Recibe las decisiones del repositorio.
        scope.launch {
            for (decision in incoming) {
                pendingMutex.withLock {
                    pending.addLast(decision)
                }

                processPending()
            }
        }

        // Cuando la aplicación vuelve a estar disponible,
        // intenta procesar las decisiones retenidas.
        scope.launch {
            availability.collect { isAvailable ->
                if (isAvailable) {
                    processPending()
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
     * Devuelve true si la decisión ya se ha procesado.
     * Devuelve false si debe permanecer pendiente.
     */
    private suspend fun processDecision(
        decision: DecisionEvent
    ): Boolean {

        if (!availability.value) return false

        val preferences =
            configRepository.accessibilityPreferences().first()

        val message = messageFor(decision) ?: return true

        if (!availability.value) return false

        when (preferences.voiceMode) {

            VoiceMode.OFF -> {
                _uiEvents.tryEmit(
                    AnnouncementUiEvent(message)
                )
            }

            VoiceMode.ALWAYS -> {
                speech.speak(message)
            }

            VoiceMode.AUTO -> {
                when (contextEstimator.estimate()) {

                    PhoneContext.COVERED -> {
                        if (!availability.value) return false

                        feedback.vibrate(preferences.vibrationEnabled)
                        speech.speak(message)
                    }

                    PhoneContext.UNCOVERED,
                    PhoneContext.UNKNOWN -> {
                        _uiEvents.tryEmit(
                            AnnouncementUiEvent(message)
                        )
                    }
                }
            }
        }

        return true
    }

    private fun messageFor(decision: DecisionEvent): String? {

        // Mensaje utilizado por el botón "Probar voz".
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