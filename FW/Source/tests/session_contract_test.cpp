#include "airhealth/fw/session_contract.hpp"

#include "airhealth/fw/session.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::BatteryState;
using airhealth::fw::QualityGates;
using airhealth::fw::RoutingMetadata;
using airhealth::fw::SessionContext;
using airhealth::fw::SessionMode;
using airhealth::fw::SessionOrchestrator;
using airhealth::fw::TerminalDisposition;
using airhealth::fw::make_session_event;
using airhealth::fw::make_session_result;
using airhealth::fw::session_event_to_payload_json;
using airhealth::fw::session_result_to_payload_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

SessionContext make_context(std::string session_id, SessionMode mode) {
  return SessionContext {
      .session_id = std::move(session_id),
      .mode = mode,
      .routing =
          RoutingMetadata {
              .hardware_profile = "hw-b",
              .voc_profile = "voc-mid",
          },
  };
}

void test_session_event_payload_is_deterministic() {
  SessionOrchestrator orchestrator;
  expect(
      orchestrator.start_session(
          make_context("session-evt-1", SessionMode::OralHealth)
      ).ok(),
      "session should start before emitting events"
  );

  const auto event = make_session_event(
      orchestrator.snapshot(),
      "2026-03-28T19:30:00Z",
      "session.start.accepted",
      "none",
      BatteryState {
          .percent = 87,
          .charging = false,
          .low_power = false,
      },
      QualityGates {
          .sample_valid = false,
          .warmup_ready = true,
          .motion_stable = true,
      },
      "alg-1.2.0"
  );

  const auto payload = session_event_to_payload_json(event);
  expect(
      payload ==
          "{\"session_id\":\"session-evt-1\",\"state\":\"active\","
          "\"occurred_at\":\"2026-03-28T19:30:00Z\","
          "\"step_name\":\"session.start.accepted\","
          "\"reason_code\":\"none\","
          "\"battery\":{\"percent\":87,\"charging\":false,\"low_power\":false},"
          "\"quality_gates\":{\"sample_valid\":false,\"warmup_ready\":true,"
          "\"motion_stable\":true},"
          "\"algorithm_version\":\"alg-1.2.0\","
          "\"routing\":{\"hardware_profile\":\"hw-b\","
          "\"voc_profile\":\"voc-mid\"}}",
      "session.event payload should stay deterministic and machine-readable"
  );
}

void test_session_result_payload_carries_terminal_metadata() {
  SessionOrchestrator orchestrator;
  expect(
      orchestrator.start_session(
          make_context("session-rst-1", SessionMode::FatBurning)
      ).ok(),
      "session should start before emitting a result"
  );
  expect(
      orchestrator.finish_active_session(TerminalDisposition::Complete).ok(),
      "terminal completion should succeed before result generation"
  );

  const auto result = make_session_result(
      orchestrator.snapshot(),
      "2026-03-28T19:31:00Z",
      "session_complete",
      BatteryState {
          .percent = 81,
          .charging = false,
          .low_power = false,
      },
      QualityGates {
          .sample_valid = true,
          .warmup_ready = true,
          .motion_stable = true,
      },
      "alg-1.2.0"
  );

  const auto payload = session_result_to_payload_json(result);
  expect(
      payload ==
          "{\"session_id\":\"session-rst-1\",\"mode\":\"fat_burning\","
          "\"terminal_state\":\"complete\","
          "\"produced_at\":\"2026-03-28T19:31:00Z\","
          "\"terminal_reason\":\"session_complete\","
          "\"battery\":{\"percent\":81,\"charging\":false,\"low_power\":false},"
          "\"quality_gates\":{\"sample_valid\":true,\"warmup_ready\":true,"
          "\"motion_stable\":true},"
          "\"algorithm_version\":\"alg-1.2.0\","
          "\"routing\":{\"hardware_profile\":\"hw-b\","
          "\"voc_profile\":\"voc-mid\"}}",
      "session.result payload should carry deterministic terminal metadata"
  );
}

void test_session_contract_requires_context() {
  SessionOrchestrator orchestrator;
  bool threw = false;
  try {
    static_cast<void>(make_session_event(
        orchestrator.snapshot(),
        "2026-03-28T19:32:00Z",
        "session.invalid",
        "invalid_transition",
        {},
        {},
        "alg-1.2.0"
    ));
  } catch (const std::invalid_argument&) {
    threw = true;
  }

  expect(threw, "session envelopes should reject snapshots without context");
}

}  // namespace

int main() {
  try {
    test_session_event_payload_is_deterministic();
    test_session_result_payload_carries_terminal_metadata();
    test_session_contract_requires_context();
  } catch (const std::exception& error) {
    std::cerr << "session_contract_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "session_contract_test passed\n";
  return EXIT_SUCCESS;
}
