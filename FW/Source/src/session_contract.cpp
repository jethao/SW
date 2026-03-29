#include "airhealth/fw/session_contract.hpp"

#include <sstream>
#include <stdexcept>

namespace airhealth::fw {

namespace {

std::string bool_to_json(bool value) {
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

void require_session_context(const SessionSnapshot& snapshot) {
  if (!snapshot.has_context || snapshot.context.session_id.empty()) {
    throw std::invalid_argument("Session snapshot must include active context");
  }
}

bool is_terminal_state(SessionState state) {
  switch (state) {
    case SessionState::Complete:
    case SessionState::Canceled:
    case SessionState::Failed:
    case SessionState::Interrupted:
    case SessionState::Reconciled:
      return true;
    case SessionState::Ready:
    case SessionState::Active:
      return false;
  }

  throw std::invalid_argument("Unsupported session state");
}

void require_terminal_session(const SessionSnapshot& snapshot) {
  require_session_context(snapshot);
  if (!is_terminal_state(snapshot.state)) {
    throw std::invalid_argument("Session result requires terminal state");
  }
}

}  // namespace

SessionEventEnvelope make_session_event(
    const SessionSnapshot& snapshot,
    std::string occurred_at,
    std::string step_name,
    std::string reason_code,
    BatteryState battery,
    QualityGates quality_gates,
    std::string algorithm_version
) {
  require_session_context(snapshot);

  return SessionEventEnvelope {
      .session_id = snapshot.context.session_id,
      .state = snapshot.state,
      .occurred_at = std::move(occurred_at),
      .step_name = std::move(step_name),
      .reason_code = std::move(reason_code),
      .battery = battery,
      .quality_gates = quality_gates,
      .algorithm_version = std::move(algorithm_version),
      .routing = snapshot.context.routing,
  };
}

SessionResultEnvelope make_session_result(
    const SessionSnapshot& snapshot,
    std::string produced_at,
    std::string terminal_reason,
    BatteryState battery,
    QualityGates quality_gates,
    std::string algorithm_version
) {
  require_terminal_session(snapshot);

  return SessionResultEnvelope {
      .session_id = snapshot.context.session_id,
      .mode = snapshot.context.mode,
      .terminal_state = snapshot.state,
      .produced_at = std::move(produced_at),
      .terminal_reason = std::move(terminal_reason),
      .battery = battery,
      .quality_gates = quality_gates,
      .algorithm_version = std::move(algorithm_version),
      .routing = snapshot.context.routing,
  };
}

std::string session_mode_to_string(SessionMode mode) {
  switch (mode) {
    case SessionMode::OralHealth:
      return "oral_health";
    case SessionMode::FatBurning:
      return "fat_burning";
  }

  throw std::invalid_argument("Unsupported session mode");
}

std::string session_event_to_payload_json(const SessionEventEnvelope& event) {
  std::ostringstream out;
  out << "{"
      << "\"session_id\":" << json_string(event.session_id) << ","
      << "\"state\":" << json_string(session_state_to_string(event.state))
      << ","
      << "\"occurred_at\":" << json_string(event.occurred_at) << ","
      << "\"step_name\":" << json_string(event.step_name) << ","
      << "\"reason_code\":" << json_string(event.reason_code) << ","
      << "\"battery\":{"
      << "\"percent\":" << event.battery.percent << ","
      << "\"charging\":" << bool_to_json(event.battery.charging) << ","
      << "\"low_power\":" << bool_to_json(event.battery.low_power)
      << "},"
      << "\"quality_gates\":{"
      << "\"sample_valid\":" << bool_to_json(event.quality_gates.sample_valid)
      << ","
      << "\"warmup_ready\":" << bool_to_json(event.quality_gates.warmup_ready)
      << ","
      << "\"motion_stable\":"
      << bool_to_json(event.quality_gates.motion_stable) << "},"
      << "\"algorithm_version\":"
      << json_string(event.algorithm_version) << ","
      << "\"routing\":{"
      << "\"hardware_profile\":"
      << json_string(event.routing.hardware_profile) << ","
      << "\"voc_profile\":" << json_string(event.routing.voc_profile)
      << "}"
      << "}";
  return out.str();
}

std::string session_result_to_payload_json(const SessionResultEnvelope& result) {
  std::ostringstream out;
  out << "{"
      << "\"session_id\":" << json_string(result.session_id) << ","
      << "\"mode\":" << json_string(session_mode_to_string(result.mode)) << ","
      << "\"terminal_state\":"
      << json_string(session_state_to_string(result.terminal_state)) << ","
      << "\"produced_at\":" << json_string(result.produced_at) << ","
      << "\"terminal_reason\":"
      << json_string(result.terminal_reason) << ","
      << "\"battery\":{"
      << "\"percent\":" << result.battery.percent << ","
      << "\"charging\":" << bool_to_json(result.battery.charging) << ","
      << "\"low_power\":" << bool_to_json(result.battery.low_power)
      << "},"
      << "\"quality_gates\":{"
      << "\"sample_valid\":"
      << bool_to_json(result.quality_gates.sample_valid) << ","
      << "\"warmup_ready\":"
      << bool_to_json(result.quality_gates.warmup_ready) << ","
      << "\"motion_stable\":"
      << bool_to_json(result.quality_gates.motion_stable) << "},"
      << "\"algorithm_version\":"
      << json_string(result.algorithm_version) << ","
      << "\"routing\":{"
      << "\"hardware_profile\":"
      << json_string(result.routing.hardware_profile) << ","
      << "\"voc_profile\":" << json_string(result.routing.voc_profile)
      << "}"
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
