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
        val reading = PhysicalRamp.parseTelemetry(telemetry, "BUS-DEMO-01", nowMs = 100)
        assertNotNull(reading)
        assertTrue(reading!!.stowed)
        assertFalse(reading.deployed)
        assertEquals(100L, reading.receivedAtMs)
        assertNull(PhysicalRamp.parseTelemetry(telemetry, "BUS-DEMO-02", nowMs = 100))
        assertNull(PhysicalRamp.parseTelemetry(telemetry.replace("\"schemaVersion\":3", "\"schemaVersion\":2"), "BUS-DEMO-01", 0))
        assertNull(PhysicalRamp.parseTelemetry("not json", "BUS-DEMO-01", 0))
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
    fun commandCarriesSchemaIdAndAction() {
        assertEquals(
            """{"schemaVersion":3,"commandId":"abc","action":"STOW"}""",
            PhysicalRamp.commandPayload(RampAction.STOW, "abc"),
        )
    }
}
