# B13 — Ramp flow detector on the HMI

HMI on the emulator, against the laboratory broker and the Node-RED ingestion of B03 (`vehicles/BUS-DEMO-01/ramp/state`).
The strip under the vehicle line shows the state of the flow. The ramp card of the physical node (#91) does not change; it shows "No data" here because there is no ESP32.

## Rules

- UNKNOWN on every connection, on a new `projectionRunId`, and after 10 s without a newer projection (measured with `elapsedRealtime()`).
- The first projection of a run is only a reference. VALID needs another one of the same run, with a greater `projectionSequence`, received at least 2 s later, and both with quality VALID.
- A repeated or older sequence does not keep the flow alive.
- STALE or INVALID degrade at once. After a degradation, two VALID projections are needed again.
- If more than 10 s pass between two projections (paused client), the next one is only a reference.

## Results

| Test | Observed | Image |
| --- | --- | --- |
| Normal | "Ramp: stowed · Flow active · Simulated sample" | 01 |
| Maneuver | Projections INTERMEDIATE at 20:39:18 and DEPLOYED at 20:39:22; the HMI shows "Ramp: deployed · Flow active" | 02, 03 |
| Contradiction | INVALID projection at 20:41:33; the HMI shows "Ramp: unknown · Contradictory sensors" | 04, 05 |
| Simulator cut | STALE projection at 21:18:35; the HMI shows "Ramp: unknown · Sensor data stale" | 06, 07 |
| Node-RED stopped | "Ramp: unknown · No flow for 17s" | 08 |
| Retained only | With Node-RED stopped, reopening the HMI never shows "Flow active": "Confirming the flow…" and then "No flow" | 09 |
| Node-RED started | The HMI returns to "Flow active" about 10 s after the new run | 10 |

`RampFlowTest` covers each rule: reference only, 2 s gap, new run, repeated sequence, 10 s boundary (10 000 ms still active, 10 001 ms no flow), STALE, INVALID, recovery, long pause and the parser.

## Not covered

The Deploy and Stow buttons of the ramp card do not depend on this detector.