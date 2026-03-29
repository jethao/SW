#include "airhealth/fw/low_power.hpp"

#include <sstream>

namespace airhealth::fw {

namespace {

const char* bool_to_json(bool value) {
  return value ? "true" : "false";
}

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

LowPowerController::LowPowerController(LowPowerConfig config) : config_(config) {}

void LowPowerController::reset() {
  low_power_ = false;
  entry_counter_seconds_ = 0;
  exit_counter_seconds_ = 0;
}

LowPowerDecision LowPowerController::evaluate(const LowPowerSample& sample) {
  if (!low_power_) {
    exit_counter_seconds_ = 0;
    if (sample.delta_percent < config_.entry_delta_threshold_percent) {
      ++entry_counter_seconds_;
    } else {
      entry_counter_seconds_ = 0;
    }

    if (entry_counter_seconds_ >= config_.entry_debounce_seconds) {
      low_power_ = true;
      exit_counter_seconds_ = 0;
      return LowPowerDecision {
          .low_power = true,
          .state_changed = true,
          .transition = "enter_low_power",
          .reason_code = "entry_threshold_sustained",
          .entry_counter_seconds = entry_counter_seconds_,
          .exit_counter_seconds = exit_counter_seconds_,
      };
    }

    return LowPowerDecision {
        .low_power = false,
        .state_changed = false,
        .transition = "stay_awake",
        .reason_code = entry_counter_seconds_ == 0 ? "entry_counter_reset"
                                                   : "entry_threshold_pending",
        .entry_counter_seconds = entry_counter_seconds_,
        .exit_counter_seconds = exit_counter_seconds_,
    };
  }

  entry_counter_seconds_ = 0;
  if (sample.user_or_app_action) {
    low_power_ = false;
    exit_counter_seconds_ = 0;
    return LowPowerDecision {
        .low_power = false,
        .state_changed = true,
        .transition = "exit_low_power",
        .reason_code = "wake_action_detected",
        .entry_counter_seconds = entry_counter_seconds_,
        .exit_counter_seconds = exit_counter_seconds_,
    };
  }

  if (sample.delta_percent > config_.exit_delta_threshold_percent) {
    ++exit_counter_seconds_;
  } else {
    exit_counter_seconds_ = 0;
  }

  if (exit_counter_seconds_ >= config_.exit_debounce_seconds) {
    low_power_ = false;
    exit_counter_seconds_ = 0;
    return LowPowerDecision {
        .low_power = false,
        .state_changed = true,
        .transition = "exit_low_power",
        .reason_code = "exit_threshold_sustained",
        .entry_counter_seconds = entry_counter_seconds_,
        .exit_counter_seconds = exit_counter_seconds_,
    };
  }

  return LowPowerDecision {
      .low_power = true,
      .state_changed = false,
      .transition = "stay_low_power",
      .reason_code = "exit_threshold_pending",
      .entry_counter_seconds = entry_counter_seconds_,
      .exit_counter_seconds = exit_counter_seconds_,
  };
}

std::string low_power_decision_to_json(const LowPowerDecision& decision) {
  std::ostringstream out;
  out << "{"
      << "\"low_power\":" << bool_to_json(decision.low_power) << ","
      << "\"state_changed\":" << bool_to_json(decision.state_changed) << ","
      << "\"transition\":" << json_string(decision.transition) << ","
      << "\"reason_code\":" << json_string(decision.reason_code) << ","
      << "\"entry_counter_seconds\":" << decision.entry_counter_seconds << ","
      << "\"exit_counter_seconds\":" << decision.exit_counter_seconds
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
