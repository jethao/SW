#include "airhealth/fw/device_info.hpp"

#include <sstream>
#include <stdexcept>

namespace airhealth::fw {

namespace {

const char* mode_to_string(ConsumerMode mode) {
  switch (mode) {
    case ConsumerMode::OralHealth:
      return "oral_health";
    case ConsumerMode::FatBurning:
      return "fat_burning";
  }

  throw std::invalid_argument("Unsupported consumer mode");
}

std::string bool_to_json(bool value) {
  return value ? "true" : "false";
}

}  // namespace

std::string ProtocolVersion::to_string() const {
  return std::to_string(major) + "." + std::to_string(minor) + "." +
      std::to_string(patch);
}

DeviceInfo make_device_info(
    std::string hardware_revision,
    bool ota_supported,
    ConsumerCapabilities capabilities
) {
  DeviceInfo device_info {};
  device_info.hardware_revision = std::move(hardware_revision);
  device_info.supported_modes = {
      ConsumerMode::OralHealth,
      ConsumerMode::FatBurning,
  };
  device_info.ota_supported = ota_supported;
  device_info.consumer_capabilities = capabilities;
  return device_info;
}

bool is_protocol_major_supported(
    const DeviceInfo& device_info,
    int requested_major
) {
  return device_info.protocol_version.major == requested_major;
}

std::string to_payload_json(const DeviceInfo& device_info) {
  std::ostringstream out;
  out << "{"
      << "\"protocol_version\":\"" << device_info.protocol_version.to_string()
      << "\","
      << "\"protocol_major\":" << device_info.protocol_version.major << ","
      << "\"protocol_minor\":" << device_info.protocol_version.minor << ","
      << "\"protocol_patch\":" << device_info.protocol_version.patch << ","
      << "\"hardware_revision\":\"" << device_info.hardware_revision << "\","
      << "\"supported_modes\":[";

  for (std::size_t i = 0; i < device_info.supported_modes.size(); ++i) {
    if (i > 0) {
      out << ",";
    }
    out << "\"" << mode_to_string(device_info.supported_modes[i]) << "\"";
  }

  out << "],"
      << "\"ota_supported\":" << bool_to_json(device_info.ota_supported)
      << ","
      << "\"consumer_capabilities\":{"
      << "\"claim_required\":"
      << bool_to_json(device_info.consumer_capabilities.claim_required) << ","
      << "\"session_resume_supported\":"
      << bool_to_json(
             device_info.consumer_capabilities.session_resume_supported
         )
      << ","
      << "\"power_state_reporting_supported\":"
      << bool_to_json(
             device_info.consumer_capabilities.power_state_reporting_supported
         )
      << "}"
      << "}";

  return out.str();
}

}  // namespace airhealth::fw
