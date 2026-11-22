package dte.masteriot.accessiblebus.hmi

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dte.masteriot.accessiblebus.AccessibleBusApplication
import dte.masteriot.accessiblebus.BuildConfig
import dte.masteriot.accessiblebus.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import dte.masteriot.accessiblebus.core.net.RejectReason
import dte.masteriot.accessiblebus.core.net.RequestState

class HmiViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as AccessibleBusApplication).container
    private val repository = container.driverRepository
    private val receipts = container.receiptSender
    private val error = MutableStateFlow<String?>(null)
    private val alerts = NewRequestAlert(application)

    val state: StateFlow<HmiUiState> =
        combine(repository.snapshot, repository.live, error) { snapshot, live, error ->
            snapshot.toHmiUiState().copy(
                connectionText = if (live) "Connection: live" else "Connection: no live notices (use Refresh)",
                error = error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HmiUiState())

    private val link = container.hmiLink

    // Re-evaluated every second so that a silent ramp node turns stale.
    private val clock = flow {
        while (true) {
            emit(SystemClock.elapsedRealtime())
            delay(1_000)
        }
    }

    /** Physical ramp for the HMI ramp card: what to show and which maneuvers are allowed now. */
    val ramp: StateFlow<RampView> =
        combine(link.rampReading, link.rampActuator, clock) { reading, actuator, now ->
            PhysicalRamp.describe(reading, actuator, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RampView("Ramp: no data"))

    private sealed interface FlowEvent {
        object Connected : FlowEvent
        data class Projection(val stamp: RampStamp) : FlowEvent
    }

    /**
     * Is the projection of the ramp still flowing? UNKNOWN on every connection, on a new
     * projectionRunId and after 10 s without a newer projection (see RampFlow.kt).
     */
    val rampFlow: StateFlow<RampFlowView> =
        combine(
            merge(
                link.feed.connected.filter { it }.map<Boolean, FlowEvent> { FlowEvent.Connected },
                link.projections.map<RampStamp, FlowEvent> { FlowEvent.Projection(it) },
            ).scan(RampFlowState()) { state, event ->
                when (event) {
                    FlowEvent.Connected -> RampFlowState()
                    is FlowEvent.Projection -> state.accept(event.stamp)
                }
            },
            clock,
        ) { state, now -> state.view(now) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RampFlowView("Ramp: unknown · Waiting for the ramp flow"))

    init {
        repository.startEvents()
        viewModelScope.launch {
            while (!link.startRamp()) delay(5_000)
        }
        viewModelScope.launch {
            while (!link.startProjection()) delay(5_000)
        }
        viewModelScope.launch {
            // Sample mode has no laboratory: start with some requests.
            if (BuildConfig.USE_SAMPLE_API) seedDemo()
            refresh()
        }
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                val tripId = snapshot.trip?.id ?: return@collect
                val fresh = snapshot.requests.filter {
                    it.tripId == tripId && it.state == RequestState.PENDING && alerts.firstTime(it.id)
                }
                if (fresh.isNotEmpty()) alerts.alert()
            }
        }
    }

    /** Pull the snapshot, then acknowledge any request the API still shows as not received. */
    fun refresh() {
        viewModelScope.launch {
            error.value = when (val r = repository.refresh()) {
                is ApiResult.Success -> null
                is ApiResult.Conflict<*> -> "Conflict: ${r.message}"
                is ApiResult.Failure -> "Error: ${r.message}"
            }
            receipts.check(forgetSent = true)
        }
    }

    /** Accept a pending request. The repository applies the answer, or the current resource on a conflict. */
    private fun command(call: suspend () -> ApiResult<*>) {
        viewModelScope.launch {
            error.value = when (val r = call()) {
                is ApiResult.Success -> null
                is ApiResult.Conflict<*> -> "Conflict: ${r.message}"
                is ApiResult.Failure -> "Error: ${r.message}"
            }
        }
    }

    fun accept(requestId: String) = command { repository.accept(requestId) }
    fun reject(requestId: String, reason: RejectReason) = command { repository.reject(requestId, reason) }
    fun complete(requestId: String) = command { repository.complete(requestId) }
    fun advance() = command { repository.advance() }
    fun endTrip() = command { repository.end() }

    /** Publishes DEPLOY, STOW or STOP to the physical ramp node of this vehicle. */
    fun ramp(action: RampAction) {
        viewModelScope.launch {
            if (!link.sendRampCommand(action)) error.value = "Error: ramp command not sent (broker unreachable)"
        }
    }

    private suspend fun seedDemo() {
        val api = container.apiSource
        val snap = (api.sync("vehicle", VEHICLE_ID) as? ApiResult.Success)?.value ?: return
        val trip = snap.trip ?: return
        val visits = snap.stopVisits.sortedBy { it.sequence }
        if (visits.size < 3) return
        val first = (api.createRequest("seed-1", visits[0].id) as? ApiResult.Success)?.value ?: return
        api.createRequest("seed-3", visits[2].id)
        api.acceptRequest("seed-accept", first.id, first.version)
        api.advanceTrip("seed-advance", trip.id, trip.lastPassedSequence)
    }

    private companion object { const val VEHICLE_ID = "BUS-DEMO-01" }
}