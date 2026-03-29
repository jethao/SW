#include "airhealth/fw/pairing.hpp"

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

std::string to_hex(std::uint64_t value) {
  std::ostringstream out;
  out << std::hex << value;
  return out.str();
}

std::uint64_t fnv1a_64(const std::string& input) {
  std::uint64_t hash = 1469598103934665603ULL;
  for (unsigned char c : input) {
    hash ^= c;
    hash *= 1099511628211ULL;
  }
  return hash;
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

ClaimState InMemoryClaimStore::load() const {
  return state_;
}

void InMemoryClaimStore::save(const ClaimState& state) {
  state_ = state;
}

ClaimService::ClaimService(std::string device_identity, ClaimStore& store)
    : device_identity_(std::move(device_identity)), store_(store) {}

ClaimBeginResult ClaimService::begin_claim(const std::string& challenge) const {
  if (challenge.empty()) {
    return ClaimBeginResult {
        .error = ClaimError::EmptyChallenge,
    };
  }

  const ClaimState existing = store_.load();
  if (existing.claimed) {
    return ClaimBeginResult {
        .error = ClaimError::AlreadyClaimed,
    };
  }

  const std::string proof_seed = device_identity_ + "::" + challenge;
  const std::string proof =
      "claim::" + device_identity_ + "::" + to_hex(fnv1a_64(proof_seed));

  store_.save(ClaimState {
      .claimed = true,
      .device_identity = device_identity_,
      .claim_proof = proof,
  });

  return ClaimBeginResult {
      .error = ClaimError::None,
      .claim_proof =
          ClaimProof {
              .device_identity = device_identity_,
              .challenge = challenge,
              .proof = proof,
          },
  };
}

ClaimState ClaimService::load_claim_state() const {
  return store_.load();
}

std::string claim_error_to_string(ClaimError error) {
  switch (error) {
    case ClaimError::None:
      return "none";
    case ClaimError::EmptyChallenge:
      return "empty_challenge";
    case ClaimError::AlreadyClaimed:
      return "already_claimed";
  }

  throw std::invalid_argument("Unsupported claim error");
}

std::string device_info_to_payload_json(const DeviceInfo& device_info) {
  std::ostringstream out;
  out << "{"
      << "\"protocol_version\":\"" << device_info.protocol_version.to_string()
      << "\","
      << "\"protocol_major\":" << device_info.protocol_version.major << ","
      << "\"protocol_minor\":" << device_info.protocol_version.minor << ","
      << "\"protocol_patch\":" << device_info.protocol_version.patch << ","
      << "\"hardware_revision\":\"" << device_info.hardware_revision << "\","
      << "\"supported_modes\":[";

  for (std::size_t index = 0; index < device_info.supported_modes.size();
       ++index) {
    if (index > 0) {
      out << ",";
    }
    out << "\"" << mode_to_string(device_info.supported_modes[index]) << "\"";
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
