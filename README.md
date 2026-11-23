# AccessBus

AccessBus allows a passenger who needs a ramp to request assistance at a bus stop before the bus arrives. The driver receives the request in the application's HMI and can accept or reject it.

This is a laboratory prototype developed as part of the Internet of Things Master's programme (Universidad Politécnica de Madrid):

- The four stops are based on open data from the Madrid Regional Transport Consortium (CRTM).
- The bus, trip and ramps are simulated.
- Optionally, an ESP32 controls a physical ramp mock-up.

## 1. Architecture and components

The system consists of:

- **Android application** (Kotlin and Jetpack Compose): a single APK with passenger and driver profiles.
- **API** (FastAPI and PostgreSQL): manages assistance requests, states, idempotency keys, synchronisation (`/sync`) and an outbox used to publish MQTT events.
- **Mosquitto**: MQTT broker for events, HMI receipts, presence and ramp telemetry.
- **Node-RED**: ramp simulators, MQTT integration, operational dashboard and optional physical mock-up panel.
- **Physical ramp node** (optional ESP32): ramp mock-up with two limit switches.

Further details about the architecture and contracts are available in `contracts/openapi.yaml`, `contracts/mqtt.md` and `docs/nodered-flows.pdf`.

## 2. Repository structure

```text
.
├── app/                 Android application
├── api/
│   ├── app/             API implementation (see api/app/README.md)
│   └── tests/           Smoke tests and OpenAPI export
├── contracts/           REST and MQTT contracts, manifest and examples
├── docs/                Stop data and Node-RED flow documentation
├── firmware/            ESP32 physical node (see firmware/README.md)
├── mosquitto/           Broker configuration
├── nodered/             Node-RED image and flows
├── scripts/
│   ├── up.sh            Starts the laboratory
│   └── reset.sh         Creates a new demo trip
├── docker-compose.yml
└── .env.example
```

## 3. Prerequisites

To run the laboratory:

- Docker Desktop with Docker Compose.
- Git.
- `bash` and `curl` to run scripts and tests from a terminal.
- Android Studio to build and run the Android application.
- An Android phone or emulator, if the application is to be tested.

On Windows, `bash` can be run from Git Bash. You do not need to use Docker Desktop's internal `docker-desktop` WSL distribution to run the scripts.

## 4. Set up and start the laboratory

### 4.1 Initial configuration

From the repository root, create the `.env` file:

**Git Bash / Linux / macOS**
```bash
cp .env.example .env
```

**Windows PowerShell**
```powershell
Copy-Item .env.example .env
```

`.env.example` contains the PostgreSQL development settings:

```dotenv
POSTGRES_DB=accessiblebus
POSTGRES_USER=accessiblebus
POSTGRES_PASSWORD=accessiblebus_dev_password
```

The `.env` file must not be committed to the repository or shared as a production configuration. These are development credentials for the laboratory environment.

### 4.2 Start the services

From the repository root:

```bash
bash scripts/up.sh
```

If `.env` does not exist, the script creates it from `.env.example` and then runs `docker compose up -d --build`.

You can also start the services directly:

```bash
docker compose up -d --build
```

Check their status:

```bash
docker compose ps
```

All four services should be running. PostgreSQL has a health check, and the API waits until the database is available.

| Service | Published port | Local access |
|---|---:|---|
| Mosquitto | 1883 | `localhost:1883` |
| Node-RED | 1880 | `http://localhost:1880` |
| Node-RED Dashboard | 1880 | `http://localhost:1880/dashboard` |
| API | 8000 | `http://localhost:8000` |
| Swagger UI | 8000 | `http://localhost:8000/docs` |
| PostgreSQL | 5432 | `127.0.0.1:5432` (local interface only) |

Within the Docker Compose network, services communicate using their service names: `mosquitto:1883`, `postgres:5432`, `nodered:1880` and `api:8000`.

### 4.3 Check the API

```bash
curl http://localhost:8000/health
```

The expected response is similar to:

```json
{"status":"ok","schemaVersion":1}
```

To browse the available endpoints, open `http://localhost:8000/docs`.

### 4.4 Stop the laboratory

To stop and remove this project's containers:

```bash
docker compose down
```

Persistent volumes are retained. To start the laboratory again:

```bash
bash scripts/up.sh
```

**Do not run `docker compose down -v` unless you intend to delete the persistent volumes as well**, including PostgreSQL, Node-RED and Mosquitto data.

## 5. Demo users and credentials

The API uses fixed Bearer tokens. These are laboratory credentials and are not an appropriate security mechanism for production.

| Token | Role | Associated identity | Usage |
|---|---|---|---|
| `demo-pasajero-1` | Passenger | `passenger-1` | Passenger assistance requests |
| `demo-conductor-1` | Driver | `driver-1`, vehicle `BUS-DEMO-01`, terminal `hmi-demo-01` | HMI operations |
| `demo-operador-1` | Operator | `operator-1` | Demo reset and authorised synchronisation |

### 5.1 How the Android application uses tokens

In the current Android code:

- The passenger API client uses `demo-pasajero-1`, defined through `BuildConfig.API_TOKEN` in `app/build.gradle.kts`.
- The driver API client uses `demo-conductor-1`, defined in `AppContainer.kt`.
- Node-RED uses `demo-operador-1` for the demo reset operation.
- The operator token is also used by `scripts/reset.sh`.

## 6. Configure the Android application

### 6.1 Open and build the project

1. Open the project folder in Android Studio.
2. Wait for Gradle synchronisation to finish.
3. Select the `app` run configuration.
4. Connect an Android device or start an emulator.
5. Run the application.

You can also build the debug APK from the repository root:

```bash
./gradlew assembleDebug
```

On Windows, from Git Bash:

```bash
./gradlew.bat assembleDebug
```

### 6.2 Network configuration

The laboratory publishes the API on port `8000` and MQTT on port `1883`. The phone must be able to reach the IP address of the computer running Docker Desktop.

The application's default configuration is intended for a computer sharing a Windows mobile hotspot. In that setup, the computer's usual address is `192.168.137.1`:

- API: `http://192.168.137.1:8000/`
- MQTT broker: `192.168.137.1`
- MQTT port: `1883`

For this setup:

1. Enable the Windows mobile hotspot.
2. Connect the phone and, if used, the ESP32 to that network.
3. Check that the computer has the expected IP address.
4. Allow the required inbound connections on ports `8000` and `1883` in the Windows firewall.
5. Check that the phone can reach the computer.

The address `192.168.137.1` is common for the Windows mobile hotspot configuration. If your network uses a different address, configure the computer's actual IP address instead.

Other common configurations:

| Scenario | API URL | Broker host | Port |
|---|---|---|---:|
| Windows mobile hotspot (default) | `http://192.168.137.1:8000/` | `192.168.137.1` | `1883` |
| Android device connected over USB with `adb reverse` | `http://127.0.0.1:8000/` | `127.0.0.1` | `1883` |
| Android emulator | `http://10.0.2.2:8000/` | `10.0.2.2` | `1883` |
| Another computer on the local network | `http://COMPUTER_IP:8000/` | `COMPUTER_IP` | `1883` |

To use `adb reverse` with a USB-connected device:

```bash
adb reverse tcp:8000 tcp:8000
adb reverse tcp:1883 tcp:1883
```

The broker field also accepts a `host:port` value.

### 6.3 Connection preferences

In the application, open **Preferences → Connection**.

There are two connection profiles:

- **Laboratory**: uses the build-time default values intended for the local laboratory.
- **Course**: allows a separate connection configuration to be saved for the course environment.

For the selected profile, you can configure:

- **API URL**: API base address, for example `http://192.168.137.1:8000/`.
- **Broker host**: broker IP address or hostname.
- **Port**: MQTT port, normally `1883`.

Press **Save** to store the configuration. Connection changes are applied when the application is started again, so close and reopen the application after changing them.

When selecting `Course`, enter the connection details provided for that environment. Empty fields may cause the application to fall back to build-time defaults; do not assume that leaving them empty disables the connection.

**Important:** changing the profile or connection addresses does not change the application's tokens. Credentials remain as described in the previous section.

### 6.4 Build parameters

Default values can be overridden at build time:

```bash
./gradlew assembleDebug -PapiBaseUrl=http://127.0.0.1:8000/ -PmqttBroker=tcp://127.0.0.1:1883
```

To build the application with sample data included in `assets/samples`, without using the laboratory backend:

```bash
./gradlew assembleDebug -PuseSampleApi=true
```

The sample-data mode is not equivalent to a backend connection and cannot be used to validate real MQTT communication.

## 7. Using the Android application

When the application opens, the **Select your profile** screen is displayed. Choose one of the two profiles.

### 7.1 Passenger profile

The passenger's usual workflow is:

1. Select **Passenger**.
2. Browse the available stops.
3. Select the stop where assistance is needed.
4. Open the stop details.
5. Confirm the assistance request.
6. Open the tracking screen to check the request status.

The application uses the stop manifest included in its resources and synchronises trip and request information with the API.

The passenger can open **Preferences** from screens that provide access to it, to change accessibility and connection settings.

### 7.2 Driver profile

1. Select **Driver**.
2. The HMI for vehicle `BUS-DEMO-01` opens.
3. Review incoming requests.
4. Accept or reject the available requests.
5. Use the trip-progress actions when appropriate.
6. Check the ramp status displayed by the HMI.

The HMI uses MQTT to receive events and communicate presence/receipts. When it reconnects, the application synchronises its state with the API again.

### 7.3 Accessibility preferences

The **Preferences** screen provides the following settings:

- **Voice**: announcement mode (`Auto`, `Always` or `Off`).
- **Vibration**: enables or disables vibration when a driver decision is received.
- **High contrast**: enables the high-contrast theme.
- **Test voice**: plays a test voice announcement.
- **Connection**: connection profile and settings described above.

## 8. Node-RED dashboard

Open:

- Editor: `http://localhost:1880`
- Dashboard: `http://localhost:1880/dashboard`

The flows are stored in `nodered/flows.json` and are included in the container image. If you make changes in the editor, export them to that file to preserve them when the container is recreated.

The dashboard contains the **Simulators** page and several groups:

- **Operation**: controls for test scenarios (for example, Normal, Duplicate, Out of order and Restart).
- **Ramps**: status of simulated ramps and the physical ramp test panel, when an ESP32 is used.
- **HMI presence**: latest presence state reported by the HMI.
- **Requests**: request information.
- **Demo**: **Reset demo (new trip)** button and the result of the last reset.

The **Reset demo (new trip)** button asks the API to create a new demo trip. It does not delete persistent data or recreate containers.

For details about each flow, see `docs/nodered-flows.pdf`.

## 9. Reset the demo scenario

The demo reset creates a new demo trip through the `POST /demo/reset` endpoint. The previous trip is ended and new visits to the four stops are created.

From the repository root:

```bash
bash scripts/reset.sh
```

The script asks for confirmation. To run it without an interactive prompt:

```bash
bash scripts/reset.sh --yes
```

The script requires the API to be available and uses these defaults:

- API: `http://localhost:8000`
- Operator token: `demo-operador-1`

They can be overridden with environment variables:

```bash
API_URL=http://192.168.137.1:8000 OPERATOR_TOKEN=demo-operador-1 bash scripts/reset.sh
```

The reset operation:

- Ends the active demo trip.
- Creates a new active trip and its four stop visits.
- Does not stop or restart containers.
- Does not delete volumes or persistent PostgreSQL, Node-RED or Mosquitto data.

The script generates a new idempotency key for each intentional execution. If confirmation is cancelled, no request is sent to the API.

## 10. Tests and checks

### 10.1 API smoke test

```bash
bash api/tests/smoke.sh
```

The test checks health, reset, request creation, idempotency-key replay and conflict, acceptance, completion and synchronisation using the three tokens.

**This test modifies the demo scenario**, so do not run it during a demonstration whose state you want to preserve.

It requires `curl` and Python 3. To target a different API address:

```bash
API_URL=http://COMPUTER_IP:8000 bash api/tests/smoke.sh
```

### 10.2 Android unit tests

```bash
./gradlew testDebugUnitTest
```

### 10.3 View service logs

```bash
docker compose logs -f api
docker compose logs -f mosquitto
docker compose logs -f nodered
docker compose logs -f postgres
```

Press `Ctrl+C` to stop viewing logs. This does not stop the containers.

### 10.4 Inspect MQTT messages

If `mosquitto_sub` and `mosquitto_pub` are installed, vehicle messages can be monitored with:

```bash
mosquitto_sub -h localhost -t "vehicles/BUS-DEMO-01/#" -v
```

Topics, payload formats, QoS, retained messages and Last Will are documented in `contracts/mqtt.md`.

## 11. Data and persistence

Docker Compose uses the following volumes:

- `postgres_data`: PostgreSQL data.
- `nodered_data`: Node-RED persistent data.
- `mosquitto_data`: Mosquitto persistence data.
- `mosquitto_log`: Mosquitto logs.

`docker compose down` retains these volumes. `docker compose down -v` deletes them. The demo reset (`scripts/reset.sh`) does not delete any of them.

## 12. Additional documentation

- `api/app/README.md`: API startup, shutdown and background processes.
- `contracts/openapi.yaml`: REST API contract.
- `contracts/mqtt.md`: MQTT messaging contract.
- `contracts/samples/`: example messages and responses.
- `docs/stop-data.md`: source and selection of the stops.
- `docs/nodered-flows.pdf`: Node-RED flow documentation.
- `firmware/README.md`: ESP32 hardware, wiring, build and operation.

The OpenAPI contract can be exported from the running API with:

```bash
bash api/tests/export_openapi.sh
```

This requires Python 3 with PyYAML. Do not edit `contracts/openapi.yaml` manually; it must be generated from the API.

## 13. Known limitations

- The bus, trip and simulated ramps are laboratory data. There is no operational connection to real EMT buses.
- Tokens are fixed and the MQTT broker allows anonymous access in the laboratory configuration. Do not expose this environment to a public network.
- The scenario uses one demo vehicle (`BUS-DEMO-01`) and one passenger identity.
- Passenger decision announcements depend on the application being visible and available; announcements while the screen is off would require a foreground service and push notifications.
- The ESP32 node is optional and controls a mock-up, not a real vehicle ramp.
