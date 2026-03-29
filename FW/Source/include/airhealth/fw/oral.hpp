#pragma once

#include "airhealth/fw/session_contract.hpp"

#include <string>

namespace airhealth::fw {

struct OralGateConfig {
  int required_stable_frames = 2;
};

struct OralSensorFrame {
  bool warmup_ready = false;
  bool sample_valid = false;
  bool motion_stable = true;
};

struct OralGateDecision {
  bool ready_to_complete = false;
  QualityGates quality_gates {};
  std::string step_name;
  std::string reason_code;
};

class OralWarmupGate {
 public:
  explicit OralWarmupGate(OralGateConfig config = {});

  void reset();

  [[nodiscard]] OralGateDecision evaluate(const OralSensorFrame& frame);

 private:
  OralGateConfig config_ {};
  int consecutive_valid_frames_ = 0;
};

[[nodiscard]] SessionEventEnvelope make_oral_gate_event(
    const SessionSnapshot& snapshot,
    const OralGateDecision& decision,
    std::string occurred_at,
    BatteryState battery,
    std::string algorithm_version
);

}  // namespace airhealth::fw
