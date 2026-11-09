# B12 — HMI presence and Will

## Configuration

- Android debug build and local Docker Compose broker.
- MQTT client ID: `hmi-demo-01`.
- Presence topic: `vehicles/BUS-DEMO-01/presence`.
- Keep alive: 15 seconds.
- Online presence and offline Will are retained.

## Results

| Test | Observed result | Evidence |
|---|---|---|
| Presence cycle | Offline and online messages observed | `01-presence-cycle.jpg` |
| Force-stop | Offline observed immediately after force-stopping the app; no precise latency measured | See test notes below |
| Silent drop | Process suspended at 16:41:26; offline received at 16:41:41: approximately 15 seconds | `02-silent-drop.jpg` |
| Resume | Online republished at 16:41:47 after resuming the process | `02-silent-drop.jpg` |
| Late subscriber | A new subscriber received the retained online presence | `03-retained-presence.jpg` |

## Procedure

- Force-stop: `adb shell am force-stop dte.masteriot.accessiblebus`.
- Silent drop: suspend the app process with `kill -STOP` using `run-as`.
- Resume the process with `kill -CONT`.
- Observe presence messages with local reception timestamps.

## Limitations

The force-stop test and the silent-drop test are different scenarios.
The 15-second measurement applies only to the silent-drop test.

Explicit offline publication during an orderly application shutdown
is not demonstrated by these screenshots.