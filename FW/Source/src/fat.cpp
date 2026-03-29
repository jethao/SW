#include "airhealth/fw/fat.hpp"

#include <sstream>
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

void require_fat_summary_ready(
    const SessionSnapshot& snapshot,
    const FatLoopDecision& decision
) {
  if (!snapshot.has_context || snapshot.context.mode != SessionMode::FatBurning) {
    throw std::invalid_argument("Fat summary payload requires a fat session");
  }

  if (snapshot.state != SessionState::Complete) {
    throw std::invalid_argument(
        "Fat summary payload requires a completed finish path"
    );
  }

  if (!decision.baseline_locked || decision.valid_reading_count <= 0) {
    throw std::invalid_argument(
        "Fat summary payload requires a locked baseline and valid readings"
    );
  }
}

}  // namespace

void FatReadingLoop::reset() {
  has_baseline_ = false;
  baseline_percent_ = 0.0;
  has_best_delta_ = false;
  best_delta_percent_ = 0.0;
  valid_reading_count_ = 0;
}

FatLoopDecision FatReadingLoop::evaluate(const FatReading& reading) {
  if (!reading.sample_valid) {
    return FatLoopDecision {
        .baseline_locked = has_baseline_,
        .loop_active = has_baseline_,
        .baseline_percent = baseline_percent_,
        .current_delta_percent = has_baseline_ ? 0.0 : 0.0,
        .best_delta_percent = has_best_delta_ ? best_delta_percent_ : 0.0,
        .valid_reading_count = valid_reading_count_,
        .step_name = "fat.sample.rejected",
        .reason_code = "invalid_sample",
    };
  }

  if (!has_baseline_) {
    has_baseline_ = true;
    baseline_percent_ = reading.reading_percent;
    valid_reading_count_ = 1;
    return FatLoopDecision {
        .baseline_locked = true,
        .loop_active = true,
        .baseline_percent = baseline_percent_,
        .current_delta_percent = 0.0,
        .best_delta_percent = 0.0,
        .valid_reading_count = valid_reading_count_,
        .step_name = "fat.baseline.locked",
        .reason_code = "baseline_locked",
    };
  }

  ++valid_reading_count_;
  const double current_delta = reading.reading_percent - baseline_percent_;
  if (!has_best_delta_ || current_delta > best_delta_percent_) {
    best_delta_percent_ = current_delta;
    has_best_delta_ = true;
  }

  return FatLoopDecision {
      .baseline_locked = true,
      .loop_active = true,
      .baseline_percent = baseline_percent_,
      .current_delta_percent = current_delta,
      .best_delta_percent = best_delta_percent_,
      .valid_reading_count = valid_reading_count_,
      .step_name = "fat.reading.updated",
      .reason_code = "delta_updated",
  };
}

SessionEventEnvelope make_fat_loop_event(
    const SessionSnapshot& snapshot,
    const FatLoopDecision& decision,
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
      QualityGates {
          .sample_valid = decision.reason_code != "invalid_sample",
          .warmup_ready = true,
          .motion_stable = true,
      },
      std::move(algorithm_version)
  );
}

std::string fat_loop_decision_to_json(const FatLoopDecision& decision) {
  std::ostringstream out;
  out << "{"
      << "\"baseline_locked\":"
      << (decision.baseline_locked ? "true" : "false") << ","
      << "\"loop_active\":" << (decision.loop_active ? "true" : "false")
      << ","
      << "\"baseline_percent\":" << decision.baseline_percent << ","
      << "\"current_delta_percent\":" << decision.current_delta_percent << ","
      << "\"best_delta_percent\":" << decision.best_delta_percent << ","
      << "\"valid_reading_count\":" << decision.valid_reading_count << ","
      << "\"step_name\":" << json_string(decision.step_name) << ","
      << "\"reason_code\":" << json_string(decision.reason_code)
      << "}";
  return out.str();
}

FatSummaryPayload make_fat_summary_payload(
    const SessionSnapshot& snapshot,
    const FatLoopDecision& decision,
    std::string produced_at,
    BatteryState battery,
    std::string algorithm_version
) {
  require_fat_summary_ready(snapshot, decision);

  auto result = make_session_result(
      snapshot,
      std::move(produced_at),
      "fat_summary_ready",
      battery,
      QualityGates {
          .sample_valid = true,
          .warmup_ready = true,
          .motion_stable = true,
      },
      std::move(algorithm_version)
  );

  return FatSummaryPayload {
      .result = std::move(result),
      .final_delta_percent = decision.current_delta_percent,
      .best_delta_percent = decision.best_delta_percent,
      .reading_count = decision.valid_reading_count,
  };
}

std::string fat_summary_to_payload_json(const FatSummaryPayload& payload) {
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
      << "\"fat_summary\":{"
      << "\"final_delta_percent\":" << payload.final_delta_percent << ","
      << "\"best_delta_percent\":" << payload.best_delta_percent << ","
      << "\"reading_count\":" << payload.reading_count
      << "}"
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
