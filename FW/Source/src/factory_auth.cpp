#include "airhealth/fw/factory_auth.hpp"

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
