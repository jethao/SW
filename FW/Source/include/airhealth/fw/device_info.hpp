#pragma once

#include <string>
#include <vector>

namespace airhealth::fw {

struct ProtocolVersion {
  int major = 1;
  int minor = 0;
  int patch = 0;

  [[nodiscard]] std::string to_string() const;
};

enum class ConsumerMode {
  OralHealth,
  FatBurning,
};

struct ConsumerCapabilities {
  bool claim_required = true;
  bool session_resume_supported = true;
  bool power_state_reporting_supported = true;
};

struct DeviceInfo {
  ProtocolVersion protocol_version {};
  std::string hardware_revision;
  std::vector<ConsumerMode> supported_modes;
  bool ota_supported = false;
  ConsumerCapabilities consumer_capabilities {};
};

[[nodiscard]] DeviceInfo make_device_info(
    std::string hardware_revision,
    bool ota_supported,
    ConsumerCapabilities capabilities = {}
);

[[nodiscard]] bool is_protocol_major_supported(
    const DeviceInfo& device_info,
    int requested_major
);

[[nodiscard]] std::string to_payload_json(const DeviceInfo& device_info);

}  // namespace airhealth::fw
