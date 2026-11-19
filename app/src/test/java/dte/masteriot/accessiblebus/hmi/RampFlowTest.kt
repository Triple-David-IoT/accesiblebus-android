package dte.masteriot.accessiblebus.hmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RampFlowTest {

    private fun stamp(seq: Long, at: Long, quality: String = "VALID", position: String = "STOWED", run: String = "run-1") =
        RampStamp(RampProjection(run, seq, position, quality), at)

    private val active = "Ramp: stowed · Flow active · Simulated sample"

    @Test
    fun firstProjectionIsOnlyAReference() {
        val view = RampFlowState().accept(stamp(1, 0)).view(1_000)
        assertFalse(view.confirmed)
        assertEquals("Ramp: unknown · Confirming the flow…", view.text)
    }

    @Test
    fun secondNewerProjectionConfirmsAfterTwoSeconds() {
        val view = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000)).view(5_000)
        assertTrue(view.confirmed)
        assertEquals(active, view.text)
    }

    @Test
    fun aProjectionTooCloseWaitsForTheNext() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 1_000))
        assertFalse(s.view(1_000).confirmed)
        assertTrue(s.accept(stamp(3, 6_000)).view(6_000).confirmed)
    }

    @Test
    fun aNewRunStartsOver() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000)).accept(stamp(1, 6_000, run = "run-2"))
        assertFalse(s.view(6_000).confirmed)
        assertEquals("Ramp: unknown · Confirming the flow…", s.view(6_000).text)
    }

    @Test
    fun aRepeatedSequenceDoesNotKeepTheFlowAlive() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000)).accept(stamp(2, 12_000))
        assertTrue(s.view(14_000).confirmed)
        assertEquals("Ramp: unknown · No flow for 11s", s.view(16_000).text)
    }

    @Test
    fun tenSecondsWithoutANewProjectionGoBackToUnknown() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000))
        assertTrue(s.view(15_000).confirmed)
        val view = s.view(15_001)
        assertFalse(view.confirmed)
        assertTrue(view.text.startsWith("Ramp: unknown · No flow"))
    }

    @Test
    fun staleDegradesAtOnce() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000))
            .accept(stamp(3, 10_000, quality = "STALE", position = "UNKNOWN"))
        assertFalse(s.view(10_000).confirmed)
        assertEquals("Ramp: unknown · Sensor data stale", s.view(10_000).text)
    }

    @Test
    fun invalidDegradesAtOnce() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000))
            .accept(stamp(3, 10_000, quality = "INVALID", position = "UNKNOWN"))
        assertEquals("Ramp: unknown · Contradictory sensors", s.view(10_000).text)
    }

    @Test
    fun afterADegradationItNeedsTwoValidProjections() {
        val degraded = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000))
            .accept(stamp(3, 10_000, quality = "STALE", position = "UNKNOWN"))
        val first = degraded.accept(stamp(4, 15_000))
        assertFalse(first.view(15_000).confirmed)
        assertTrue(first.accept(stamp(5, 20_000)).view(20_000).confirmed)
    }

    @Test
    fun aRetainedProjectionAloneNeverConfirms() {
        val s = RampFlowState().accept(stamp(9, 0))
        assertFalse(s.view(1_000).confirmed)
        assertFalse(s.view(60_000).confirmed)
    }

    @Test
    fun aLongClientPauseRearmsTheCheck() {
        val s = RampFlowState().accept(stamp(1, 0)).accept(stamp(2, 5_000)).accept(stamp(3, 65_000))
        assertFalse(s.view(65_000).confirmed)
        assertTrue(s.accept(stamp(4, 70_000)).view(70_000).confirmed)
    }

    @Test
    fun parsesTheProjectionOfItsVehicleOnly() {
        val json = """{"schemaVersion":3,"vehicleId":"BUS-DEMO-01","projectionRunId":"r1","projectionSequence":17,"sourceSequence":42,"position":"STOWED","quality":"VALID"}"""
        val p = RampFlow.parseProjection(json, "BUS-DEMO-01")
        assertNotNull(p)
        assertEquals(17L, p!!.sequence)
        assertEquals("STOWED", p.position)
        assertNull(RampFlow.parseProjection(json, "BUS-DEMO-02"))
        assertNull(RampFlow.parseProjection(json.replace("\"schemaVersion\":3", "\"schemaVersion\":2"), "BUS-DEMO-01"))
        assertNull(RampFlow.parseProjection("not json", "BUS-DEMO-01"))
    }
}
