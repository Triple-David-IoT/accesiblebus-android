# AccessBus

AccessBus lets a passenger who needs the ramp ask for it at a bus stop before the bus arrives, and lets the driver answer from a terminal in the cab. It is a laboratory prototype for the Master in Internet of Things (Universidad Politécnica de Madrid):

- the four stops are real open data from the Madrid transport consortium (CRTM);
- the bus, the trip and the ramps are simulated;
- an optional ESP32 drives a model ramp.

The system has five parts:

- **Android app** (Kotlin, Jetpack Compose): one APK with two profiles. The passenger picks a stop, asks for assistance and follows the request. The driver's HMI accepts or rejects requests, marks stops as passed and shows the ramp.
- **API** (FastAPI, PostgreSQL): commands with idempotency keys, versioned requests, `GET /sync`, and an outbox that publishes every change to MQTT with QoS 1.
- **Mosquitto**: the MQTT broker for events, HMI receipts, presence with Last Will, and ramp telemetry.
- **Node-RED**: the ramp simulators, the ingestion that projects the ramp state, the operation dashboard and a laboratory panel for the physical ramp.
- **Physical ramp node** (ESP32, optional): a model ramp with two limit switches.

## Repository layout

```text
.
├── app/                 Android app
├── api/
│   ├── app/             API code (see api/app/README.md)
│   └── tests/           smoke test and OpenAPI export
├── contracts/           REST (openapi.yaml) and MQTT (mqtt.md) contracts, stop manifest, sample messages
├── docs/                stop data selection and the Node-RED flows (PDF)
├── firmware/            physical ramp node (see firmware/README.md)
├── mosquitto/           broker configuration
├── nodered/             Node-RED image and flows
├── scripts/up.sh        starts the laboratory
├── docker-compose.yml
└── .env.example
```

## Laboratory

### Requirements

Docker with Docker Compose, Git, and optionally `mosquitto_pub` and `mosquitto_sub` for manual tests.

### Start

```bash
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
docker compose up -d --build  # or: bash scripts/up.sh
docker compose ps
```

All four services should be running. PostgreSQL must pass its health check before the API starts.

| Service | Image | Endpoint |
| --- | --- | --- |
| Mosquitto | `eclipse-mosquitto:2.1.2-alpine` | `localhost:1883` |
| Node-RED | `nodered/node-red` 4.1.3 with Dashboard 2.0 | editor `http://localhost:1880`, dashboard `http://localhost:1880/dashboard` |
| API | Python 3.12, FastAPI | `http://localhost:8000`, Swagger UI at `/docs` |
| PostgreSQL | `postgres:16.10` | `127.0.0.1:5432` (local only) |

`.env` holds the PostgreSQL credentials and must not be committed. Inside the Compose network the services reach each other by name: `mosquitto:1883`, `postgres:5432`, `nodered:1880`, `api:8000`.

### Demo identities

The API uses fixed bearer tokens. This is laboratory access, not real security.

| Token | Role | Identity |
| --- | --- | --- |
| `demo-pasajero-1` | Passenger | `passenger-1` |
| `demo-conductor-1` | Driver | `driver-1`, vehicle `BUS-DEMO-01`, terminal `hmi-demo-01` |
| `demo-operador-1` | Operator | `operator-1` (demo reset, and `/sync` of any vehicle) |

The app uses the passenger and driver tokens. Node-RED uses the operator token. The **Reset demo** button of the dashboard starts a new demo trip.

### Node-RED

The flows are in `nodered/flows.json` and are built into the image, so `docker compose up -d --build` loads them. A deploy from the editor lasts until the container is recreated, so export it to `nodered/flows.json`. `docs/nodered-flows.pdf` shows the three flows and what each one does.

### MQTT

`mosquitto/mosquitto.conf` enables port 1883, persistence and anonymous access. Anonymous access is for the laboratory only. The topics, QoS, retained messages and Last Will are defined in `contracts/mqtt.md`.

```bash
mosquitto_sub -h localhost -t "vehicles/BUS-DEMO-01/#" -v
```

### API

```bash
curl http://localhost:8000/health     # {"status":"ok","schemaVersion":1}
bash api/tests/smoke.sh               # API_URL=http://<host>:8000 for another machine
```

The smoke test checks health, demo reset, request creation, a repeated and a conflicting idempotency key, accept, complete and `/sync` with the three tokens. It needs `curl` and Python 3, and it resets the demo trip, so do not run it during a demo.

`contracts/openapi.yaml` is exported from the running API with `bash api/tests/export_openapi.sh` (needs Python 3 with PyYAML); do not edit it by hand. Real example responses are in `contracts/samples`.

### Data, logs and stop

The volumes `postgres_data`, `nodered_data`, `mosquitto_data` and `mosquitto_log` survive `docker compose down`. **`docker compose down -v` deletes them.**

```bash
docker compose logs -f api            # or mosquitto, nodered, postgres
docker compose down
```

## Android app

Open the project in Android Studio and run the `app` configuration, or build it with `./gradlew assembleDebug`.

By default the app talks to the laptop that runs Docker Compose and shares a **Windows mobile hotspot**. Windows always gives that laptop `192.168.137.1`, so the API is `http://192.168.137.1:8000/` and the broker `192.168.137.1:1883`. To use this setup:

- Set the hotspot to the **2.4 GHz** band, because the ESP32 has no 5 GHz radio.
- Join the phones and the ESP32 to it.
- Allow ports 8000 and 1883 in the Windows firewall.

Any other address goes in **Preferences → Connection** (API URL, broker host and port), and applies the next time the app starts:

| Where the laboratory runs | API URL | Broker host |
| --- | --- | --- |
| Laptop hotspot (default) | `http://192.168.137.1:8000/` | `192.168.137.1` |
| Phone on USB, with `adb reverse tcp:8000 tcp:8000` and `adb reverse tcp:1883 tcp:1883` | `http://127.0.0.1:8000/` | `127.0.0.1` |
| Android emulator on the same computer | `http://10.0.2.2:8000/` | `10.0.2.2` |
| Another computer on a network that lets devices reach each other | `http://<its IP>:8000/` | `<its IP>` |

The broker field also accepts `host:port`. A build can change the defaults with `-PapiBaseUrl=... -PmqttBroker=tcp://host:port`, and `-PuseSampleApi=true` runs the app on `assets/samples` without a laboratory.

Unit tests: `./gradlew testDebugUnitTest`.

## Physical ramp node

Optional. Wiring, configuration and flashing are in `firmware/README.md`. The node publishes the same telemetry as the simulator. The driver's HMI, or the laboratory panel in Node-RED, can deploy and stow it.

## Limitations

- The bus, the trip and the simulated ramps are laboratory data. There is no connection to real EMT vehicles.
- Fixed demo tokens and an anonymous MQTT broker: not suitable for a public network.
- One demo vehicle (`BUS-DEMO-01`) and one passenger.
- The passenger is told of decisions while the app is visible; announcements with the screen off would need a foreground service and push notifications.
