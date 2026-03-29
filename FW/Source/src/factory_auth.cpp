#include "airhealth/fw/factory_auth.hpp"

#include <fstream>
#include <stdexcept>
#include <utility>

namespace airhealth::fw {

FactoryAuthorizationService::FactoryAuthorizationService(
    std::string expected_token
) : expected_token_(std::move(expected_token)) {}

FactoryAuthResult FactoryAuthorizationService::authorize(
    const std::string& token
) {
  if (token.empty()) {
    return FactoryAuthResult {
        .error = FactoryAccessError::InvalidToken,
        .authorized = false,
    };
  }

  if (token != expected_token_) {
    return FactoryAuthResult {
        .error = FactoryAccessError::Unauthorized,
        .authorized = false,
    };
  }

  authorized_ = true;
  return FactoryAuthResult {
      .error = FactoryAccessError::None,
      .authorized = true,
  };
}

bool FactoryAuthorizationService::can_run(FactoryCommand command) const {
  if (command == FactoryCommand::None) {
    return true;
  }
  return authorized_;
}

FactoryModeController::FactoryModeController() = default;

void FactoryModeController::reset() {
  active_ = false;
}

FactoryEntryDecision FactoryModeController::evaluate(
    ButtonIntent intent,
    bool authorization_granted
) {
  if (intent == ButtonIntent::FactoryEntry) {
    if (!authorization_granted) {
      return FactoryEntryDecision {
          .factory_mode_active = active_,
          .state_changed = false,
          .reason_code = "authorization_required",
      };
    }

    if (!active_) {
      active_ = true;
      return FactoryEntryDecision {
          .factory_mode_active = true,
          .state_changed = true,
          .reason_code = "factory_mode_entered",
      };
    }
  }

  if (intent == ButtonIntent::FactoryExit && active_) {
    active_ = false;
    return FactoryEntryDecision {
        .factory_mode_active = false,
        .state_changed = true,
        .reason_code = "factory_mode_exited",
    };
  }

  return FactoryEntryDecision {
      .factory_mode_active = active_,
      .state_changed = false,
      .reason_code = active_ ? "factory_mode_active" : "factory_mode_idle",
  };
}

FactoryProvisioningState InMemoryFactoryProvisioningStore::load() const {
  return state_;
}

void InMemoryFactoryProvisioningStore::save(
    const FactoryProvisioningState& state
) {
  state_ = state;
}

FileFactoryProvisioningStore::FileFactoryProvisioningStore(
    std::string storage_path
) : storage_path_(std::move(storage_path)) {}

FactoryProvisioningState FileFactoryProvisioningStore::load() const {
  std::ifstream input(storage_path_);
  if (!input.is_open()) {
    return {};
  }

  FactoryProvisioningState state {};
  std::string line;
  if (std::getline(input, line)) {
    state.hardware_check_completed = line == "1";
  }
  if (std::getline(input, line)) {
    state.hardware_check_passed = line == "1";
  }
  if (std::getline(input, line)) {
    state.provisioning_locked = line == "1";
  }
  return state;
}

void FileFactoryProvisioningStore::save(
    const FactoryProvisioningState& state
) {
  std::ofstream output(storage_path_, std::ios::trunc);
  if (!output.is_open()) {
    throw std::runtime_error(
        "Unable to open factory provisioning store for write"
    );
  }

  output << (state.hardware_check_completed ? "1" : "0") << "\n"
         << (state.hardware_check_passed ? "1" : "0") << "\n"
         << (state.provisioning_locked ? "1" : "0") << "\n";
}

const std::string& FileFactoryProvisioningStore::storage_path() const {
  return storage_path_;
}

FactoryProvisioningController::FactoryProvisioningController(
    FactoryProvisioningStore& store
) : store_(store) {}

FactoryCheckDecision FactoryProvisioningController::run_hardware_check(
    bool authorization_granted,
    bool hardware_check_passed
) {
  if (!authorization_granted) {
    return FactoryCheckDecision {
        .executed = false,
        .passed = false,
        .lockout_active = false,
        .reason_code = "authorization_required",
    };
  }

  const FactoryProvisioningState existing = store_.load();
  if (existing.provisioning_locked) {
    return FactoryCheckDecision {
        .executed = false,
        .passed = existing.hardware_check_passed,
        .lockout_active = true,
        .reason_code = "factory_mode_locked_out",
    };
  }

  if (existing.hardware_check_completed) {
    return FactoryCheckDecision {
        .executed = false,
        .passed = existing.hardware_check_passed,
        .lockout_active = existing.provisioning_locked,
        .reason_code = "hardware_check_already_completed",
    };
  }

  FactoryProvisioningState updated {};
  updated.hardware_check_completed = true;
  updated.hardware_check_passed = hardware_check_passed;
  updated.provisioning_locked = true;
  store_.save(updated);
  mode_controller_.reset();

  return FactoryCheckDecision {
      .executed = true,
      .passed = hardware_check_passed,
      .lockout_active = true,
      .reason_code = hardware_check_passed
          ? "hardware_check_passed"
          : "hardware_check_failed",
  };
}

FactoryEntryDecision FactoryProvisioningController::evaluate_entry(
    ButtonIntent intent,
    bool authorization_granted
) {
  const FactoryProvisioningState state = store_.load();
  if (state.provisioning_locked && intent == ButtonIntent::FactoryEntry) {
    return FactoryEntryDecision {
        .factory_mode_active = false,
        .state_changed = false,
        .reason_code = "factory_mode_locked_out",
    };
  }

  return mode_controller_.evaluate(intent, authorization_granted);
}

FactoryProvisioningState FactoryProvisioningController::load_state() const {
  return store_.load();
}

std::string factory_access_error_to_string(FactoryAccessError error) {
  switch (error) {
    case FactoryAccessError::None:
      return "none";
    case FactoryAccessError::Unauthorized:
      return "unauthorized";
    case FactoryAccessError::InvalidToken:
      return "invalid_token";
  }

  throw std::invalid_argument("Unsupported factory access error");
}

}  // namespace airhealth::fw
