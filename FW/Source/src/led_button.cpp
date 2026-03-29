#include "airhealth/fw/led_button.hpp"

#include <sstream>
#include <stdexcept>

namespace airhealth::fw {

namespace {

const char* bool_to_json(bool value) {
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

}  // namespace

LedOutput LedController::render(RuntimeVisualState state) const {
  switch (state) {
    case RuntimeVisualState::Ready:
      return LedOutput {
          .color = LedColor::Green,
          .blinking = false,
          .blink_period_ms = 0,
      };
    case RuntimeVisualState::Measuring:
      return LedOutput {
          .color = LedColor::Orange,
          .blinking = true,
          .blink_period_ms = 800,
      };
    case RuntimeVisualState::LowPower:
      return LedOutput {
          .color = LedColor::Off,
          .blinking = false,
          .blink_period_ms = 0,
      };
    case RuntimeVisualState::FactoryCheckRunning:
      return LedOutput {
          .color = LedColor::Orange,
          .blinking = false,
          .blink_period_ms = 0,
      };
    case RuntimeVisualState::FactoryCheckPassed:
      return LedOutput {
          .color = LedColor::Green,
          .blinking = false,
          .blink_period_ms = 0,
      };
    case RuntimeVisualState::FactoryCheckFailed:
      return LedOutput {
          .color = LedColor::Red,
          .blinking = false,
          .blink_period_ms = 0,
      };
  }

  throw std::invalid_argument("Unsupported runtime visual state");
}

ButtonHoldController::ButtonHoldController(ButtonHoldConfig config)
    : config_(config) {}

void ButtonHoldController::reset() {
  last_emitted_intent_ = ButtonIntent::None;
}

ButtonIntent ButtonHoldController::evaluate(const ButtonSample& sample) {
  if (!sample.pressed) {
    last_emitted_intent_ = ButtonIntent::None;
    return ButtonIntent::None;
  }

  const auto candidate_intent = sample.factory_mode_active
      ? ButtonIntent::FactoryExit
      : ButtonIntent::FactoryEntry;
  const int required_hold_ms = sample.factory_mode_active
      ? config_.factory_exit_hold_ms
      : config_.factory_entry_hold_ms;

  if (sample.held_ms < required_hold_ms) {
    return ButtonIntent::None;
  }

  if (last_emitted_intent_ == candidate_intent) {
    return ButtonIntent::None;
  }

  last_emitted_intent_ = candidate_intent;
  return candidate_intent;
}

std::string led_color_to_string(LedColor color) {
  switch (color) {
    case LedColor::Off:
      return "off";
    case LedColor::Green:
      return "green";
    case LedColor::Orange:
      return "orange";
    case LedColor::Red:
      return "red";
  }

  throw std::invalid_argument("Unsupported LED color");
}

std::string button_intent_to_string(ButtonIntent intent) {
  switch (intent) {
    case ButtonIntent::None:
      return "none";
    case ButtonIntent::FactoryEntry:
      return "factory_entry";
    case ButtonIntent::FactoryExit:
      return "factory_exit";
  }

  throw std::invalid_argument("Unsupported button intent");
}

std::string led_output_to_json(const LedOutput& output) {
  std::ostringstream out;
  out << "{"
      << "\"color\":" << json_string(led_color_to_string(output.color)) << ","
      << "\"blinking\":" << bool_to_json(output.blinking) << ","
      << "\"blink_period_ms\":" << output.blink_period_ms
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
