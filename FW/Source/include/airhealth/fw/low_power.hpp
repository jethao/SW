#pragma once

#include "airhealth/fw/session.hpp"

#include <string>

namespace airhealth::fw {

struct LowPowerConfig {
  double entry_delta_threshold_percent = 1.0;
  int entry_debounce_seconds = 3;
  double exit_delta_threshold_percent = 2.0;
  int exit_debounce_seconds = 2;
};

struct LowPowerSample {
  double delta_percent = 0.0;
  bool user_or_app_action = false;
};

struct LowPowerDecision {
  bool low_power = false;
  bool state_changed = false;
  std::string transition;
  std::string reason_code;
  int entry_counter_seconds = 0;
  int exit_counter_seconds = 0;
};

struct LowPowerSessionInput {
  LowPowerSample sample {};
  SessionSnapshot session {};
  bool transition_in_flight = false;
};

struct LowPowerSessionDecision {
  LowPowerDecision power {};
  SessionState resume_state = SessionState::Ready;
  bool transition_inhibited = false;
  bool failure_suppressed = false;
};

class LowPowerController {
 public:
  explicit LowPowerController(LowPowerConfig config = {});

  void reset();

  [[nodiscard]] LowPowerDecision evaluate(const LowPowerSample& sample);

 private:
  LowPowerConfig config_ {};
  bool low_power_ = false;
  int entry_counter_seconds_ = 0;
  int exit_counter_seconds_ = 0;
};

class LowPowerSessionGate {
 public:
  explicit LowPowerSessionGate(LowPowerConfig config = {});

  void reset();

  [[nodiscard]] LowPowerSessionDecision evaluate(
      const LowPowerSessionInput& input
  );

 private:
  LowPowerController controller_ {};
  bool low_power_active_ = false;
  SessionState latched_resume_state_ = SessionState::Ready;
};

[[nodiscard]] std::string low_power_decision_to_json(
    const LowPowerDecision& decision
);

}  // namespace airhealth::fw
