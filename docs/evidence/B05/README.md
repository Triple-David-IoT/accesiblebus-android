# B05 — advance and end of the trip

Local stack in Docker Compose (postgres, mosquitto, api). Tokens of K7.

| Test | Result | Image |
|---|---|---|
| `advance` from 0 | `lastPassedSequence` 1 | 01-advance-and-state.jpg |
| Same key and body again | Same response, no second event | 01-advance-and-state.jpg |
| `advance` from 0 with a new key | 409 `VERSION_CONFLICT` with the current trip | 01-advance-and-state.jpg |
| `advance` with the passenger token | 403 | 01-advance-and-state.jpg |
| State after passing the stop | Pending request of that visit `EXPIRED` / `STOP_PASSED`; accepted request stays `ACCEPTED` | 01-advance-and-state.jpg |
| Complete the accepted request, then `end` | `COMPLETED`; trip `ENDED` | 02-complete-and-end.jpg |
| MQTT events | `trip.progressed`, `trip.ended`, `request.expired` on the vehicle topic and on the owner topic | 03-events.jpg |
| Accept vs advance at the same time | See 04-race.jpg: no impossible state | 04-race.jpg |
| Operations on an ended trip | 409 `TRIP_NOT_ACTIVE` with the current resource | 05-ended-trip-rejected.jpg |

This is the "progress" part of TD04.