package dte.masteriot.accessiblebus.ui.passenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dte.masteriot.accessiblebus.AccessibleBusApplication
import dte.masteriot.accessiblebus.core.data.AssistanceRepository
import dte.masteriot.accessiblebus.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Passenger screens. Commands run in viewModelScope, which survives a rotation, and the key is
 * saved by the repository before sending, so turning the phone never sends a command twice.
 */
class PassengerViewModel(private val repository: AssistanceRepository) : ViewModel() {

    val snapshot = repository.snapshot
    val commands = repository.commands
    val live = repository.live

    private val _created = MutableStateFlow<String?>(null)

    /** Id of the request just created, for Confirm to open Tracking once. */
    val created: StateFlow<String?> = _created.asStateFlow()

    init {
        repository.startEvents()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun create(stopVisitId: String) {
        viewModelScope.launch {
            val result = repository.create(stopVisitId)
            if (result is ApiResult.Success) _created.value = result.value.id
        }
    }

    fun cancel(requestId: String) {
        viewModelScope.launch { repository.cancel(requestId) }
    }

    fun dismiss(commandId: String) = repository.dismiss(commandId)

    fun createdShown() {
        _created.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PassengerViewModel((this[APPLICATION_KEY] as AccessibleBusApplication).container.assistanceRepository)
            }
        }
    }
}
