#pragma once

#include "airhealth/fw/session.hpp"

#include <string>

namespace airhealth::fw {

struct BatteryState {
  int percent = 100;
  bool charging = false;
  bool low_power = false;
};

struct QualityGates {
  bool sample_valid = false;
  bool warmup_ready = false;
  bool motion_stable = true;
};

struct SessionEventEnvelope {
  std::string session_id;
  SessionState state = SessionState::Ready;
  std::string occurred_at;
  std::string step_name;
  std::string reason_code;
  BatteryState battery {};
  QualityGates quality_gates {};
  std::string algorithm_version;
  RoutingMetadata routing {};
};

struct SessionResultEnvelope {
  std::string session_id;
  SessionMode mode = SessionMode::OralHealth;
  SessionState terminal_state = SessionState::Ready;
  std::string produced_at;
  std::string terminal_reason;
  BatteryState battery {};
  QualityGates quality_gates {};
  std::string algorithm_version;
  RoutingMetadata routing {};
};

[[nodiscard]] SessionEventEnvelope make_session_event(
    const SessionSnapshot& snapshot,
    std::string occurred_at,
    std::string step_name,
    std::string reason_code,
    BatteryState battery,
    QualityGates quality_gates,
    std::string algorithm_version
);

[[nodiscard]] SessionResultEnvelope make_session_result(
    const SessionSnapshot& snapshot,
    std::string produced_at,
    std::string terminal_reason,
    BatteryState battery,
    QualityGates quality_gates,
    std::string algorithm_version
);

[[nodiscard]] std::string session_mode_to_string(SessionMode mode);
[[nodiscard]] std::string session_event_to_payload_json(
    const SessionEventEnvelope& event
);
[[nodiscard]] std::string session_result_to_payload_json(
    const SessionResultEnvelope& result
);

}  // namespace airhealth::fw
