// The ramp: a continuous-rotation servo and two limit switches. The switches, not the servo,
// decide where a maneuver ends; a maneuver that lasts too long, or both switches closed at
// once, cut the servo and leave the ramp in FAULT. Nothing moves at boot.
#pragma once
#include <Arduino.h>
#include <Bounce2.h>
#include <ESP32Servo.h>

enum class Action : uint8_t { DEPLOY, STOW, STOP };

class Ramp {
 public:
  void begin();
  void command(Action action, const char* commandId, uint32_t now);
  bool update(uint32_t now);   // true when a limit switch changed

  bool stowed() const { return stowed_; }
  bool deployed() const { return deployed_; }
  const char* position() const;
  const char* motion() const { return motion_; }
  const char* fault() const { return fault_; }
  const char* result() const { return result_; }
  const char* commandId() const { return commandId_; }
  uint32_t changes() const { return changes_; }   // +1 when motion, fault or result change

 private:
  void start(const char* motion, uint16_t us, uint32_t now);
  void stop(const char* motion, const char* fault);

  Servo servo_;
  Bounce stowedIn_;
  Bounce deployedIn_;
  bool stowed_ = false;
  bool deployed_ = false;
  const char* motion_ = "IDLE";
  const char* fault_ = "NONE";
  const char* result_ = "NONE";
  char commandId_[40] = "";
  uint32_t startMs_ = 0;
  uint32_t changes_ = 0;
};
