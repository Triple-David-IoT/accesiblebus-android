# B03 — Ingestion, quality and retained projection

Node-RED 4.1.3, same tab as the B02 simulators. Broker `mosquitto:1883`, client ID `nodered`.

## What it does

- Subscribes to `telemetry/vehicles/+/ramp` and validates each sample: types, `schemaVersion` 3, known `nodeId`/`vehicleId` pair, `sessionId` equal to the expected one, and `sequence` greater than the last accepted.
- Derives position and quality with the table of the proposal: `(T,F)` STOWED/VALID, `(F,T)` DEPLOYED/VALID, `(F,F)` INTERMEDIATE/VALID, `(T,T)` UNKNOWN/INVALID, no recent sample UNKNOWN/STALE.
- Discards without renewing the validity: malformed sample (`schema`), other session (`session`), repeated sequence (`duplicate`), lower sequence (`order`). Each discard is counted in the node context (`ingest_<nodeId>.discards`) and shown in the "discarded" debug node.
- Age of the last accepted sample: a counter that grows in a 1 s tick and returns to 0 only when a sample is accepted. At 15 s the quality is STALE. The projection timer never touches it.
- Publishes the retained projection on `vehicles/{id}/ramp/state` every 5 s and on every change. `projectionSequence` grows with each publication; `projectionRunId` is fixed until the next deploy or restart; `sourceSequence` is the last accepted sample.
- A simulator restart (second output of `simulate`) publishes UNKNOWN/STALE at once.
- State is in memory only: after restarting Node-RED nothing is restored.

## Results

| Test | Observed | Image |
| --- | --- | --- |
| Steady state | One projection per vehicle every 5 s; `projectionSequence` and `sourceSequence` grow by one; the `projectionRunId` does not change | 01 |
| `BUS-DEMO-01` scenarios (18 Nov) | INTERMEDIATE at 01:21:38, DEPLOYED at 01:21:41, UNKNOWN/INVALID at 01:21:44 and INTERMEDIATE at 01:21:50. `BUS-DEMO-02` stayed STOWED/VALID | 02 |
| `BUS-DEMO-02` scenarios | STOWED, INTERMEDIATE (01:20:27), DEPLOYED (01:20:30), INTERMEDIATE, STOWED (01:20:35), UNKNOWN/INVALID (01:20:39), INTERMEDIATE (01:20:54). `BUS-DEMO-01` stayed STOWED/VALID | 03, 04 |
| Duplicate and out of order | `discarded: duplicate` (00:54:59) and `discarded: order` (00:56:15); no projection change | 05 |
| Old samples replayed | Samples with `sequence` 19 every 5 s between the live ones (00:52:04 to 00:52:34); the live stream is not affected | 06 |
| Previous session | `discarded: session` at 01:00:58 | 07 |
| Simulator restart | At 01:03:35 UNKNOWN/STALE with `sourceSequence` 0 and the same `projectionRunId`; at 01:03:39 STOWED/VALID with `sourceSequence` 1. The other vehicle did not change | 08, 09 |
| Cut | Last live sample `sequence` 5 at 01:34:58. First STALE at 01:35:12, 14 s later | 10, 11 |
| Replays during STALE | The last accepted sequence (5) is published every 5 s from 01:36:01 to 01:37:01; the projection stays UNKNOWN/STALE with `sourceSequence` 5 | 13 |
| Resume | First live sample (`sequence` 6) at 01:37:08 and STOWED/VALID in the same second | 14 |
| Node-RED restart | Old run `3b59d785…` (`projectionSequence` 21); at 01:21:33 two new `projectionRunId` with `projectionSequence` 1 and UNKNOWN/STALE; at 01:21:37 and 01:21:38 STOWED/VALID with `sourceSequence` 1 | 12 |

## How to run it

1. `docker compose up -d`, load the flow: `docker cp nodered/flows.json nodered_service:/data/flows.json && docker restart nodered_service`.
2. `mosquitto_sub -h localhost -t 'vehicles/+/ramp/state' -F '%I %p'` and `mosquitto_sub -h localhost -t 'telemetry/#' -F '%I %p'`.
3. Use the buttons of `http://localhost:1880/dashboard`, and the "discarded" debug node of the editor.

## Not covered here

The detection of the flow stopping on the HMI (B13), the dashboard (B04), and the physical node `ramp-phys-01` of #88, which this ingestion does not accept yet.