#include "airhealth/fw/low_power.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::LowPowerController;
using airhealth::fw::LowPowerDecision;
using airhealth::fw::LowPowerSample;
using airhealth::fw::low_power_decision_to_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void expect_reason(
    const LowPowerDecision& decision,
    const std::string& expected_reason
) {
  expect(decision.reason_code == expected_reason,
         "unexpected low-power reason code: " + decision.reason_code);
}

void test_entry_requires_three_consecutive_seconds_below_threshold() {
  LowPowerController controller;

  const auto first = controller.evaluate(LowPowerSample {
      .delta_percent = 0.7,
  });
  expect(!first.low_power, "controller should remain awake after first second");
  expect_reason(first, "entry_threshold_pending");

  const auto second = controller.evaluate(LowPowerSample {
      .delta_percent = 0.5,
  });
  expect(!second.low_power,
         "controller should remain awake after second second below threshold");
  expect_reason(second, "entry_threshold_pending");

  const auto third = controller.evaluate(LowPowerSample {
      .delta_percent = 0.2,
  });
  expect(third.low_power,
         "controller should enter low power after three consecutive seconds");
  expect(third.state_changed,
         "third second below threshold should change the low-power state");
  expect_reason(third, "entry_threshold_sustained");
}

void test_entry_counter_resets_when_threshold_is_broken() {
  LowPowerController controller;

  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.4,
  }));
  const auto reset = controller.evaluate(LowPowerSample {
      .delta_percent = 1.3,
  });
  expect(!reset.low_power,
         "threshold break should keep the controller awake");
  expect(reset.entry_counter_seconds == 0,
         "entry counter should reset when the threshold is broken");
  expect_reason(reset, "entry_counter_reset");
}

void test_exit_requires_two_consecutive_seconds_above_threshold() {
  LowPowerController controller;

  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.3,
  }));
  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.4,
  }));
  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.5,
  }));

  const auto first_exit = controller.evaluate(LowPowerSample {
      .delta_percent = 2.4,
  });
  expect(first_exit.low_power,
         "single second above exit threshold should keep low power active");
  expect_reason(first_exit, "exit_threshold_pending");

  const auto second_exit = controller.evaluate(LowPowerSample {
      .delta_percent = 2.6,
  });
  expect(!second_exit.low_power,
         "two consecutive seconds above exit threshold should wake the device");
  expect(second_exit.state_changed,
         "exit threshold satisfaction should change the low-power state");
  expect_reason(second_exit, "exit_threshold_sustained");
}

void test_user_action_exits_immediately() {
  LowPowerController controller;

  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.2,
  }));
  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.2,
  }));
  static_cast<void>(controller.evaluate(LowPowerSample {
      .delta_percent = 0.2,
  }));

  const auto exit = controller.evaluate(LowPowerSample {
      .delta_percent = 0.1,
      .user_or_app_action = true,
  });
  expect(!exit.low_power,
         "user or app action should exit low power immediately");
  expect_reason(exit, "wake_action_detected");
}

void test_low_power_decision_json_is_stable() {
  const auto json = low_power_decision_to_json(LowPowerDecision {
      .low_power = false,
      .state_changed = true,
      .transition = "exit_low_power",
      .reason_code = "wake_action_detected",
      .entry_counter_seconds = 0,
      .exit_counter_seconds = 0,
  });

  expect(
      json ==
          "{\"low_power\":false,\"state_changed\":true,"
          "\"transition\":\"exit_low_power\","
          "\"reason_code\":\"wake_action_detected\","
          "\"entry_counter_seconds\":0,\"exit_counter_seconds\":0}",
      "low-power decision JSON should remain deterministic"
  );
}

}  // namespace

int main() {
  try {
    test_entry_requires_three_consecutive_seconds_below_threshold();
    test_entry_counter_resets_when_threshold_is_broken();
    test_exit_requires_two_consecutive_seconds_above_threshold();
    test_user_action_exits_immediately();
    test_low_power_decision_json_is_stable();
  } catch (const std::exception& error) {
    std::cerr << "low_power_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "low_power_test passed\n";
  return EXIT_SUCCESS;
}
