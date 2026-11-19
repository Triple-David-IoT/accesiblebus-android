# HMI ramp card with the physical ramp

Run on 17 Nov 2026 with the real ramp node: the ESP32 (`ramp-esp32-01`, node `ramp-phys-01`)
publishing to the laboratory broker, and the HMI on a physical phone (Aquaris C, Android 8.1)
reaching the laboratory API and broker over USB (`adb reverse` and an SSH tunnel to the VM).
Every state below came from the node's own limit switches and actuator messages.

| Screenshot | What happened | What the card shows |
| --- | --- | --- |
| `01-stowed.png` | Ramp at rest | "Stowed", "Stowed limit ON · Deployed limit OFF". Only Deploy is enabled |
| `02-deploying.png` | Deploy ramp tapped | The node reports DEPLOYING: "Deploying…", both maneuvers locked, red "Stop ramp" |
| `03-deployed.png` | Deployed limit reached | "Deployed", "Stowed limit OFF · Deployed limit ON". Only Stow is enabled |
| `04-between-limits.png` | Deploy, then Stop ramp after about 1 s | The node stops in INTERMEDIATE: "Between limits", no guessed position, both maneuvers enabled |
| `05-no-live-data.png` | The phone lost the broker for about 30 s | "No live data", "Last sample 28s ago", alarm "No recent data from the ramp node. Controls locked." and both buttons disabled |
| `06-recovered-stowed.png` | Broker back, then Stow ramp | The card recovered on its own within 10 s, and the ramp went back to "Stowed" |

## MQTT seen on the broker

Each tap published one command with a fresh `commandId`, and the node answered on its actuator
topic:

```text
commands/vehicles/BUS-DEMO-01/ramp {"schemaVersion":3,"commandId":"bfc4d68e-…","action":"DEPLOY"}
devices/ramp-esp32-01/ramps/ramp-phys-01 {…,"position":"STOWED","motion":"DEPLOYING",…}
commands/vehicles/BUS-DEMO-01/ramp {"schemaVersion":3,"commandId":"a5421964-…","action":"STOP"}
devices/ramp-esp32-01/ramps/ramp-phys-01 {…,"position":"INTERMEDIATE","motion":"IDLE",…}
```

## Not run on the hardware

A fault (TIMEOUT) and contradictory limit switches need the ramp to be blocked or a switch to
be forced. `PhysicalRampTest` covers both: the title, the alarm text and which buttons stay
enabled.
