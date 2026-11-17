# MQTT contract (K2)

Broker: Mosquitto on the local lab network. Protocol: MQTT 3.1.1, clean session. After a reconnect, clients subscribe again and recover business state with `GET /sync` (K1).
Fixed client IDs (K7): `api-outbox`, `api-receipts`, `app-passenger-1`, `hmi-demo-01`, `nodered`, `ramp-esp32-01` (physical ramp node, proposal).
Demo identities: subject `passenger-1`, vehicle `BUS-DEMO-01`, terminal `hmi-demo-01`.
Every message carries `"schemaVersion": 3`. Dates are ISO 8601 UTC. One example per message is in `contracts/samples/`.

## Topics

| Topic | Publisher -> consumer | QoS | Retain |
| --- | --- | --- | --- |
| `telemetry/vehicles/{vehicleId}/ramp` | Ramp simulator or physical ramp node -> Node-RED ingest | 0 | no |
| `vehicles/{vehicleId}/ramp/state` | Node-RED -> HMI and dashboard | 0 | yes |
| `commands/vehicles/{vehicleId}/ramp` (proposal) | HMI or dashboard -> physical ramp node | 1 | no |
| `devices/{deviceId}/status` (proposal) | Physical ramp node -> Node-RED ingest and dashboard | 1 | yes |
| `devices/{deviceId}/ramps/{nodeId}` (proposal) | Physical ramp node -> HMI and dashboard | 0 | no |
| `users/{subject}/events` | API outbox -> passenger | 1 | no |
| `vehicles/{vehicleId}/events` | API outbox -> HMI and dashboard | 1 | no |
| `terminals/{terminalId}/receipts` | HMI -> API receipts subscriber | 1 | no |
| `vehicles/{vehicleId}/presence` | HMI -> dashboard | 1 | yes |

## Rules

- QoS 1 can repeat messages. Consumers deduplicate by `eventId`.
- A retained message is the last known reference, never proof that the data is current.
- A receipt means "received by the terminal". It does not mean read or accepted.
- Topics are a convention, not isolation (local network, fictitious data).
- With MQTT 3.1.1 the Will payload must be bytes, not a String (see `docs/mqttastic.md`).
- After every reconnect the HMI publishes its retained `online` presence again, because the broker publishes the Will `offline` when the old connection drops.

## Outbox events (`users/...` and `vehicles/...`)

The whole message is what `outbox_event.payload` stores (K3), and the publisher sends it unchanged. `eventId` equals the column `event_id` (UUID).
Envelope: `schemaVersion`, `eventId`, `eventType`, `tripId`, `payload`. `requestId` and `version` appear only in `request.*` events, as in the SQL schema.

| `eventType` | `payload` |
| --- | --- |
| `request.created`, `request.accepted`, `request.rejected`, `request.cancelled`, `request.completed`, `request.expired` | `AssistanceRequest` from K1 after the change. `request.expired` carries `closeReason` `STOP_PASSED` or `TRIP_ENDED` |
| `request.receipt_recorded` (proposal) | `{"received": true}`. Owner topic only. `version` is the current one, not incremented |
| `trip.progressed`, `trip.ended` (proposal) | `Trip` from K1 |

Samples: `event_request_accepted.json`, `event_receipt_recorded.json`, `event_trip_progressed.json`.

## HMI receipt (`terminals/{terminalId}/receipts`)

`eventId` is the `createdEventId` of the `request.created` event being acknowledged. The API checks that `requestId` belongs to that event (enforced by the composite foreign key in `terminal_receipt`, K3) and that the terminal is the one assigned to the vehicle (K7), and ignores anything else.
Sample: `receipt.json`.

## Ramp telemetry (`telemetry/vehicles/{vehicleId}/ramp`)

`sourceMode`: `SIMULATED` (Node-RED simulator) or `PHYSICAL` (ESP32 with two limit switches, proposal). The message is otherwise identical, so the ingest does not depend on the source.
A sample is published every 5 s and on every change of a limit switch. `sessionId` is a new UUID at every boot of the node and `sequence` starts at 1.
Sample: `telemetry.json`.

## Physical ramp node (proposal)

An ESP32 drives a rack-and-pinion actuator (continuous-rotation servo) and reads the `stowed` and `deployed` limit switches. One device can host several nodes, one per vehicle. The switches, not the servo, decide where a maneuver stops; the node cuts the servo and reports `FAULT` if a maneuver lasts longer than its timeout or both switches close at once. Nothing moves at boot.

### Commands (`commands/vehicles/{vehicleId}/ramp`)

`{"schemaVersion":3,"commandId":"<UUID>","action":"DEPLOY" | "STOW" | "STOP"}`, QoS 1, never retained (a retained command would run again on every reconnect).
The node ignores a `commandId` it has already seen, and messages with another `schemaVersion` or without `commandId`. `DEPLOY` on a deployed ramp and `STOW` on a stowed one answer `ALREADY_THERE` without moving.
Commands come from the driver's HMI (or the operator dashboard), never from the passenger app.
Sample: `ramp_command.json`.

### Node status (`devices/{deviceId}/status`)

Retained `online` after every connection, with the `sessionId` of the current boot and the nodes it hosts; the Will is the retained `offline` of the same session.
The ingest (B03) takes the expected `sessionId` of a physical node from this message, instead of fixing it itself as it does for the simulators.
Samples: `ramp_device_online.json`, `ramp_device_offline.json`.

### Actuator state (`devices/{deviceId}/ramps/{nodeId}`)

Published on every change of `motion`, `fault` or the last command result. Diagnostics only: the position for business decisions is the projection.
`motion`: `IDLE`, `DEPLOYING`, `STOWING`, `DRIVING` (calibration) or `FAULT`. `fault`: `NONE`, `TIMEOUT` or `CONTRADICTION`.
`lastResult`: `NONE`, `STARTED`, `ALREADY_THERE`, `STOPPED`, `REJECTED_CONTRADICTION` or `CONFIGURED`.
Sample: `ramp_actuator.json`.

## Ramp projection (`vehicles/{vehicleId}/ramp/state`) — proposal

Adds `projectionRunId`, `projectionSequence`, `sourceSequence`, `position` and `quality`.
`position`: `STOWED`, `DEPLOYED`, `INTERMEDIATE` or `UNKNOWN`. `quality`: `VALID`, `INVALID` or `STALE`.
Sample: `projection.json`.

## HMI presence (`vehicles/{vehicleId}/presence`) — proposal

`online` is published retained with the connection time. The Will `offline` is a fixed message with no date, so the dashboard stores the reception time.
Samples: `presence_online.json`, `presence_offline.json`.

## Open points for review

1. Payload of `request.receipt_recorded`.
3. Fields of the projection and presence messages (proposed by B).
4. Physical ramp node: commands, node status and actuator state (proposed by A), and how the ingest (B03) accepts the session of a physical node.