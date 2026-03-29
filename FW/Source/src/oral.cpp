#include "airhealth/fw/oral.hpp"

#include <utility>

namespace airhealth::fw {

OralWarmupGate::OralWarmupGate(OralGateConfig config) : config_(config) {}

void OralWarmupGate::reset() {
  consecutive_valid_frames_ = 0;
}

OralGateDecision OralWarmupGate::evaluate(const OralSensorFrame& frame) {
  QualityGates quality_gates {
      .sample_valid = frame.sample_valid,
      .warmup_ready = frame.warmup_ready,
      .motion_stable = frame.motion_stable,
  };

  if (!frame.warmup_ready) {
    consecutive_valid_frames_ = 0;
    return OralGateDecision {
        .ready_to_complete = false,
        .quality_gates = quality_gates,
        .step_name = "oral.warmup.pending",
        .reason_code = "warmup_incomplete",
    };
  }

  if (!frame.motion_stable) {
    consecutive_valid_frames_ = 0;
    return OralGateDecision {
        .ready_to_complete = false,
        .quality_gates = quality_gates,
        .step_name = "oral.sample.rejected",
        .reason_code = "unstable_sample",
    };
  }

  if (!frame.sample_valid) {
    consecutive_valid_frames_ = 0;
    return OralGateDecision {
        .ready_to_complete = false,
        .quality_gates = quality_gates,
        .step_name = "oral.sample.rejected",
        .reason_code = "invalid_sample",
    };
  }

  ++consecutive_valid_frames_;
  if (consecutive_valid_frames_ < config_.required_stable_frames) {
    return OralGateDecision {
        .ready_to_complete = false,
        .quality_gates = quality_gates,
        .step_name = "oral.sample.validating",
        .reason_code = "validating_sample",
    };
  }

  return OralGateDecision {
      .ready_to_complete = true,
      .quality_gates = quality_gates,
      .step_name = "oral.sample.validated",
      .reason_code = "sample_validated",
  };
}

SessionEventEnvelope make_oral_gate_event(
    const SessionSnapshot& snapshot,
    const OralGateDecision& decision,
    std::string occurred_at,
    BatteryState battery,
    std::string algorithm_version
) {
  return make_session_event(
      snapshot,
      std::move(occurred_at),
      decision.step_name,
      decision.reason_code,
      battery,
      decision.quality_gates,
      std::move(algorithm_version)
  );
}

}  // namespace airhealth::fw
