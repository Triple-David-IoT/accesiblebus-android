# B04 — Operation dashboard

Node-RED 4.1.3 with Dashboard 2.0 1.29.0, new tab "B04 operation dashboard". It reuses the broker node `Mosquitto (compose)`, so there is no second MQTT connection. Texts use Europe/Madrid time; `reportedAt` from the HMI is UTC.

## What it shows

- **Ramps:** position, quality and sequence of each vehicle, read from `vehicles/+/ramp/state`.
- **HMI presence:** last report of `vehicles/+/presence`, with the time it was received.
- **Requests:** `/sync` once, 5 s after deploy (or with Resync), then `vehicles/+/events`. The highest `version` wins, `received` is an OR, and an ended trip never reopens. An event of another trip triggers a new sync. Requests of other trips that `/sync` returns (closings of the previous run) are ignored.
- **Demo:** the scenario buttons of B02, Resync and "Reset demo (new trip)", which calls `POST /demo/reset` with the operator token and a new `Idempotency-Key` on each click.

## Results

| Test | Observed | Image |
| --- | --- | --- |
| Overview | Both ramps, presence online, trip and buttons on one page | 01 |
| Independent ramps | Contradiction on BUS-DEMO-01: UNKNOWN · INVALID, while BUS-DEMO-02 stays STOWED · VALID | 02 |
| Request created (passenger app) | `request.created` and `request.receipt_recorded` at 13:40:08; the row shows `received: yes` with `updated 13:40:08`. Created at 12:40:07.720Z: under 1.3 s | 03 |
| Accepted in the HMI | `request.accepted` at 13:09:24; the row is ACCEPTED · v2, `updated 13:09:24` | 04 |
| Stop passed | `trip.progressed` at 13:17:10; header "last stop passed: 1" | 05 |
| Node-RED restart | New run `22f213cd`; list rebuilt by the sync (stop 456 ACCEPTED, last stop passed 1); presence kept by the retained message | 06 |
| Reset | Before: two PENDING requests of trip 8a168ace. After "Reset OK · new trip 294cf0d6 · 13:41:41": two `request.expired` (TRIP_ENDED) and `trip.ended` at 13:41:42, and "No requests". The earlier reset (13:36:03) had given trip 8a168ace, so each click makes a new trip | 07, 08 |
| Presence | Airplane mode on the emulator: `offline` (Will, no date) at 13:43:08, about 15–16 s later (by hand, ±1–2 s), within the 12–18 s measured in B01 (`docs/mqttastic.md`). `online` again at 13:43:18 | 09, 10 |

## Not covered

No automated tests; everything was checked by hand. The presence line is a technical report and says nothing about the driver's attention.