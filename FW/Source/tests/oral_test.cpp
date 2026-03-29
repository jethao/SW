#include "airhealth/fw/oral.hpp"

#include "airhealth/fw/session.hpp"
#include "airhealth/fw/session_contract.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::BatteryState;
using airhealth::fw::OralGateConfig;
using airhealth::fw::OralScoreInputs;
using airhealth::fw::OralSensorFrame;
using airhealth::fw::OralWarmupGate;
using airhealth::fw::SessionContext;
using airhealth::fw::TerminalDisposition;
using airhealth::fw::SessionMode;
using airhealth::fw::SessionOrchestrator;
using airhealth::fw::make_oral_gate_event;
using airhealth::fw::make_oral_result_payload;
using airhealth::fw::oral_result_to_payload_json;
using airhealth::fw::session_event_to_payload_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

SessionContext make_context() {
  return SessionContext {
      .session_id = "oral-session-1",
      .mode = SessionMode::OralHealth,
      .routing =
          {
              .hardware_profile = "hw-oral",
              .voc_profile = "voc-none",
          },
  };
}

void test_warmup_blocks_completion() {
  OralWarmupGate gate(OralGateConfig {
      .required_stable_frames = 2,
  });

  const auto decision = gate.evaluate(OralSensorFrame {
      .warmup_ready = false,
      .sample_valid = true,
      .motion_stable = true,
  });

  expect(!decision.ready_to_complete,
         "oral gate should not allow completion before warmup is ready");
  expect(decision.reason_code == "warmup_incomplete",
         "warmup gate should emit a stable warmup failure reason");
}

void test_invalid_and_unstable_samples_emit_stable_reasons() {
  OralWarmupGate gate;

  const auto invalid = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = false,
      .motion_stable = true,
  });
  expect(!invalid.ready_to_complete,
         "invalid samples must not allow oral completion");
  expect(invalid.reason_code == "invalid_sample",
         "invalid samples should emit a stable invalid reason");

  const auto unstable = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = true,
      .motion_stable = false,
  });
  expect(!unstable.ready_to_complete,
         "unstable samples must not allow oral completion");
  expect(unstable.reason_code == "unstable_sample",
         "unstable samples should emit a stable unstable reason");
}

void test_consecutive_valid_samples_enable_completion() {
  OralWarmupGate gate(OralGateConfig {
      .required_stable_frames = 2,
  });

  const auto first = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = true,
      .motion_stable = true,
  });
  expect(!first.ready_to_complete,
         "first valid frame should still be validating");
  expect(first.reason_code == "validating_sample",
         "first valid frame should remain in validating state");

  const auto second = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = true,
      .motion_stable = true,
  });
  expect(second.ready_to_complete,
         "required stable valid frames should allow completion");
  expect(second.reason_code == "sample_validated",
         "validated oral samples should emit the stable completion-ready code");
}

void test_oral_gate_events_use_shared_session_envelope() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context()).ok(),
         "oral event test requires an active session");

  OralWarmupGate gate;
  const auto decision = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = false,
      .motion_stable = true,
  });
  const auto event = make_oral_gate_event(
      orchestrator.snapshot(),
      decision,
      "2026-03-28T19:50:00Z",
      BatteryState {
          .percent = 76,
          .charging = false,
          .low_power = false,
      },
      "alg-oral-1.0.0"
  );

  const auto payload = session_event_to_payload_json(event);
  expect(
      payload ==
          "{\"session_id\":\"oral-session-1\",\"state\":\"active\","
          "\"occurred_at\":\"2026-03-28T19:50:00Z\","
          "\"step_name\":\"oral.sample.rejected\","
          "\"reason_code\":\"invalid_sample\","
          "\"battery\":{\"percent\":76,\"charging\":false,\"low_power\":false},"
          "\"quality_gates\":{\"sample_valid\":false,\"warmup_ready\":true,"
          "\"motion_stable\":true},"
          "\"algorithm_version\":\"alg-oral-1.0.0\","
          "\"routing\":{\"hardware_profile\":\"hw-oral\","
          "\"voc_profile\":\"voc-none\"}}",
      "oral gating should emit the shared deterministic session.event payload"
  );
}

void test_oral_result_requires_validated_completion() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context()).ok(),
         "oral result guard test requires an active session");

  OralWarmupGate gate;
  const auto decision = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = false,
      .motion_stable = true,
  });

  bool threw = false;
  try {
    static_cast<void>(make_oral_result_payload(
        orchestrator.snapshot(),
        decision,
        "2026-03-29T03:00:00Z",
        BatteryState {
            .percent = 75,
            .charging = false,
            .low_power = false,
        },
        "alg-oral-1.1.0",
        OralScoreInputs {
            .oral_score = 82.5,
            .score_band = "good",
            .stable_frame_count = 2,
            .rejected_sample_count = 1,
        }
    ));
  } catch (const std::invalid_argument&) {
    threw = true;
  }

  expect(threw,
         "oral result payload must not emit before a validated completion path");
}

void test_oral_result_payload_emits_after_valid_completion() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context()).ok(),
         "oral result payload test requires an active session");

  OralWarmupGate gate(OralGateConfig {
      .required_stable_frames = 2,
  });
  static_cast<void>(gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = true,
      .motion_stable = true,
  }));
  const auto decision = gate.evaluate(OralSensorFrame {
      .warmup_ready = true,
      .sample_valid = true,
      .motion_stable = true,
  });

  expect(decision.ready_to_complete,
         "oral result payload test requires a validated gate decision");
  expect(orchestrator.finish_active_session(TerminalDisposition::Complete).ok(),
         "oral result payload test requires a completed oral session");

  const auto payload = make_oral_result_payload(
      orchestrator.snapshot(),
      decision,
      "2026-03-29T03:01:00Z",
      BatteryState {
          .percent = 74,
          .charging = false,
          .low_power = false,
      },
      "alg-oral-1.1.0",
      OralScoreInputs {
          .oral_score = 82.5,
          .score_band = "good",
          .stable_frame_count = 2,
          .rejected_sample_count = 1,
      }
  );

  const auto json = oral_result_to_payload_json(payload);
  expect(
      json ==
          "{\"session_id\":\"oral-session-1\",\"mode\":\"oral_health\","
          "\"terminal_state\":\"complete\","
          "\"produced_at\":\"2026-03-29T03:01:00Z\","
          "\"terminal_reason\":\"oral_result_ready\","
          "\"battery\":{\"percent\":74,\"charging\":false,\"low_power\":false},"
          "\"quality_gates\":{\"sample_valid\":true,\"warmup_ready\":true,"
          "\"motion_stable\":true},"
          "\"algorithm_version\":\"alg-oral-1.1.0\","
          "\"routing\":{\"hardware_profile\":\"hw-oral\","
          "\"voc_profile\":\"voc-none\"},"
          "\"oral_summary\":{\"score\":82.5,\"score_band\":\"good\","
          "\"stable_frame_count\":2,\"rejected_sample_count\":1}}",
      "oral result payload should include session metadata plus oral summary"
  );
}

}  // namespace

int main() {
  try {
    test_warmup_blocks_completion();
    test_invalid_and_unstable_samples_emit_stable_reasons();
    test_consecutive_valid_samples_enable_completion();
    test_oral_gate_events_use_shared_session_envelope();
    test_oral_result_requires_validated_completion();
    test_oral_result_payload_emits_after_valid_completion();
  } catch (const std::exception& error) {
    std::cerr << "oral_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "oral_test passed\n";
  return EXIT_SUCCESS;
}
