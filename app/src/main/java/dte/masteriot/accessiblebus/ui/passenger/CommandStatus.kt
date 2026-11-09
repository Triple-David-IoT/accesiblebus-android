package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dte.masteriot.accessiblebus.core.data.CommandState
import dte.masteriot.accessiblebus.core.net.RequestState

/** Local state of one command: sending, no answer with a retry button, or refused. */
@Composable
fun CommandStatus(state: CommandState?, onRetry: () -> Unit, onDismiss: () -> Unit) {
    if (state == null) return
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state) {
            CommandState.Sending -> Text("Pendiente de envío…", style = MaterialTheme.typography.bodyLarge)
            is CommandState.Unconfirmed -> {
                Text("Pendiente de envío: no hubo respuesta del servidor.", style = MaterialTheme.typography.bodyLarge)
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRetry) { Text("Reintentar") }
            }
            is CommandState.Refused -> {
                Text(refusal(state.code), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onDismiss) { Text("Entendido") }
            }
        }
    }
}

fun refusal(code: String): String = when (code) {
    "VERSION_CONFLICT" -> "La solicitud cambió mientras tanto. Se muestra su estado actual."
    "INVALID_TRANSITION" -> "La solicitud ya no admite ese cambio."
    "ACTIVE_REQUEST_EXISTS" -> "Ya tienes una solicitud abierta en esta parada."
    "VISIT_ALREADY_PASSED" -> "El autobús ya pasó por esta parada."
    "TRIP_NOT_ACTIVE" -> "El viaje de demostración ha finalizado."
    "IDEMPOTENCY_CONFLICT" -> "El envío anterior no coincide. Vuelve a intentarlo."
    else -> "No se pudo completar ($code)."
}

fun stateLabel(state: RequestState): String = when (state) {
    RequestState.PENDING -> "Registrada, pendiente de respuesta"
    RequestState.ACCEPTED -> "Aceptada por el conductor"
    RequestState.REJECTED -> "Rechazada por el conductor"
    RequestState.CANCELLED -> "Cancelada"
    RequestState.COMPLETED -> "Completada"
    RequestState.EXPIRED -> "Caducada"
}

fun closeLabel(reason: String?): String? = when (reason) {
    null -> null
    "RAMP_UNAVAILABLE" -> "Rampa no disponible"
    "UNSAFE_STOP" -> "Parada no segura"
    "STOP_PASSED" -> "El autobús pasó la parada"
    "TRIP_ENDED" -> "El viaje terminó"
    else -> "Otro motivo"
}
