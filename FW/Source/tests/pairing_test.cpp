#include "airhealth/fw/pairing.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::ConsumerCapabilities;
using airhealth::fw::DeviceInfo;
using airhealth::fw::device_info_to_payload_json;
using airhealth::fw::is_protocol_major_supported;
using airhealth::fw::make_device_info;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_device_info_shape() {
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
         "hardware revision should remain stable");
  expect(device_info.supported_modes.size() == 2,
         "device.info should advertise the supported consumer modes");
  expect(device_info.ota_supported, "OTA support flag should be exposed");
}

void test_protocol_major_detection() {
  const DeviceInfo device_info = make_device_info("AH-REV-A", true);

  expect(is_protocol_major_supported(device_info, 1),
         "matching major protocol version should be accepted");
  expect(!is_protocol_major_supported(device_info, 2),
         "unsupported major protocol version should be detectable");
}

void test_device_info_payload_is_stable() {
  const DeviceInfo device_info = make_device_info(
      "AH-REV-B",
      false,
      ConsumerCapabilities {
          .claim_required = true,
          .session_resume_supported = true,
          .power_state_reporting_supported = false,
      }
  );

  const std::string payload = device_info_to_payload_json(device_info);

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
      "device.info payload should remain deterministic for the client"
  );
}

}  // namespace

int main() {
  try {
    test_device_info_shape();
    test_protocol_major_detection();
    test_device_info_payload_is_stable();
  } catch (const std::exception& error) {
    std::cerr << "pairing_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "pairing_test passed\n";
  return EXIT_SUCCESS;
}
