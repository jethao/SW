#pragma once

#include "airhealth/fw/session_contract.hpp"

#include <string>

namespace airhealth::fw {

struct FatReading {
  bool sample_valid = false;
  double reading_percent = 0.0;
};

struct FatLoopDecision {
  bool baseline_locked = false;
  bool loop_active = false;
  double baseline_percent = 0.0;
  double current_delta_percent = 0.0;
  double best_delta_percent = 0.0;
  int valid_reading_count = 0;
  std::string step_name;
  std::string reason_code;
};

struct FatSummaryPayload {
  SessionResultEnvelope result {};
  double final_delta_percent = 0.0;
  double best_delta_percent = 0.0;
  int reading_count = 0;
};

class FatReadingLoop {
 public:
  void reset();

  [[nodiscard]] FatLoopDecision evaluate(const FatReading& reading);

 private:
  bool has_baseline_ = false;
  double baseline_percent_ = 0.0;
  bool has_best_delta_ = false;
  double best_delta_percent_ = 0.0;
  int valid_reading_count_ = 0;
};

[[nodiscard]] SessionEventEnvelope make_fat_loop_event(
    const SessionSnapshot& snapshot,
    const FatLoopDecision& decision,
    std::string occurred_at,
    BatteryState battery,
    std::string algorithm_version
);

[[nodiscard]] std::string fat_loop_decision_to_json(
    const FatLoopDecision& decision
);

[[nodiscard]] FatSummaryPayload make_fat_summary_payload(
    const SessionSnapshot& snapshot,
    const FatLoopDecision& decision,
    std::string produced_at,
    BatteryState battery,
    std::string algorithm_version
);

[[nodiscard]] std::string fat_summary_to_payload_json(
    const FatSummaryPayload& payload
);

}  // namespace airhealth::fw
