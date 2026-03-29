#include "airhealth/fw/routing.hpp"

#include <sstream>
#include <stdexcept>

namespace airhealth::fw {

namespace {

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

std::string resolve_hardware_profile(const std::string& hardware_id) {
  if (hardware_id == "AH-BRD-A") {
    return "hw-a";
  }
  if (hardware_id == "AH-BRD-B") {
    return "hw-b";
  }
  return "hw-unknown";
}

RoutingResolutionError resolve_hardware_error(const std::string& hardware_id) {
  return resolve_hardware_profile(hardware_id) == "hw-unknown"
      ? RoutingResolutionError::UnknownHardwareProfile
      : RoutingResolutionError::None;
}

std::string resolve_voc_profile(int detected_voc_ppb) {
  if (detected_voc_ppb < 0) {
    return "voc-unknown";
  }
  if (detected_voc_ppb < 150) {
    return "voc-low";
  }
  if (detected_voc_ppb < 300) {
    return "voc-mid";
  }
  return "voc-high";
}

RoutingResolutionError resolve_voc_error(int detected_voc_ppb) {
  return detected_voc_ppb < 0 ? RoutingResolutionError::UnknownVocProfile
                              : RoutingResolutionError::None;
}

}  // namespace

RoutingResolution resolve_routing_metadata(const RoutingInputs& inputs) {
  return RoutingResolution {
      .routing =
          RoutingMetadata {
              .hardware_profile = resolve_hardware_profile(inputs.hardware_id),
              .voc_profile = resolve_voc_profile(inputs.detected_voc_ppb),
          },
      .hardware_error = resolve_hardware_error(inputs.hardware_id),
      .voc_error = resolve_voc_error(inputs.detected_voc_ppb),
  };
}

std::string routing_resolution_error_to_string(RoutingResolutionError error) {
  switch (error) {
    case RoutingResolutionError::None:
      return "none";
    case RoutingResolutionError::UnknownHardwareProfile:
      return "unknown_hardware_profile";
    case RoutingResolutionError::UnknownVocProfile:
      return "unknown_voc_profile";
  }

  throw std::invalid_argument("Unsupported routing resolution error");
}

std::string routing_resolution_to_json(const RoutingResolution& resolution) {
  std::ostringstream out;
  out << "{"
      << "\"routing\":{\"hardware_profile\":"
      << json_string(resolution.routing.hardware_profile) << ","
      << "\"voc_profile\":" << json_string(resolution.routing.voc_profile)
      << "},"
      << "\"hardware_error\":"
      << json_string(routing_resolution_error_to_string(
             resolution.hardware_error
         )) << ","
      << "\"voc_error\":"
      << json_string(routing_resolution_error_to_string(resolution.voc_error))
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
