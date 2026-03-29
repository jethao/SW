#pragma once

#include "airhealth/fw/session.hpp"

#include <string>

namespace airhealth::fw {

struct RoutingInputs {
  std::string hardware_id;
  int detected_voc_ppb = 0;
};

enum class RoutingResolutionError {
  None,
  UnknownHardwareProfile,
  UnknownVocProfile,
};

struct RoutingResolution {
  RoutingMetadata routing {};
  RoutingResolutionError hardware_error = RoutingResolutionError::None;
  RoutingResolutionError voc_error = RoutingResolutionError::None;

  [[nodiscard]] bool ok() const {
    return hardware_error == RoutingResolutionError::None &&
        voc_error == RoutingResolutionError::None;
  }
};

[[nodiscard]] RoutingResolution resolve_routing_metadata(
    const RoutingInputs& inputs
);

[[nodiscard]] std::string routing_resolution_error_to_string(
    RoutingResolutionError error
);

[[nodiscard]] std::string routing_resolution_to_json(
    const RoutingResolution& resolution
);

}  // namespace airhealth::fw
