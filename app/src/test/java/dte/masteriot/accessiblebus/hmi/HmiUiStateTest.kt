package dte.masteriot.accessiblebus.hmi

import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.StopVisit
import dte.masteriot.accessiblebus.core.net.Trip
import dte.masteriot.accessiblebus.core.net.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

class HmiUiStateTest {

    private val visits = listOf("456", "459", "460", "461").mapIndexed { i, code -> StopVisit("v${i + 1}", "t1", i + 1, code) }

    private fun label(passed: Int, state: TripState = TripState.ACTIVE) =
        Snapshot(Trip("t1", "BUS-DEMO-01", "driver-1", state, passed), visits).toHmiUiState().tripLabel

    @Test
    fun activeTripNamesTheNextStop() {
        assertEquals("Active trip · next stop 456 (1 of 4)", label(0))
        assertEquals("Active trip · next stop 460 (3 of 4)", label(2))
    }

    @Test
    fun afterTheLastStopItSaysSo() {
        assertEquals("Active trip · all 4 stops passed", label(4))
    }

    @Test
    fun endedOrMissingTripKeepsItsLabel() {
        assertEquals("Trip ended", label(2, TripState.ENDED))
        assertEquals("No trip", Snapshot().toHmiUiState().tripLabel)
    }
}
