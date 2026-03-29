#pragma once

#include <string>

namespace airhealth::fw {

enum class LedColor {
  Off,
  Green,
  Orange,
  Red,
};

enum class RuntimeVisualState {
  Ready,
  Measuring,
  LowPower,
  FactoryCheckRunning,
  FactoryCheckPassed,
  FactoryCheckFailed,
};

struct LedOutput {
  LedColor color = LedColor::Off;
  bool blinking = false;
  int blink_period_ms = 0;
};

struct ButtonHoldConfig {
  int factory_entry_hold_ms = 10000;
  int factory_exit_hold_ms = 10000;
};

enum class ButtonIntent {
  None,
  FactoryEntry,
  FactoryExit,
};

struct ButtonSample {
  bool pressed = false;
  int held_ms = 0;
  bool factory_mode_active = false;
};

class LedController {
 public:
  [[nodiscard]] LedOutput render(RuntimeVisualState state) const;
};

class ButtonHoldController {
 public:
  explicit ButtonHoldController(ButtonHoldConfig config = {});

  void reset();

  [[nodiscard]] ButtonIntent evaluate(const ButtonSample& sample);

 private:
  ButtonHoldConfig config_ {};
  ButtonIntent last_emitted_intent_ = ButtonIntent::None;
};

[[nodiscard]] std::string led_color_to_string(LedColor color);
[[nodiscard]] std::string button_intent_to_string(ButtonIntent intent);
[[nodiscard]] std::string led_output_to_json(const LedOutput& output);

}  // namespace airhealth::fw
