#pragma once

#include "airhealth/fw/pairing.hpp"

#include <string>

namespace airhealth::fw::app {

int init_device_info_service(const DeviceInfo& device_info);
int start_device_info_advertising();
const std::string& device_info_payload();

}  // namespace airhealth::fw::app
