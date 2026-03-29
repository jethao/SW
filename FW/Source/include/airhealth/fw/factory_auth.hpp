#pragma once

#include "airhealth/fw/led_button.hpp"

#include <string>

namespace airhealth::fw {

enum class FactoryCommand {
  None,
  ReadDiagnostics,
  BeginHardwareCheck,
};

enum class FactoryAccessError {
  None,
  Unauthorized,
  InvalidToken,
};

struct FactoryAuthResult {
  FactoryAccessError error = FactoryAccessError::None;
  bool authorized = false;

  [[nodiscard]] bool ok() const {
    return error == FactoryAccessError::None;
  }
};

struct FactoryEntryDecision {
  bool factory_mode_active = false;
  bool state_changed = false;
  std::string reason_code;
};

class FactoryAuthorizationService {
 public:
  explicit FactoryAuthorizationService(std::string expected_token);

  [[nodiscard]] FactoryAuthResult authorize(const std::string& token);
  [[nodiscard]] bool can_run(FactoryCommand command) const;

 private:
  std::string expected_token_;
  bool authorized_ = false;
};

class FactoryModeController {
 public:
  FactoryModeController();

  void reset();

  [[nodiscard]] FactoryEntryDecision evaluate(
      ButtonIntent intent,
      bool authorization_granted
  );

 private:
  bool active_ = false;
};

[[nodiscard]] std::string factory_access_error_to_string(
    FactoryAccessError error
);

}  // namespace airhealth::fw
