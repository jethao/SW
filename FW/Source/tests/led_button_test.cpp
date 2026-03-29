#include "airhealth/fw/led_button.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::ButtonHoldController;
using airhealth::fw::ButtonIntent;
using airhealth::fw::ButtonSample;
using airhealth::fw::LedColor;
using airhealth::fw::LedController;
using airhealth::fw::RuntimeVisualState;
using airhealth::fw::button_intent_to_string;
using airhealth::fw::led_output_to_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_factory_visual_states_use_required_colors() {
  LedController controller;

  const auto running = controller.render(RuntimeVisualState::FactoryCheckRunning);
  expect(running.color == LedColor::Orange,
         "factory check should show orange while running");

  const auto passed = controller.render(RuntimeVisualState::FactoryCheckPassed);
  expect(passed.color == LedColor::Green,
         "factory pass should show green");

  const auto failed = controller.render(RuntimeVisualState::FactoryCheckFailed);
  expect(failed.color == LedColor::Red,
         "factory fail should show red");
}

void test_consumer_visual_states_are_consistent() {
  LedController controller;

  const auto ready = controller.render(RuntimeVisualState::Ready);
  expect(ready.color == LedColor::Green && !ready.blinking,
         "ready state should be solid green");

  const auto measuring = controller.render(RuntimeVisualState::Measuring);
  expect(measuring.color == LedColor::Orange && measuring.blinking,
         "measuring state should be blinking orange");

  const auto low_power = controller.render(RuntimeVisualState::LowPower);
  expect(low_power.color == LedColor::Off,
         "low power should turn the LED off");
}

void test_factory_entry_requires_ten_second_hold() {
  ButtonHoldController controller;

  const auto pending = controller.evaluate(ButtonSample {
      .pressed = true,
      .held_ms = 9000,
      .factory_mode_active = false,
  });
  expect(pending == ButtonIntent::None,
         "entry intent should not fire before ten seconds");

  const auto entry = controller.evaluate(ButtonSample {
      .pressed = true,
      .held_ms = 10000,
      .factory_mode_active = false,
  });
  expect(entry == ButtonIntent::FactoryEntry,
         "entry intent should fire at ten seconds");

  const auto repeated = controller.evaluate(ButtonSample {
      .pressed = true,
      .held_ms = 12000,
      .factory_mode_active = false,
  });
  expect(repeated == ButtonIntent::None,
         "entry intent should not repeat until the button is released");
}

void test_factory_exit_requires_ten_second_hold() {
  ButtonHoldController controller;

  const auto exit = controller.evaluate(ButtonSample {
      .pressed = true,
      .held_ms = 10000,
      .factory_mode_active = true,
  });
  expect(exit == ButtonIntent::FactoryExit,
         "exit intent should fire at ten seconds while factory mode is active");
}

void test_button_release_rearms_long_press_detection() {
  ButtonHoldController controller;

  static_cast<void>(controller.evaluate(ButtonSample {
      .pressed = true,
      .held_ms = 10000,
      .factory_mode_active = false,
  }));
  static_cast<void>(controller.evaluate(ButtonSample {
      .pressed = false,
      .held_ms = 0,
      .factory_mode_active = false,
  }));

  const auto second_entry = controller.evaluate(ButtonSample {
      .pressed = true,
      .held_ms = 10000,
      .factory_mode_active = false,
  });
  expect(second_entry == ButtonIntent::FactoryEntry,
         "releasing the button should rearm long-press detection");
}

void test_serialization_is_stable() {
  LedController controller;
  const auto json = led_output_to_json(
      controller.render(RuntimeVisualState::FactoryCheckPassed)
  );
  expect(json == "{\"color\":\"green\",\"blinking\":false,\"blink_period_ms\":0}",
         "LED output JSON should remain deterministic");
  expect(button_intent_to_string(ButtonIntent::FactoryExit) == "factory_exit",
         "button intent strings should remain stable");
}

}  // namespace

int main() {
  try {
    test_factory_visual_states_use_required_colors();
    test_consumer_visual_states_are_consistent();
    test_factory_entry_requires_ten_second_hold();
    test_factory_exit_requires_ten_second_hold();
    test_button_release_rearms_long_press_detection();
    test_serialization_is_stable();
  } catch (const std::exception& error) {
    std::cerr << "led_button_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "led_button_test passed\n";
  return EXIT_SUCCESS;
}
