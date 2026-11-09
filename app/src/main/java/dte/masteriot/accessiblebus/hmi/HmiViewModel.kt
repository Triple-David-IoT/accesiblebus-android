package dte.masteriot.accessiblebus.hmi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dte.masteriot.accessiblebus.AccessibleBusApplication
import dte.masteriot.accessiblebus.BuildConfig
import dte.masteriot.accessiblebus.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    init {
        repository.startEvents()
        viewModelScope.launch {
            // TODO(I1): remove with SampleApi.
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