#include "airhealth/fw/fat.hpp"

#include "airhealth/fw/session.hpp"
#include "airhealth/fw/session_contract.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::BatteryState;
using airhealth::fw::FatReading;
using airhealth::fw::FatReadingLoop;
using airhealth::fw::SessionContext;
using airhealth::fw::SessionMode;
using airhealth::fw::SessionOrchestrator;
using airhealth::fw::TerminalDisposition;
using airhealth::fw::fat_loop_decision_to_json;
using airhealth::fw::make_fat_loop_event;
using airhealth::fw::make_fat_summary_payload;
using airhealth::fw::fat_summary_to_payload_json;
using airhealth::fw::session_event_to_payload_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

SessionContext make_context() {
  return SessionContext {
      .session_id = "fat-session-1",
      .mode = SessionMode::FatBurning,
      .routing =
          {
              .hardware_profile = "hw-fat",
              .voc_profile = "voc-low",
          },
  };
}

void test_first_valid_reading_locks_baseline() {
  FatReadingLoop loop;

  const auto decision = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 18.4,
  });

  expect(decision.baseline_locked,
         "first valid fat reading should lock the baseline");
  expect(decision.loop_active,
         "baseline lock should keep the repeated-reading loop active");
  expect(decision.baseline_percent == 18.4,
         "baseline should match the first valid reading");
  expect(decision.current_delta_percent == 0.0,
         "baseline lock should start at a zero delta");
  expect(decision.reason_code == "baseline_locked",
         "baseline lock should emit a stable baseline reason");
}

void test_invalid_readings_do_not_advance_loop() {
  FatReadingLoop loop;

  const auto invalid = loop.evaluate(FatReading {
      .sample_valid = false,
      .reading_percent = 17.0,
  });
  expect(!invalid.baseline_locked,
         "invalid reading should not lock the baseline");
  expect(!invalid.loop_active,
         "invalid reading without baseline should not activate the loop");
  expect(invalid.valid_reading_count == 0,
         "invalid reading should not increment the valid count");
  expect(invalid.reason_code == "invalid_sample",
         "invalid fat reading should emit a stable invalid reason");
}

void test_negative_only_sessions_keep_best_delta_correct() {
  FatReadingLoop loop;

  static_cast<void>(loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 20.0,
  }));
  const auto first = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 18.0,
  });
  expect(first.current_delta_percent == -2.0,
         "first post-baseline delta should be negative two");
  expect(first.best_delta_percent == -2.0,
         "best delta should track the first negative-only reading");

  const auto second = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 19.0,
  });
  expect(second.current_delta_percent == -1.0,
         "second post-baseline delta should be negative one");
  expect(second.best_delta_percent == -1.0,
         "best delta should improve to the least negative reading");
}

void test_positive_deltas_update_best_delta() {
  FatReadingLoop loop;

  static_cast<void>(loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 20.0,
  }));
  static_cast<void>(loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 19.0,
  }));
  const auto improved = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 22.5,
  });

  expect(improved.current_delta_percent == 2.5,
         "positive reading should measure against the original baseline");
  expect(improved.best_delta_percent == 2.5,
         "best delta should update when a better reading appears");
  expect(improved.valid_reading_count == 3,
         "valid count should include baseline and repeated readings");
}

void test_fat_loop_events_stay_in_active_session_envelope() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context()).ok(),
         "fat event test requires an active session");

  FatReadingLoop loop;
  static_cast<void>(loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 20.0,
  }));
  const auto decision = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 18.5,
  });

  const auto event = make_fat_loop_event(
      orchestrator.snapshot(),
      decision,
      "2026-03-29T03:40:00Z",
      BatteryState {
          .percent = 68,
          .charging = false,
          .low_power = false,
      },
      "alg-fat-1.0.0"
  );

  const auto payload = session_event_to_payload_json(event);
  expect(
      payload ==
          "{\"session_id\":\"fat-session-1\",\"state\":\"active\","
          "\"occurred_at\":\"2026-03-29T03:40:00Z\","
          "\"step_name\":\"fat.reading.updated\","
          "\"reason_code\":\"delta_updated\","
          "\"battery\":{\"percent\":68,\"charging\":false,\"low_power\":false},"
          "\"quality_gates\":{\"sample_valid\":true,\"warmup_ready\":true,"
          "\"motion_stable\":true},"
          "\"algorithm_version\":\"alg-fat-1.0.0\","
          "\"routing\":{\"hardware_profile\":\"hw-fat\","
          "\"voc_profile\":\"voc-low\"}}",
      "fat loop updates should stay in the shared active session envelope"
  );
}

void test_fat_loop_json_is_deterministic() {
  const auto json = fat_loop_decision_to_json(airhealth::fw::FatLoopDecision {
      .baseline_locked = true,
      .loop_active = true,
      .baseline_percent = 20.0,
      .current_delta_percent = -1.0,
      .best_delta_percent = -1.0,
      .valid_reading_count = 2,
      .step_name = "fat.reading.updated",
      .reason_code = "delta_updated",
  });

  expect(
      json ==
          "{\"baseline_locked\":true,\"loop_active\":true,"
          "\"baseline_percent\":20,\"current_delta_percent\":-1,"
          "\"best_delta_percent\":-1,\"valid_reading_count\":2,"
          "\"step_name\":\"fat.reading.updated\","
          "\"reason_code\":\"delta_updated\"}",
      "fat loop JSON should remain deterministic"
  );
}

void test_fat_summary_requires_completed_finish_path() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context()).ok(),
         "fat summary guard test requires an active session");

  FatReadingLoop loop;
  static_cast<void>(loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 20.0,
  }));
  const auto decision = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 18.0,
  });

  expect(orchestrator.finish_active_session(TerminalDisposition::Canceled).ok(),
         "fat summary guard test requires a canceled terminal state");

  bool threw = false;
  try {
    static_cast<void>(make_fat_summary_payload(
        orchestrator.snapshot(),
        decision,
        "2026-03-29T04:00:00Z",
        BatteryState {
            .percent = 67,
            .charging = false,
            .low_power = false,
        },
        "alg-fat-1.1.0"
    ));
  } catch (const std::invalid_argument&) {
    threw = true;
  }

  expect(threw,
         "fat summary payload must not emit for canceled sessions");
}

void test_fat_summary_emits_after_valid_finish() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context()).ok(),
         "fat summary payload test requires an active session");

  FatReadingLoop loop;
  static_cast<void>(loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 20.0,
  }));
  const auto decision = loop.evaluate(FatReading {
      .sample_valid = true,
      .reading_percent = 22.0,
  });

  expect(orchestrator.finish_active_session(TerminalDisposition::Complete).ok(),
         "fat summary payload test requires a completed session");

  const auto payload = make_fat_summary_payload(
      orchestrator.snapshot(),
      decision,
      "2026-03-29T04:01:00Z",
      BatteryState {
          .percent = 66,
          .charging = false,
          .low_power = false,
      },
      "alg-fat-1.1.0"
  );

  const auto json = fat_summary_to_payload_json(payload);
  expect(
      json ==
          "{\"session_id\":\"fat-session-1\",\"mode\":\"fat_burning\","
          "\"terminal_state\":\"complete\","
          "\"produced_at\":\"2026-03-29T04:01:00Z\","
          "\"terminal_reason\":\"fat_summary_ready\","
          "\"battery\":{\"percent\":66,\"charging\":false,\"low_power\":false},"
          "\"quality_gates\":{\"sample_valid\":true,\"warmup_ready\":true,"
          "\"motion_stable\":true},"
          "\"algorithm_version\":\"alg-fat-1.1.0\","
          "\"routing\":{\"hardware_profile\":\"hw-fat\","
          "\"voc_profile\":\"voc-low\"},"
          "\"fat_summary\":{\"final_delta_percent\":2,"
          "\"best_delta_percent\":2,\"reading_count\":2}}",
      "fat summary payload should include final and best delta values"
  );
}

}  // namespace

int main() {
  try {
    test_first_valid_reading_locks_baseline();
    test_invalid_readings_do_not_advance_loop();
    test_negative_only_sessions_keep_best_delta_correct();
    test_positive_deltas_update_best_delta();
    test_fat_loop_events_stay_in_active_session_envelope();
    test_fat_loop_json_is_deterministic();
    test_fat_summary_requires_completed_finish_path();
    test_fat_summary_emits_after_valid_finish();
  } catch (const std::exception& error) {
    std::cerr << "fat_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "fat_test passed\n";
  return EXIT_SUCCESS;
}
