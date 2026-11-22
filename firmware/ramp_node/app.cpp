// Physical ramp node: one ESP32 and one ramp. It publishes the same K2 telemetry as the
// Node-RED simulator and takes the maneuver commands of the HMI or the lab dashboard.
//   telemetry/vehicles/{vehicle}/ramp  QoS 0, every 5 s and on every switch change
//   commands/vehicles/{vehicle}/ramp   QoS 1  {"schemaVersion":3,"commandId":"…","action":"DEPLOY"|"STOW"|"STOP"}
//   devices/{device}/status            retained "online"; retained Will "offline"; with the boot sessionId
//   devices/{device}/ramps/{node}      actuator state and result of the last command
//
// The MQTT client is asynchronous: connecting or reconnecting never blocks loop(), so the
// limit switches are watched all the time. Commands arrive in the network task and reach
// the ramp through a queue; only loop() touches the ramp.

#include <Arduino.h>
#include <ArduinoJson.h>
#include <AsyncMqttClient.h>
#include <WiFi.h>
#include <esp_random.h>

#include "Ramp.h"
#include "config.h"
#if __has_include("secrets.h")
#include "secrets.h"
#else
#warning "secrets.h not found: using the placeholders of secrets.example.h"
#include "secrets.example.h"
#endif

static const char* const STATUS_TOPIC = "devices/" DEVICE_ID "/status";
static const char* const STATE_TOPIC = "devices/" DEVICE_ID "/ramps/" NODE_ID;
static const char* const TELEMETRY_TOPIC = "telemetry/vehicles/" VEHICLE_ID "/ramp";
static const char* const COMMAND_TOPIC = "commands/vehicles/" VEHICLE_ID "/ramp";

struct Command {
  Action action;
  char id[40];   // the commandId: a UUID (36) fits
};

static AsyncMqttClient mqtt;
static Ramp ramp;
static QueueHandle_t commands;
static char sessionId[37];
static char willPayload[160];
static uint32_t sequence = 0;
static volatile bool justConnected = false;

// QoS 1 may deliver a command twice: the last commandIds are remembered and repeats ignored.
static char seen[8][sizeof(Command::id)];
static uint8_t seenNext = 0;

static void makeSessionId() {
  uint8_t b[16];
  esp_fill_random(b, sizeof(b));
  b[6] = (b[6] & 0x0F) | 0x40;   // UUID version 4
  b[8] = (b[8] & 0x3F) | 0x80;
  snprintf(sessionId, sizeof(sessionId),
           "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x", b[0], b[1],
           b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14],
           b[15]);
}

// ---------- Network task --------------------------------------------------------------
static void onConnect(bool) {
  Serial.println("[mqtt] connected");
  char birth[220];
  snprintf(birth, sizeof(birth),
           "{\"schemaVersion\":%d,\"deviceId\":\"%s\",\"sessionId\":\"%s\",\"status\":\"online\","
           "\"sourceMode\":\"%s\",\"nodes\":[{\"nodeId\":\"%s\",\"vehicleId\":\"%s\"}]}",
           SCHEMA_VERSION, DEVICE_ID, sessionId, SOURCE_MODE, NODE_ID, VEHICLE_ID);
  mqtt.publish(STATUS_TOPIC, 1, true, birth);
  mqtt.subscribe(COMMAND_TOPIC, 1);
  justConnected = true;   // loop() publishes the telemetry and the state at once
}

static void onDisconnect(AsyncMqttClientDisconnectReason reason) {
  Serial.printf("[mqtt] disconnected (%d)\n", (int)reason);
}

static void onMessage(char*, char* payload, AsyncMqttClientMessageProperties, size_t len,
                      size_t index, size_t total) {
  if (index != 0 || len != total) return;   // commands are small: never split

  JsonDocument doc;
  if (deserializeJson(doc, (const char*)payload, len)) return;
  const char* id = doc["commandId"] | "";
  const char* action = doc["action"] | "";
  if ((doc["schemaVersion"] | 0) != SCHEMA_VERSION || !*id || strlen(id) >= sizeof(Command::id)) {
    Serial.println("[mqtt] command without schemaVersion 3 or commandId, ignored");
    return;
  }

  Command cmd;
  if (!strcmp(action, "DEPLOY")) cmd.action = Action::DEPLOY;
  else if (!strcmp(action, "STOW")) cmd.action = Action::STOW;
  else if (!strcmp(action, "STOP")) cmd.action = Action::STOP;
  else return;

  for (const char* s : seen) {
    if (!strcmp(s, id)) {
      Serial.printf("[mqtt] repeated command %s, ignored\n", id);
      return;
    }
  }
  strlcpy(seen[seenNext], id, sizeof(seen[0]));
  seenNext = (seenNext + 1) % 8;

  strlcpy(cmd.id, id, sizeof(cmd.id));
  xQueueSend(commands, &cmd, 0);
}

// ---------- Publications ---------------------------------------------------------------
static void publishTelemetry() {
  char payload[300];
  snprintf(payload, sizeof(payload),
           "{\"schemaVersion\":%d,\"nodeId\":\"%s\",\"vehicleId\":\"%s\",\"sessionId\":\"%s\","
           "\"sequence\":%lu,\"sourceMode\":\"%s\",\"stowedLimit\":%s,\"deployedLimit\":%s}",
           SCHEMA_VERSION, NODE_ID, VEHICLE_ID, sessionId, (unsigned long)++sequence, SOURCE_MODE,
           ramp.stowed() ? "true" : "false", ramp.deployed() ? "true" : "false");
  mqtt.publish(TELEMETRY_TOPIC, 0, false, payload);
}

static void publishState() {
  char payload[340];
  snprintf(payload, sizeof(payload),
           "{\"schemaVersion\":%d,\"deviceId\":\"%s\",\"nodeId\":\"%s\",\"sessionId\":\"%s\","
           "\"position\":\"%s\",\"motion\":\"%s\",\"fault\":\"%s\",\"lastCommandId\":\"%s\","
           "\"lastResult\":\"%s\"}",
           SCHEMA_VERSION, DEVICE_ID, NODE_ID, sessionId, ramp.position(), ramp.motion(),
           ramp.fault(), ramp.commandId(), ramp.result());
  mqtt.publish(STATE_TOPIC, 0, false, payload);
}

// ---------- Arduino ----------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  ramp.begin();
  commands = xQueueCreate(8, sizeof(Command));

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  makeSessionId();   // after the radio starts, so the random numbers use its noise
  snprintf(willPayload, sizeof(willPayload),
           "{\"schemaVersion\":%d,\"deviceId\":\"%s\",\"sessionId\":\"%s\",\"status\":\"offline\"}",
           SCHEMA_VERSION, DEVICE_ID, sessionId);

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setClientId(DEVICE_ID);
  mqtt.setKeepAlive(MQTT_KEEPALIVE_S);
  mqtt.setCleanSession(true);
  mqtt.setWill(STATUS_TOPIC, 1, true, willPayload);
  mqtt.onConnect(onConnect);
  mqtt.onDisconnect(onDisconnect);
  mqtt.onMessage(onMessage);

  Serial.printf("\nramp node %s, session %s, %s\n", DEVICE_ID, sessionId, ramp.position());
}

void loop() {
  const uint32_t now = millis();

  Command cmd;
  while (xQueueReceive(commands, &cmd, 0) == pdTRUE) ramp.command(cmd.action, cmd.id, now);
  const bool switchChanged = ramp.update(now);

  static uint32_t loggedChanges = 0;
  if (ramp.changes() != loggedChanges) {
    loggedChanges = ramp.changes();
    Serial.printf("[ramp] %s motion=%s fault=%s result=%s %s\n", ramp.position(), ramp.motion(),
                  ramp.fault(), ramp.result(), ramp.commandId());
  }

  static uint32_t lastTelemetry = 0, publishedChanges = 0, lastMqttTry = 0, lastWifiTry = 0;
  if (mqtt.connected()) {
    const bool all = justConnected;
    justConnected = false;
    if (all || switchChanged || now - lastTelemetry >= TELEMETRY_PERIOD_MS) {
      publishTelemetry();
      lastTelemetry = now;
    }
    if (all || ramp.changes() != publishedChanges) {
      publishState();
      publishedChanges = ramp.changes();
    }
  } else if (WiFi.isConnected()) {
    if (now - lastMqttTry >= MQTT_RETRY_MS) {   // connect() returns at once
      lastMqttTry = now;
      mqtt.connect();
    }
  } else if (now - lastWifiTry >= WIFI_RETRY_MS) {
    lastWifiTry = now;
    WiFi.reconnect();
  }

  // LED: fast blink without Wi-Fi, slow blink without MQTT, steady when connected.
  const bool led = !WiFi.isConnected() ? (now / 100) % 2 : !mqtt.connected() ? (now / 500) % 2 : true;
  digitalWrite(LED_PIN, led ? HIGH : LOW);
  delay(1);
}
