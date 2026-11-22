
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

/**
 * Local state of one command: sending, no answer with a retry button, or refused.
 */
@Composable
fun CommandStatus(state: CommandState?, onRetry: () -> Unit, onDismiss: () -> Unit) {
    if (state == null) return

    Column(Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
    }) {
        when (state) {
            CommandState.Sending ->
                Text("Sending…", style = MaterialTheme.typography.bodyLarge)

            is CommandState.Unconfirmed -> {
                Text(
                    "Sending pending: no response from the server.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }

            is CommandState.Refused -> {
                Text(
                    refusal(state.code),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onDismiss) {
                    Text("Understood")
                }
            }
        }
    }
}

fun refusal(code: String): String = when (code) {
    "VERSION_CONFLICT" -> "The request changed in the meantime. Its current status is shown."
    "INVALID_TRANSITION" -> "The request no longer allows this change."
    "ACTIVE_REQUEST_EXISTS" -> "You already have an open request at this stop."
    "VISIT_ALREADY_PASSED" -> "The bus has already passed this stop."
    "TRIP_NOT_ACTIVE" -> "The demonstration trip has ended."
    "IDEMPOTENCY_CONFLICT" -> "The previous submission does not match. Please try again."
    else -> "Could not complete the operation ($code)."
}

fun stateLabel(state: RequestState): String = when (state) {
    RequestState.PENDING -> "Registered, awaiting response"
    RequestState.ACCEPTED -> "Accepted by the driver"
    RequestState.REJECTED -> "Rejected by the driver"
    RequestState.CANCELLED -> "Cancelled"
    RequestState.COMPLETED -> "Completed"
    RequestState.EXPIRED -> "Expired"
}

fun closeLabel(reason: String?): String? = when (reason) {
    null -> null
    "RAMP_UNAVAILABLE" -> "Ramp unavailable"
    "UNSAFE_STOP" -> "Unsafe stop"
    "STOP_PASSED" -> "The bus passed the stop"
    "TRIP_ENDED" -> "The trip ended"
    else -> "Other reason"
}
