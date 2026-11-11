# AccessibleBus — Docker Infrastructure (C01)

## 1. Overview

This file describes the Docker Compose infrastructure for the AccessibleBus project.

The environment provides the services required for local development and integration:

- **Mosquitto:** MQTT message broker.
- **Node-RED:** flow-based integration and dashboard.
- **FastAPI:** backend API.
- **PostgreSQL:** relational database.

The infrastructure is designed to provide a reproducible development environment for the project team.

## 2. Requirements

The following software is required:

- Docker Desktop with Docker Compose support.
- Git.
- A terminal (PowerShell, Bash, or equivalent).
- An MQTT client such as `mosquitto_pub` and `mosquitto_sub` for testing.

Docker Desktop must be running before starting the services.

## 3. Project Structure

```text
AccessibleBus/
├── app/                         # Android application
├── api/
│   ├── Dockerfile
│   ├── app/                     # API code; see api/app/README.md
│   ├── requirements.txt
│   └── tests/
│       ├── export_openapi.sh
│       └── smoke.sh
├── mosquitto/
│   └── mosquitto.conf
├── nodered/
│   ├── Dockerfile
│   └── package.json
├── scripts/
│   └── up.sh
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

## 4. Docker Images and Versions

The Compose configuration uses the following image families:

| Service | Image | Version |
|---|---|---|
| Mosquitto | `eclipse-mosquitto` | 2.1.2-alpine |
| Node-RED | `nodered/node-red` | 4.1.3 |
| API | `python` | 3.12-slim |
| PostgreSQL | `postgres` | 16.x |

The exact image tags are defined in `docker-compose.yml`.

Node-RED Dashboard 2.0 is declared in `nodered/package.json` and installed by `nodered/Dockerfile`.

## 5. Environment Configuration

The project uses environment variables to configure PostgreSQL and the API.

Create a local `.env` file from the example:

### Linux / macOS

```bash
cp .env.example .env
```

### Windows PowerShell

```powershell
Copy-Item .env.example .env
```

The example configuration contains:

```dotenv
POSTGRES_DB=accessiblebus
POSTGRES_USER=accessiblebus
POSTGRES_PASSWORD=accessiblebus_dev_password
```

The `.env` file is local configuration and must not be committed to Git.

The `.env.example` file is committed so that other team members can reproduce the environment.

## 6. Starting the Environment

From the repository root, validate the Compose configuration:

```bash
docker compose config
```

Start and build the services:

```bash
docker compose up -d --build
```

Alternatively, use the provided script:

```bash
bash scripts/up.sh
```

On Windows, the Compose command can be executed directly from PowerShell:

```powershell
docker compose up -d --build
```

Check the status of the services:

```bash
docker compose ps
```

All four services should be running. PostgreSQL must pass its health check before the API starts.

## 7. Service Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Mosquitto | `localhost:1883` | MQTT broker |
| Node-RED | `http://localhost:1880` | Node-RED editor |
| FastAPI | `http://localhost:8000` | Backend API |
| FastAPI Swagger UI | `http://localhost:8000/docs` | API documentation |
| PostgreSQL | `localhost:5432` | Database connection |

The API and PostgreSQL ports are bound to the local host in the Compose configuration.

## 8. MQTT Configuration

Mosquitto is configured through `mosquitto/mosquitto.conf`.

The development configuration includes:

```properties
listener 1883
allow_anonymous true

persistence true
persistence_location /mosquitto/data/

log_dest file /mosquitto/log/mosquitto.log
```

This configuration enables MQTT connections on port 1883 and persistence.

Anonymous access is enabled for the development environment. This configuration must not be used for a publicly exposed production broker.

### MQTT test

Subscribe to a test topic:

```bash
mosquitto_sub -h localhost -p 1883 -t "accessiblebus/test" -v
```

Publish a message from another terminal:

```bash
mosquitto_pub -h localhost -p 1883 -t "accessiblebus/test" -m "Hello AccessibleBus"
```

The subscriber should receive:

```text
accessiblebus/test Hello AccessibleBus
```

## 9. API Health Check

The initial API implementation provides a health endpoint:

```http
GET /health
```

Expected response:

```json
{
  "status": "ok",
  "schemaVersion": 1
}
```

Test it with:

```bash
curl http://localhost:8000/health
```

The API container runs Uvicorn with one worker:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

### Smoke test

With the stack running, `api/tests/smoke.sh` checks the main flow end to end:
health, demo reset, request creation, a repeated and a conflicting idempotency key,
accept, complete and `/sync` with the three demo tokens. It needs `curl` and Python 3.

```bash
bash api/tests/smoke.sh
API_URL=http://192.168.1.50:8000 bash api/tests/smoke.sh
```

It prints one `OK` line per step and `Smoke test passed`, or stops at the first
`FAIL` with exit code 1. It resets the demo trip, so do not run it during a demo.

### REST contract

`contracts/openapi.yaml` is exported from the running API, so it always matches the code.
After changing a route or a model, start the stack and run:

```bash
bash api/tests/export_openapi.sh
API_URL=http://192.168.1.50:8000 bash api/tests/export_openapi.sh
```

It needs `curl` and Python 3 with PyYAML. Do not edit the file by hand. Real example
responses for each resource are in `contracts/samples`. The interactive version is at
`http://localhost:8000/docs`.

## 10. Data Persistence

Docker volumes are used to preserve service data.

| Volume | Purpose |
|---|---|
| `postgres_data` | PostgreSQL database files |
| `nodered_data` | Node-RED flows and configuration |
| `mosquitto_data` | Mosquitto persistent data |
| `mosquitto_log` | Mosquitto logs |

Stopping or recreating the containers does not remove these volumes.

To stop the services while preserving data:

```bash
docker compose down
```

**Warning:** the following command removes the Compose volumes and deletes the persisted data:

```bash
docker compose down -v
```

Use it only when a complete reset is intended.

## 11. Logs and Troubleshooting

Display logs for all services:

```bash
docker compose logs -f
```

Display logs for a specific service:

```bash
docker compose logs -f api
```

```bash
docker compose logs -f mosquitto
```

```bash
docker compose logs -f postgres
```

Check the status of the containers:

```bash
docker compose ps
```

If a service fails to start, inspect its logs and verify the `.env` configuration.

## 12. Local Network Configuration

For applications running on the same computer, the MQTT broker is available at:

```text
localhost:1883
```

For the Android emulator, the host computer can normally be reached using:

```text
10.0.2.2:1883
```

For a physical Android device, use the host computer's LAN IP address, for example:

```text
192.168.1.50:1883
```

The example address must be replaced with the actual IP address of the computer running Docker.

The Android device and the host computer must be connected to a network that allows communication between them. The host firewall must permit the required connections.

Inside the Docker Compose network, services communicate using their service names:

- MQTT broker: `mosquitto:1883`
- PostgreSQL: `postgres:5432`
- Node-RED: `nodered:1880`
- API: `api:8000`

## 13. Evidence and Verification

The C01 infrastructure should be verified by checking:

1. The four Compose services are running.
2. PostgreSQL passes its health check.
3. The API health endpoint responds successfully.
4. The Node-RED editor is accessible.
5. MQTT messages can be published and received.
6. Persistent data is retained after restarting the containers.

Evidence should be stored under:

```text
docs/
└── evidence/
    └── C01/
        ├── 01-compose-ps.png
        ├── 02-api-health.png
        ├── 03-nodered.png
        ├── 04-mqtt-pc.png
        ├── 05-mqtt-device.png
        └── 06-persistence.png
```

These screenshots should be captured from the actual running environment.

## 14. Limitations

- The API currently provides a minimal health endpoint.
- MQTT anonymous access is enabled for development.
- The environment is intended for local development and integration.
- Production deployment requires appropriate authentication, access control, and network security.
- Node-RED flows and the complete backend functionality will be integrated as the project progresses.

## 15. Stopping the Environment

Stop the services:

```bash
docker compose down
```

The named volumes remain available for the next execution.
