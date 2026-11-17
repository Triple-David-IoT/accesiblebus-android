package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.config.VoiceMode
import dte.masteriot.accessiblebus.data.AppConfigRepository
import dte.masteriot.accessiblebus.sensors.ContextEstimator
import dte.masteriot.accessiblebus.sensors.PhoneContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
) : DecisionAnnouncer {

    private val incoming = Channel<DecisionEvent>(Channel.UNLIMITED)

    private val pending = ArrayDeque<DecisionEvent>()

    private val pendingMutex = Mutex()
    private val processingMutex = Mutex()

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
        if (!availability.value) {
            return false
        }

        val preferences =
            configRepository.accessibilityPreferences().first()

        when (preferences.voiceMode) {
            VoiceMode.OFF -> {
                // OFF: no se emite ningún anuncio.
                return true
            }

            VoiceMode.ALWAYS -> {
                // El anuncio se implementará en el siguiente paso.
                return true
            }

            VoiceMode.AUTO -> {
                val context = contextEstimator.estimate()

                if (!availability.value) {
                    return false
                }

                when (context) {
                    PhoneContext.COVERED -> {
                        // El teléfono está cubierto.
                        // Se anunciará mediante TTS.
                    }

                    PhoneContext.UNCOVERED -> {
                        // El teléfono está descubierto.
                        // Se anunciará mediante texto y vibración.
                    }

                    PhoneContext.UNKNOWN -> {
                        // Contexto no concluyente.
                        // No se debe inventar una clasificación.
                    }
                }

                return true
            }
        }
    }
}