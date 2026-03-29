#include "airhealth/fw/device_info.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::ConsumerCapabilities;
using airhealth::fw::DeviceInfo;
using airhealth::fw::is_protocol_major_supported;
using airhealth::fw::make_device_info;
using airhealth::fw::to_payload_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_default_device_info_shape() {
  const DeviceInfo device_info = make_device_info(
      "AH-REV-A",
      true,
      ConsumerCapabilities {
          .claim_required = true,
          .session_resume_supported = true,
          .power_state_reporting_supported = true,
      }
  );

  expect(device_info.hardware_revision == "AH-REV-A",
         "hardware revision should be preserved");
  expect(device_info.supported_modes.size() == 2,
         "device.info should advertise both consumer modes");
  expect(device_info.ota_supported, "OTA support flag should be set");
}

void test_protocol_major_compatibility() {
  const DeviceInfo device_info = make_device_info("AH-REV-A", true);

  expect(is_protocol_major_supported(device_info, 1),
         "matching major protocol version should be supported");
  expect(!is_protocol_major_supported(device_info, 2),
         "unsupported major protocol version should be detectable");
}

void test_payload_serialization_is_stable() {
  const DeviceInfo device_info = make_device_info(
      "AH-REV-B",
      false,
      ConsumerCapabilities {
          .claim_required = true,
          .session_resume_supported = true,
          .power_state_reporting_supported = false,
      }
  );

  const std::string payload = to_payload_json(device_info);

  expect(
      payload ==
          "{\"protocol_version\":\"1.0.0\",\"protocol_major\":1,"
          "\"protocol_minor\":0,\"protocol_patch\":0,"
          "\"hardware_revision\":\"AH-REV-B\","
          "\"supported_modes\":[\"oral_health\",\"fat_burning\"],"
          "\"ota_supported\":false,"
          "\"consumer_capabilities\":{\"claim_required\":true,"
          "\"session_resume_supported\":true,"
          "\"power_state_reporting_supported\":false}}",
      "payload serialization should remain deterministic for clients"
  );
}

}  // namespace

int main() {
  try {
    test_default_device_info_shape();
    test_protocol_major_compatibility();
    test_payload_serialization_is_stable();
  } catch (const std::exception& error) {
    std::cerr << "device_info_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "device_info_test passed\n";
  return EXIT_SUCCESS;
}
