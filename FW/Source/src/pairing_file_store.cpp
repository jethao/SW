#include "airhealth/fw/pairing.hpp"

#include <fstream>
#include <stdexcept>
#include <utility>

namespace airhealth::fw {

FileClaimStore::FileClaimStore(std::string storage_path)
    : storage_path_(std::move(storage_path)) {}

ClaimState FileClaimStore::load() const {
  std::ifstream input(storage_path_);
  if (!input.is_open()) {
    return {};
  }

  ClaimState state {};
  std::string claimed_line;
  if (!std::getline(input, claimed_line)) {
    return {};
  }

  state.claimed = claimed_line == "1";
  if (!std::getline(input, state.device_identity)) {
    state.device_identity.clear();
  }
  if (!std::getline(input, state.claim_proof)) {
    state.claim_proof.clear();
  }

  return state;
}

void FileClaimStore::save(const ClaimState& state) {
  std::ofstream output(storage_path_, std::ios::trunc);
  if (!output.is_open()) {
    throw std::runtime_error("Unable to open claim store for write");
  }

  output << (state.claimed ? "1" : "0") << "\n"
         << state.device_identity << "\n"
         << state.claim_proof << "\n";
}

const std::string& FileClaimStore::storage_path() const {
  return storage_path_;
}

}  // namespace airhealth::fw
