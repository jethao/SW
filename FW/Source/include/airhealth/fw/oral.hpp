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

struct OralScoreInputs {
  double oral_score = 0.0;
  std::string score_band;
  int stable_frame_count = 0;
  int rejected_sample_count = 0;
};

struct OralResultPayload {
  SessionResultEnvelope result {};
  double oral_score = 0.0;
  std::string score_band;
  int stable_frame_count = 0;
  int rejected_sample_count = 0;
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

[[nodiscard]] OralResultPayload make_oral_result_payload(
    const SessionSnapshot& snapshot,
    const OralGateDecision& decision,
    std::string produced_at,
    BatteryState battery,
    std::string algorithm_version,
    OralScoreInputs score_inputs
);

[[nodiscard]] std::string oral_result_to_payload_json(
    const OralResultPayload& payload
);

}  // namespace airhealth::fw
