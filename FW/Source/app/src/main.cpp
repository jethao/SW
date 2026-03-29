#include "device_info_service.hpp"

#include "airhealth/fw/led_button.hpp"
#include "airhealth/fw/pairing.hpp"

#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/kernel.h>
#include <zephyr/logging/log.h>

LOG_MODULE_REGISTER(airhealth_fw_app, LOG_LEVEL_INF);

namespace {

using airhealth::fw::ButtonHoldController;
using airhealth::fw::ButtonIntent;
using airhealth::fw::ButtonSample;
using airhealth::fw::ConsumerCapabilities;
using airhealth::fw::LedColor;
using airhealth::fw::LedController;
using airhealth::fw::RuntimeVisualState;

const gpio_dt_spec kStatusLed =
    GPIO_DT_SPEC_GET(DT_ALIAS(airhealth_status_led), gpios);
const gpio_dt_spec kActionButton =
    GPIO_DT_SPEC_GET(DT_ALIAS(airhealth_action_button), gpios);

bool configure_gpio(const gpio_dt_spec& spec, gpio_flags_t extra_flags) {
  if (!gpio_is_ready_dt(&spec)) {
    LOG_ERR("GPIO controller not ready for pin %d", spec.pin);
    return false;
  }

  const int rc = gpio_pin_configure_dt(&spec, extra_flags);
  if (rc != 0) {
    LOG_ERR("Failed to configure pin %d (%d)", spec.pin, rc);
    return false;
  }

  return true;
}

void apply_led_output(const airhealth::fw::LedOutput& output) {
  const int value = output.color == LedColor::Off ? 0 : 1;
  const int rc = gpio_pin_set_dt(&kStatusLed, value);
  if (rc != 0) {
    LOG_ERR("Failed to set status LED (%d)", rc);
  }
}

}  // namespace

int main() {
  if (!configure_gpio(kStatusLed, GPIO_OUTPUT_INACTIVE)) {
    return -1;
  }
  if (!configure_gpio(kActionButton, GPIO_INPUT)) {
    return -1;
  }

  const int bt_rc = bt_enable(nullptr);
  if (bt_rc != 0) {
    LOG_ERR("Bluetooth init failed (%d)", bt_rc);
    return bt_rc;
  }

  const auto device_info = airhealth::fw::make_device_info(
      "nrf5340dk_nrf5340_cpuapp",
      false,
      ConsumerCapabilities {
          .claim_required = true,
          .session_resume_supported = true,
          .power_state_reporting_supported = true,
      }
  );

  const int service_rc =
      airhealth::fw::app::init_device_info_service(device_info);
  if (service_rc != 0) {
    return service_rc;
  }

  const int advertise_rc = airhealth::fw::app::start_device_info_advertising();
  if (advertise_rc != 0) {
    return advertise_rc;
  }

  LedController led_controller;
  apply_led_output(led_controller.render(RuntimeVisualState::Ready));

  ButtonHoldController hold_controller;
  int held_ms = 0;

  LOG_INF("AirHealth firmware app booted");

  while (true) {
    const bool pressed = gpio_pin_get_dt(&kActionButton) > 0;
    held_ms = pressed ? held_ms + 100 : 0;

    const ButtonIntent intent = hold_controller.evaluate(ButtonSample {
        .pressed = pressed,
        .held_ms = held_ms,
        .factory_mode_active = false,
    });

    if (intent != ButtonIntent::None) {
      LOG_INF("Button intent: %s", airhealth::fw::button_intent_to_string(intent).c_str());
    }

    k_sleep(K_MSEC(100));
  }

  return 0;
}
