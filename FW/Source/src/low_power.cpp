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

LowPowerSessionGate::LowPowerSessionGate(LowPowerConfig config)
    : controller_(config) {}

void LowPowerSessionGate::reset() {
  controller_.reset();
  low_power_active_ = false;
  latched_resume_state_ = SessionState::Ready;
}

LowPowerSessionDecision LowPowerSessionGate::evaluate(
    const LowPowerSessionInput& input
) {
  const auto requested_resume_state =
      input.session.state == SessionState::Active ? SessionState::Active
                                                  : SessionState::Ready;

  if (input.transition_in_flight) {
    const bool was_low_power = low_power_active_;
    reset();
    return LowPowerSessionDecision {
        .power =
            LowPowerDecision {
                .low_power = false,
                .state_changed = was_low_power,
                .transition = was_low_power ? "exit_low_power" : "stay_awake",
                .reason_code = "session_transition_inhibited",
                .entry_counter_seconds = 0,
                .exit_counter_seconds = 0,
            },
        .resume_state = requested_resume_state,
        .transition_inhibited = true,
        .failure_suppressed = true,
    };
  }

  const auto power = controller_.evaluate(input.sample);
  if (power.state_changed && power.low_power) {
    low_power_active_ = true;
    latched_resume_state_ = requested_resume_state;
  } else if (!power.low_power) {
    low_power_active_ = false;
  }

  const auto resume_state =
      power.low_power ? latched_resume_state_ : requested_resume_state;

  return LowPowerSessionDecision {
      .power = power,
      .resume_state = resume_state,
      .transition_inhibited = false,
      .failure_suppressed = low_power_active_ ||
          (power.state_changed && power.transition == "exit_low_power"),
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
