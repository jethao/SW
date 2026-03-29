#include "airhealth/fw/oral.hpp"

#include <sstream>
#include <stdexcept>
#include <utility>

namespace airhealth::fw {

namespace {

std::string json_string(const std::string& value) {
  std::ostringstream out;
  out << "\"";
  for (char c : value) {
    if (c == '"' || c == '\\') {
      out << '\\';
    }
    out << c;
  }
  out << "\"";
  return out.str();
}

void require_oral_completion_ready(
    const SessionSnapshot& snapshot,
    const OralGateDecision& decision,
    const OralScoreInputs& score_inputs
) {
  if (!snapshot.has_context || snapshot.context.mode != SessionMode::OralHealth) {
    throw std::invalid_argument("Oral result payload requires an oral session");
  }

  if (!decision.ready_to_complete || decision.reason_code != "sample_validated" ||
      !decision.quality_gates.sample_valid ||
      !decision.quality_gates.warmup_ready ||
      !decision.quality_gates.motion_stable) {
    throw std::invalid_argument(
        "Oral result payload requires a validated oral gate decision"
    );
  }

  if (score_inputs.stable_frame_count <= 0) {
    throw std::invalid_argument(
        "Oral result payload requires at least one stable frame"
    );
  }

  if (score_inputs.score_band.empty()) {
    throw std::invalid_argument("Oral result payload requires a score band");
  }
}

}  // namespace

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

OralResultPayload make_oral_result_payload(
    const SessionSnapshot& snapshot,
    const OralGateDecision& decision,
    std::string produced_at,
    BatteryState battery,
    std::string algorithm_version,
    OralScoreInputs score_inputs
) {
  require_oral_completion_ready(snapshot, decision, score_inputs);

  auto result = make_session_result(
      snapshot,
      std::move(produced_at),
      "oral_result_ready",
      battery,
      decision.quality_gates,
      std::move(algorithm_version)
  );

  return OralResultPayload {
      .result = std::move(result),
      .oral_score = score_inputs.oral_score,
      .score_band = std::move(score_inputs.score_band),
      .stable_frame_count = score_inputs.stable_frame_count,
      .rejected_sample_count = score_inputs.rejected_sample_count,
  };
}

std::string oral_result_to_payload_json(const OralResultPayload& payload) {
  std::ostringstream out;
  out << "{"
      << "\"session_id\":" << json_string(payload.result.session_id) << ","
      << "\"mode\":" << json_string(session_mode_to_string(payload.result.mode))
      << ","
      << "\"terminal_state\":"
      << json_string(session_state_to_string(payload.result.terminal_state))
      << ","
      << "\"produced_at\":" << json_string(payload.result.produced_at) << ","
      << "\"terminal_reason\":"
      << json_string(payload.result.terminal_reason) << ","
      << "\"battery\":{"
      << "\"percent\":" << payload.result.battery.percent << ","
      << "\"charging\":"
      << (payload.result.battery.charging ? "true" : "false") << ","
      << "\"low_power\":"
      << (payload.result.battery.low_power ? "true" : "false") << "},"
      << "\"quality_gates\":{"
      << "\"sample_valid\":"
      << (payload.result.quality_gates.sample_valid ? "true" : "false") << ","
      << "\"warmup_ready\":"
      << (payload.result.quality_gates.warmup_ready ? "true" : "false") << ","
      << "\"motion_stable\":"
      << (payload.result.quality_gates.motion_stable ? "true" : "false")
      << "},"
      << "\"algorithm_version\":"
      << json_string(payload.result.algorithm_version) << ","
      << "\"routing\":{"
      << "\"hardware_profile\":"
      << json_string(payload.result.routing.hardware_profile) << ","
      << "\"voc_profile\":"
      << json_string(payload.result.routing.voc_profile) << "},"
      << "\"oral_summary\":{"
      << "\"score\":" << payload.oral_score << ","
      << "\"score_band\":" << json_string(payload.score_band) << ","
      << "\"stable_frame_count\":" << payload.stable_frame_count << ","
      << "\"rejected_sample_count\":" << payload.rejected_sample_count
      << "}"
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
