# MQTT contract (K2)

Broker: Mosquitto on the local lab network. Protocol: MQTT 3.1.1, clean session. After a reconnect, clients subscribe again and recover business state with `GET /sync` (K1).
Fixed client IDs (K7): `api-outbox`, `api-receipts`, `app-passenger-1`, `hmi-demo-01`, `nodered`.
Demo identities: subject `passenger-1`, vehicle `BUS-DEMO-01`, terminal `hmi-demo-01`.
Every message carries `"schemaVersion": 3`. Dates are ISO 8601 UTC. One example per message is in `contracts/samples/`.

## Topics

| Topic | Publisher -> consumer | QoS | Retain |
| --- | --- | --- | --- |
| `telemetry/vehicles/{vehicleId}/ramp` | Ramp simulator -> Node-RED ingest | 0 | no |
| `vehicles/{vehicleId}/ramp/state` | Node-RED -> HMI and dashboard | 0 | yes |
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

Sample: `telemetry.json`.

## Ramp projection (`vehicles/{vehicleId}/ramp/state`) — proposal

Adds `projectionRunId`, `projectionSequence`, `sourceSequence`, `position` and `quality`.
`position`: `STOWED`, `DEPLOYED`, `INTERMEDIATE` or `UNKNOWN`. `quality`: `VALID`, `INVALID` or `STALE`.
Sample: `projection.json`.

## HMI presence (`vehicles/{vehicleId}/presence`) — proposal

`online` is published retained with the connection time. The Will `offline` is a fixed message with no date, so the dashboard stores the reception time.
Samples: `presence_online.json`, `presence_offline.json`.

## Open points for review

1. Payload of `request.receipt_recorded`.
2. Topics that receive `trip.progressed` and `trip.ended` (decided in B05).
3. Fields of the projection and presence messages (proposed by B).