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

}  // namespace airhealth::fw
