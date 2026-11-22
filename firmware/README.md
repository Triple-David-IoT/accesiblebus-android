# Physical ramp node (ESP32)

An ESP32 drives a rack-and-pinion ramp (a continuous-rotation SG90 servo and two limit switches,
"stowed" and "deployed"). It publishes the same ramp telemetry as the Node-RED simulator and takes
the maneuver commands of the driver's HMI or of the laboratory panel's test mode. The MQTT contract is
in `contracts/mqtt.md` ("Physical ramp node").

## Hardware

| Part | Wiring |
| --- | --- |
| SG90 360° servo | Signal (orange) to D25 · red to +5 V of an external supply · brown to common GND |
| Stowed limit switch | COM to GND · NO to D32 |
| Deployed limit switch | COM to GND · NO to D33 |

- The servo is **not** powered from the ESP32: a 5 V supply of 2 A or more, its GND joined to a
  GND of the board, and a 470–1000 µF capacitor next to the servo.
- Blue LED: fast blink = no Wi-Fi, slow blink = no MQTT, steady = connected.

## Code

Four files, about 350 lines:

- `config.h`: identity, pins, calibration and timing.
- `Ramp.h` / `Ramp.cpp`: the ramp. The limit switches, not the servo, decide where a maneuver
  ends. A maneuver longer than `MANEUVER_TIMEOUT_MS`, or both switches closed at once, cut the
  servo and leave the ramp in `FAULT`. Nothing moves at boot.
- `app.cpp`: Wi-Fi, MQTT, the contract messages and the loop.

Libraries: **AsyncMqttClient** (asynchronous: connecting or reconnecting never blocks the loop, so
the limit switches are always watched), **ESP32Servo**, **Bounce2** (debounce) and **ArduinoJson**.
Commands arrive in the network task and reach the loop through a queue; only the loop touches the
ramp.

## Build and flash

Copy `ramp_node/secrets.example.h` to `ramp_node/secrets.h` with the Wi-Fi network (2.4 GHz) and
the broker. `secrets.h` is ignored by Git. Without it the code builds with the example values and
a warning.

```bash
cd firmware
pio run -t upload
```

PlatformIO installs the libraries of `platformio.ini`. Built with `espressif32` 6.13.0
(Arduino-ESP32 2.0.17): 14 % of RAM and 61 % of flash. The folder also opens as a sketch in the
Arduino IDE (`ramp_node/ramp_node.ino`, board "ESP32 Dev Module").

If the upload stays at `Connecting.....`, hold BOOT. Opening the serial monitor resets the board.

## Calibration

The values measured on the bench are in `config.h`: `DEPLOY_US = 1560`, `STOW_US = 1440` and
`MANEUVER_TIMEOUT_MS = 6000` (1.5 times the maneuver). The closer to 1500 µs, the slower. To
calibrate again, change them and flash.

## MQTT

| Topic | Direction | QoS / retain | Content |
| --- | --- | --- | --- |
| `telemetry/vehicles/BUS-DEMO-01/ramp` | node → ingest, HMI and laboratory panel | 0 / no | Telemetry sample, every 5 s and on every limit switch change |
| `commands/vehicles/BUS-DEMO-01/ramp` | HMI or laboratory panel → node | 1 / no | `{"schemaVersion":3,"commandId":"…","action":"DEPLOY"\|"STOW"\|"STOP"}` |
| `devices/ramp-esp32-01/status` | node → laboratory panel | 1 / yes | `online` on connect and Will `offline`, with the `sessionId` of the boot |
| `devices/ramp-esp32-01/ramps/ramp-phys-01` | node → HMI and laboratory panel | 0 / no | `position`, `motion`, `fault`, `lastCommandId` and `lastResult` |

- `sessionId` is a new UUID on every boot; `sequence` starts at 1 and grows with every sample.
- A repeated command (same `commandId`, the last eight) is ignored, because QoS 1 may deliver it
  twice. So is a command without `schemaVersion` 3 or `commandId`.
