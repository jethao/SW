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

struct FactoryProvisioningState {
  bool hardware_check_completed = false;
  bool hardware_check_passed = false;
  bool provisioning_locked = false;
};

struct FactoryCheckDecision {
  bool executed = false;
  bool passed = false;
  bool lockout_active = false;
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

class FactoryProvisioningStore {
 public:
  virtual ~FactoryProvisioningStore() = default;

  [[nodiscard]] virtual FactoryProvisioningState load() const = 0;
  virtual void save(const FactoryProvisioningState& state) = 0;
};

class InMemoryFactoryProvisioningStore final : public FactoryProvisioningStore {
 public:
  [[nodiscard]] FactoryProvisioningState load() const override;
  void save(const FactoryProvisioningState& state) override;

 private:
  FactoryProvisioningState state_ {};
};

class FileFactoryProvisioningStore final : public FactoryProvisioningStore {
 public:
  explicit FileFactoryProvisioningStore(std::string storage_path);

  [[nodiscard]] FactoryProvisioningState load() const override;
  void save(const FactoryProvisioningState& state) override;

  [[nodiscard]] const std::string& storage_path() const;

 private:
  std::string storage_path_;
};

class FactoryProvisioningController {
 public:
  explicit FactoryProvisioningController(FactoryProvisioningStore& store);

  [[nodiscard]] FactoryCheckDecision run_hardware_check(
      bool authorization_granted,
      bool hardware_check_passed
  );

  [[nodiscard]] FactoryEntryDecision evaluate_entry(
      ButtonIntent intent,
      bool authorization_granted
  );

  [[nodiscard]] FactoryProvisioningState load_state() const;

 private:
  FactoryProvisioningStore& store_;
  FactoryModeController mode_controller_ {};
};

[[nodiscard]] std::string factory_access_error_to_string(
    FactoryAccessError error
);

}  // namespace airhealth::fw
