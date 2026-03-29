#include "airhealth/fw/routing.hpp"

#include "airhealth/fw/session_contract.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::BatteryState;
using airhealth::fw::QualityGates;
using airhealth::fw::RoutingInputs;
using airhealth::fw::RoutingResolutionError;
using airhealth::fw::SessionContext;
using airhealth::fw::SessionMode;
using airhealth::fw::SessionOrchestrator;
using airhealth::fw::TerminalDisposition;
using airhealth::fw::make_session_result;
using airhealth::fw::resolve_routing_metadata;
using airhealth::fw::routing_resolution_error_to_string;
using airhealth::fw::routing_resolution_to_json;
using airhealth::fw::session_result_to_payload_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_known_hardware_and_voc_profiles_resolve_cleanly() {
  const auto resolution = resolve_routing_metadata(RoutingInputs {
      .hardware_id = "AH-BRD-B",
      .detected_voc_ppb = 210,
  });

  expect(resolution.ok(), "known hardware and VOC should resolve cleanly");
  expect(resolution.routing.hardware_profile == "hw-b",
         "hardware profile should match the known board id");
  expect(resolution.routing.voc_profile == "voc-mid",
         "VOC profile should map to the expected bucket");
}

void test_unknown_hardware_is_explicitly_flagged() {
  const auto resolution = resolve_routing_metadata(RoutingInputs {
      .hardware_id = "AH-BRD-Z",
      .detected_voc_ppb = 90,
  });

  expect(!resolution.ok(), "unknown hardware should be surfaced explicitly");
  expect(routing_resolution_error_to_string(resolution.hardware_error) ==
             "unknown_hardware_profile",
         "unknown hardware should use a stable error code");
  expect(resolution.routing.hardware_profile == "hw-unknown",
         "unknown hardware should map to the sentinel profile");
}

void test_unknown_voc_is_explicitly_flagged() {
  const auto resolution = resolve_routing_metadata(RoutingInputs {
      .hardware_id = "AH-BRD-A",
      .detected_voc_ppb = -1,
  });

  expect(!resolution.ok(), "unknown VOC should be surfaced explicitly");
  expect(routing_resolution_error_to_string(resolution.voc_error) ==
             "unknown_voc_profile",
         "unknown VOC should use a stable error code");
  expect(resolution.routing.voc_profile == "voc-unknown",
         "unknown VOC should map to the sentinel profile");
}

void test_session_payload_carries_resolved_routing_metadata() {
  SessionOrchestrator orchestrator;
  const auto resolution = resolve_routing_metadata(RoutingInputs {
      .hardware_id = "AH-BRD-A",
      .detected_voc_ppb = 320,
  });

  expect(
      orchestrator.start_session(SessionContext {
          .session_id = "routing-session-1",
          .mode = SessionMode::OralHealth,
          .routing = resolution.routing,
      }).ok(),
      "session should start for routing payload validation"
  );
  expect(
      orchestrator.finish_active_session(TerminalDisposition::Complete).ok(),
      "session should complete for result payload validation"
  );

  const auto payload = session_result_to_payload_json(make_session_result(
      orchestrator.snapshot(),
      "2026-03-29T04:40:00Z",
      "routing_ready",
      BatteryState {
          .percent = 78,
          .charging = false,
          .low_power = false,
      },
      QualityGates {
          .sample_valid = true,
          .warmup_ready = true,
          .motion_stable = true,
      },
      "alg-routing-1.0.0"
  ));

  expect(payload.find("\"hardware_profile\":\"hw-a\"") != std::string::npos,
         "session payload should carry the resolved hardware profile");
  expect(payload.find("\"voc_profile\":\"voc-high\"") != std::string::npos,
         "session payload should carry the resolved VOC profile");
}

void test_resolution_json_is_stable() {
  const auto json = routing_resolution_to_json(resolve_routing_metadata(
      RoutingInputs {
          .hardware_id = "AH-BRD-A",
          .detected_voc_ppb = 75,
      }
  ));

  expect(
      json ==
          "{\"routing\":{\"hardware_profile\":\"hw-a\","
          "\"voc_profile\":\"voc-low\"},"
          "\"hardware_error\":\"none\","
          "\"voc_error\":\"none\"}",
      "routing resolution JSON should remain deterministic"
  );
}

}  // namespace

int main() {
  try {
    test_known_hardware_and_voc_profiles_resolve_cleanly();
    test_unknown_hardware_is_explicitly_flagged();
    test_unknown_voc_is_explicitly_flagged();
    test_session_payload_carries_resolved_routing_metadata();
    test_resolution_json_is_stable();
  } catch (const std::exception& error) {
    std::cerr << "routing_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "routing_test passed\n";
  return EXIT_SUCCESS;
}
