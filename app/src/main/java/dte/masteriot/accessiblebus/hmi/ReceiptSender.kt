package dte.masteriot.accessiblebus.hmi

import dte.masteriot.accessiblebus.core.net.Snapshot
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReceiptSender(
    private val snapshot: StateFlow<Snapshot>,
    private val link: HmiLink,
    private val scope: CoroutineScope,
    private val enabled: Boolean,
) {
    private val sent = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        if (enabled) scope.launch { snapshot.collect { check() } }
    }

    /** Sends the receipts still missing. Call it after every /sync, even if the snapshot did not change. */
    fun check(forgetSent: Boolean = false) {
        if (!enabled) return
        if (forgetSent) sent.clear()
        val current = snapshot.value
        val tripId = current.trip?.id ?: return
        current.requests
            .filter { it.tripId == tripId && it.isOpen && !it.received && it.createdEventId !in sent }
            .forEach { request ->
                scope.launch { if (link.sendReceipt(request)) sent += request.createdEventId }
            }
    }
}