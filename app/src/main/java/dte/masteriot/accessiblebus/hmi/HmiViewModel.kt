package dte.masteriot.accessiblebus.hmi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dte.masteriot.accessiblebus.AccessibleBusApplication
import dte.masteriot.accessiblebus.BuildConfig
import dte.masteriot.accessiblebus.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HmiViewModel(application: Application) : AndroidViewModel(application) {

    private val api = (application as AccessibleBusApplication).container.apiSource
    private val _state = MutableStateFlow(HmiUiState())
    val state: StateFlow<HmiUiState> = _state

    init {
        viewModelScope.launch {
            // TODO(I1): remove with SampleApi.
            if (BuildConfig.USE_SAMPLE_API) seedDemo()
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = when (val r = api.sync("vehicle", VEHICLE_ID)) {
                is ApiResult.Success -> r.value.toHmiUiState()
                is ApiResult.Conflict<*> -> HmiUiState(error = "Conflicto: ${r.message}")
                is ApiResult.Failure -> HmiUiState(error = "Error: ${r.message}")
            }
        }
    }

    private suspend fun seedDemo() {
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