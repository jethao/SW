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

struct ClaimProof {
  std::string device_identity;
  std::string challenge;
  std::string proof;
};

enum class ClaimError {
  None,
  EmptyChallenge,
  AlreadyClaimed,
};

struct ClaimState {
  bool claimed = false;
  std::string device_identity;
  std::string claim_proof;
};

struct ClaimBeginResult {
  ClaimError error = ClaimError::None;
  ClaimProof claim_proof {};

  [[nodiscard]] bool ok() const {
    return error == ClaimError::None;
  }
};

class ClaimStore {
 public:
  virtual ~ClaimStore() = default;

  [[nodiscard]] virtual ClaimState load() const = 0;
  virtual void save(const ClaimState& state) = 0;
};

class InMemoryClaimStore final : public ClaimStore {
 public:
  [[nodiscard]] ClaimState load() const override;
  void save(const ClaimState& state) override;

 private:
  ClaimState state_ {};
};

class ClaimService {
 public:
  ClaimService(std::string device_identity, ClaimStore& store);

  [[nodiscard]] ClaimBeginResult begin_claim(
      const std::string& challenge
  ) const;

  [[nodiscard]] ClaimState load_claim_state() const;

 private:
  std::string device_identity_;
  ClaimStore& store_;
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

[[nodiscard]] std::string claim_error_to_string(ClaimError error);

[[nodiscard]] std::string device_info_to_payload_json(
    const DeviceInfo& device_info
);

}  // namespace airhealth::fw
