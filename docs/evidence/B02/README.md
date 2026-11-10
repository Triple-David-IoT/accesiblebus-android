# B02 — Ramp simulators in Node-RED

Local stack in Docker Compose (mosquitto, nodered, api, postgres). Node-RED 4.1.3 with Dashboard 2.0.
Flow tab: "Ramp simulators and ingestion". Broker: `mosquitto:1883`, client ID `nodered` (K7).

## What it does

- Two identical simulators, parametrised by vehicle: `BUS-DEMO-01` / `ramp-node-01` and `BUS-DEMO-02` / `ramp-node-02`.
- A new sample every 5 s and, immediately, when the limits change, on `telemetry/vehicles/{id}/ramp` (QoS 0, not retained). The format is the one of `contracts/samples/telemetry.json`.
- On deploy, an initialiser creates a session UUID per node, stores it as the expected session, creates a `projectionRunId` per vehicle and publishes a retained `UNKNOWN` / `STALE` projection on `vehicles/{id}/ramp/state` before the first sample.
- State lives in flow context (memory only): after restarting Node-RED nothing is restored, as the proposal requires.
- Dashboard (page "Simulators", group "Operation"): a vehicle selector and seven buttons. A button acts on the selected vehicle (`BUS-DEMO-01` by default).

## Scenarios

| Button | Behaviour |
|---|---|
| Normal | Walks the ramp: stowed, intermediate (5 s), deployed; pressed again it goes back. From an intermediate or contradictory state it goes to deployed |
| Incomplete | Goes to intermediate `(false,false)` and stays there |
| Contradiction | Both limits true `(true,true)` |
| Duplicate | Sends the last sample again, same `sequence` |
| Out of order | Sends the previous sample again, lower `sequence` |
| Cut / resume | Stops or restarts the publication of that vehicle. While it is cut, every command except Restart and Cut / resume is ignored |
| Restart | New session UUID, `sequence` back to 0 (next sample is 1), limits back to stowed, expected session updated, ingestion state of that node cleared. A message is sent for B03 |

## Results

| Test | Observed | Image |
|---|---|---|
| Deploy | Two retained `UNKNOWN` / `STALE` projections, one per vehicle, with their own `projectionRunId` | 03-scenarios.jpg |
| Every 5 s | One sample per vehicle, `sequence` increasing, each node with its own `sessionId` | 03-scenarios.jpg |
| Contradiction | `BUS-DEMO-01`, sequence 18, `stowedLimit` and `deployedLimit` both true, between two ticks | 03-scenarios.jpg |
| Restart | New `sessionId`, next sample is `sequence` 1 with the limits back to `(true,false)` | 03-scenarios.jpg |
| Incomplete | `sequence` 2, `(false,false)` | 03-scenarios.jpg |
| Cut / resume | No sample from `BUS-DEMO-01` at 19:30:40 and 19:30:45, back at 19:30:50 with `sequence` 3; `BUS-DEMO-02` kept publishing (17 to 23) | 03-scenarios.jpg |
| Normal (from intermediate) | `sequence` 4, `(false,true)`, immediately | 03-scenarios.jpg |
| Duplicate | `sequence` 5 published twice at 19:30:55 | 03-scenarios.jpg |
| Out of order | The previous sample is sent again with a lower `sequence` | 04-out-of-order.jpg |
| Second vehicle | Buttons with `BUS-DEMO-02` selected only change that vehicle | 05-second-vehicle.jpg |

The four limit combinations (`(true,false)`, `(false,false)`, `(false,true)`, `(true,true)`) were observed on `BUS-DEMO-01`.

## How to run it

1. `docker compose up -d`.
2. Load the flow: `docker cp nodered/flows.json nodered_service:/data/flows.json` and `docker restart nodered_service`.
3. Watch the traffic: `mosquitto_sub -h localhost -t 'telemetry/#' -t 'vehicles/+/ramp/state' -F '%I %t %p'`.
4. Open `http://localhost:1880/dashboard` and use the buttons.

## Not covered here

The ingestion, the quality rules and the projection after the first `UNKNOWN` are B03. The operation dashboard (ramp state, presence, requests, demo reset) is B04. The TD01, TD02 and TD03 sheets are B07.