#include "Ramp.h"

#include "config.h"

namespace {
// Every motion_ value comes from these constants, so they can be compared by pointer.
const char* const IDLE = "IDLE";
const char* const DEPLOYING = "DEPLOYING";
const char* const STOWING = "STOWING";
const char* const FAULT = "FAULT";
}  // namespace

void Ramp::begin() {
  stowedIn_.attach(STOWED_PIN, INPUT_PULLUP);
  deployedIn_.attach(DEPLOYED_PIN, INPUT_PULLUP);
  stowedIn_.interval(DEBOUNCE_MS);
  deployedIn_.interval(DEBOUNCE_MS);
  stowed_ = stowedIn_.read() == LOW;
  deployed_ = deployedIn_.read() == LOW;
  motion_ = IDLE;
  servo_.setPeriodHertz(50);   // attached only during a maneuver
}

void Ramp::command(Action action, const char* commandId, uint32_t now) {
  strlcpy(commandId_, commandId, sizeof(commandId_));
  if (action == Action::STOP) {
    stop(IDLE, "NONE");
    result_ = "STOPPED";
  } else {
    const bool deploy = action == Action::DEPLOY;
    if (stowed_ && deployed_) {
      stop(FAULT, "CONTRADICTION");
      result_ = "REJECTED_CONTRADICTION";
    } else if (deploy ? deployed_ : stowed_) {
      stop(IDLE, "NONE");
      result_ = "ALREADY_THERE";
    } else {
      start(deploy ? DEPLOYING : STOWING, deploy ? DEPLOY_US : STOW_US, now);
      result_ = "STARTED";
    }
  }
  changes_++;
}

bool Ramp::update(uint32_t now) {
  stowedIn_.update();
  deployedIn_.update();
  const bool changed = stowedIn_.changed() || deployedIn_.changed();
  stowed_ = stowedIn_.read() == LOW;
  deployed_ = deployedIn_.read() == LOW;

  if (motion_ == DEPLOYING || motion_ == STOWING) {
    if (stowed_ && deployed_) stop(FAULT, "CONTRADICTION");
    else if (motion_ == DEPLOYING ? deployed_ : stowed_) stop(IDLE, "NONE");
    else if (now - startMs_ >= MANEUVER_TIMEOUT_MS) stop(FAULT, "TIMEOUT");
  }
  return changed;
}

const char* Ramp::position() const {
  if (stowed_ && deployed_) return "UNKNOWN";
  return stowed_ ? "STOWED" : deployed_ ? "DEPLOYED" : "INTERMEDIATE";
}

void Ramp::start(const char* motion, uint16_t us, uint32_t now) {
  if (!servo_.attached()) servo_.attach(SERVO_PIN, 1000, 2000);
  servo_.writeMicroseconds(us);
  motion_ = motion;
  fault_ = "NONE";
  startMs_ = now;
  changes_++;
}

void Ramp::stop(const char* motion, const char* fault) {
  servo_.detach();   // cut the signal: 1500 us would still creep
  if (motion_ != motion || strcmp(fault_, fault) != 0) {
    motion_ = motion;
    fault_ = fault;
    changes_++;
  }
}
