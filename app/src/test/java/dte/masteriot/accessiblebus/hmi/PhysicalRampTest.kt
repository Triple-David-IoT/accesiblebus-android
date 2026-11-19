package dte.masteriot.accessiblebus.hmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalRampTest {

    private val telemetry =
        """{"schemaVersion":3,"nodeId":"ramp-phys-01","vehicleId":"BUS-DEMO-01","sessionId":"s","sequence":4,"sourceMode":"PHYSICAL","stowedLimit":true,"deployedLimit":false}"""

    @Test
    fun parsesTelemetryOfItsVehicleOnly() {
        val reading = PhysicalRamp.parseTelemetry(telemetry, "BUS-DEMO-01", "ramp-phys-01", nowMs = 100)
        assertNotNull(reading)
        assertTrue(reading!!.stowed)
        assertFalse(reading.deployed)
        assertEquals(100L, reading.receivedAtMs)
        assertNull(PhysicalRamp.parseTelemetry(telemetry, "BUS-DEMO-02", "ramp-phys-01", nowMs = 100))
        assertNull(
            PhysicalRamp.parseTelemetry(
                telemetry.replace("\"schemaVersion\":3", "\"schemaVersion\":2"), "BUS-DEMO-01", "ramp-phys-01", 0,
            )
        )
        assertNull(PhysicalRamp.parseTelemetry("not json", "BUS-DEMO-01", "ramp-phys-01", 0))
    }

    @Test
    fun ignoresTheSimulatorOnTheSameTopic() {
        val simulated = telemetry.replace("ramp-phys-01", "ramp-node-01").replace("PHYSICAL", "SIMULATED")
        assertNull(PhysicalRamp.parseTelemetry(simulated, "BUS-DEMO-01", "ramp-phys-01", nowMs = 100))
    }

    @Test
    fun stowedRampCanOnlyDeploy() {
        val view = PhysicalRamp.describe(RampReading(true, false, "PHYSICAL", 0), null, nowMs = 1_000)
        assertEquals("Ramp: stowed · physical sensor", view.text)
        assertTrue(view.canDeploy)
        assertFalse(view.canStow)
        assertFalse(view.canStop)
    }

    @Test
    fun movingRampCanOnlyStop() {
        val actuator = PhysicalRamp.parseActuator(
            """{"schemaVersion":3,"motion":"DEPLOYING","fault":"NONE","lastResult":"STARTED"}"""
        )
        val view = PhysicalRamp.describe(RampReading(false, false, "PHYSICAL", 0), actuator, nowMs = 1_000)
        assertEquals("Ramp: deploying… · physical sensor", view.text)
        assertFalse(view.canDeploy)
        assertFalse(view.canStow)
        assertTrue(view.canStop)
    }

    @Test
    fun faultIsShownAndBothManeuversStayAvailable() {
        val view = PhysicalRamp.describe(
            RampReading(false, false, "PHYSICAL", 0), RampActuator("FAULT", "TIMEOUT", "STARTED"), nowMs = 1_000,
        )
        assertEquals("Ramp: intermediate · FAULT TIMEOUT · physical sensor", view.text)
        assertTrue(view.canDeploy)
        assertTrue(view.canStow)
    }

    @Test
    fun contradictoryOrStaleSensorsDisableTheButtons() {
        val both = PhysicalRamp.describe(RampReading(true, true, "PHYSICAL", 0), null, nowMs = 1_000)
        assertFalse(both.canDeploy || both.canStow)
        val stale = PhysicalRamp.describe(RampReading(true, false, "PHYSICAL", 0), null, nowMs = 20_000)
        assertEquals("Ramp: no recent data (last sample 20s ago)", stale.text)
        assertFalse(stale.canDeploy || stale.canStow || stale.canStop)
    }

    @Test
    fun cardFieldsFollowTheState() {
        val stowed = PhysicalRamp.describe(RampReading(true, false, "PHYSICAL", 0), null, nowMs = 1_000)
        assertEquals("Stowed", stowed.title)
        assertEquals("Stowed limit ON · Deployed limit OFF", stowed.detail)
        assertEquals("Physical sensor", stowed.source)
        assertNull(stowed.alarm)
        assertEquals(RampTone.NORMAL, stowed.tone)

        val moving = PhysicalRamp.describe(
            RampReading(false, false, "PHYSICAL", 0), RampActuator("STOWING", "NONE", "STARTED"), nowMs = 1_000,
        )
        assertEquals("Stowing…", moving.title)
        assertEquals(RampTone.MOVING, moving.tone)
        assertNull(moving.alarm)

        val stopped = PhysicalRamp.describe(
            RampReading(false, false, "PHYSICAL", 0), RampActuator("IDLE", "NONE", "STOPPED"), nowMs = 1_000,
        )
        assertEquals("Between limits", stopped.title)
        assertEquals("Stowed limit OFF · Deployed limit OFF", stopped.detail)
        assertNull(stopped.alarm)
    }

    @Test
    fun alarmsExplainWhatIsWrong() {
        val fault = PhysicalRamp.describe(
            RampReading(false, false, "PHYSICAL", 0), RampActuator("FAULT", "TIMEOUT", "STARTED"), nowMs = 1_000,
        )
        assertEquals("Between limits", fault.title)
        assertEquals("Fault: TIMEOUT. Check the ramp area before retrying.", fault.alarm)
        assertEquals(RampTone.ALERT, fault.tone)

        val both = PhysicalRamp.describe(RampReading(true, true, "PHYSICAL", 0), null, nowMs = 1_000)
        assertEquals("Sensor conflict", both.title)
        assertEquals("Both limit switches ON. Controls locked.", both.alarm)

        val stale = PhysicalRamp.describe(RampReading(true, false, "SIMULATED", 0), null, nowMs = 20_000)
        assertEquals("No live data", stale.title)
        assertEquals("Last sample 20s ago", stale.detail)
        assertEquals("Simulated sample", stale.source)
        assertEquals("No recent data from the ramp node. Controls locked.", stale.alarm)
        assertEquals(RampTone.ALERT, stale.tone)
    }

    @Test
    fun noSampleYetLocksEverythingWithoutAnAlarm() {
        val none = PhysicalRamp.describe(null, null, nowMs = 1_000)
        assertEquals("No data", none.title)
        assertNull(none.alarm)
        assertFalse(none.canDeploy || none.canStow || none.canStop)
    }

    @Test
    fun commandCarriesSchemaIdAndAction() {
        assertEquals(
            """{"schemaVersion":3,"commandId":"abc","action":"STOW"}""",
            PhysicalRamp.commandPayload(RampAction.STOW, "abc"),
        )
    }
}
