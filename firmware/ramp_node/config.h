// Build-time configuration of the physical ramp node (one ESP32, one ramp).
#pragma once
#include <Arduino.h>

#define DEVICE_ID "ramp-esp32-01"   // MQTT clientId and devices/{DEVICE_ID}/...
#define NODE_ID "ramp-phys-01"
#define VEHICLE_ID "BUS-DEMO-01"
#define SOURCE_MODE "PHYSICAL"      // the Node-RED simulator uses "SIMULATED"
#define SCHEMA_VERSION 3

// ESP32 DevKit V1. Limit switches: COM to GND, NO to the pin, so pressed = LOW.
constexpr uint8_t SERVO_PIN = 25;
constexpr uint8_t STOWED_PIN = 32;
constexpr uint8_t DEPLOYED_PIN = 33;
constexpr uint8_t LED_PIN = 2;      // blue LED of the board

// Continuous-rotation SG90: about 1500 us stands still; each side turns one way.
// Values measured on the bench.
constexpr uint16_t DEPLOY_US = 1560;
constexpr uint16_t STOW_US = 1440;
constexpr uint32_t MANEUVER_TIMEOUT_MS = 6000;   // 1.5 x the measured maneuver

constexpr uint32_t DEBOUNCE_MS = 25;
constexpr uint32_t TELEMETRY_PERIOD_MS = 5000;   // K2: every 5 s and on every switch change
constexpr uint16_t MQTT_KEEPALIVE_S = 15;
constexpr uint32_t MQTT_RETRY_MS = 3000;
constexpr uint32_t WIFI_RETRY_MS = 15000;
