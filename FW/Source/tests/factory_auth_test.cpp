#include "airhealth/fw/factory_auth.hpp"

#include "airhealth/fw/led_button.hpp"

#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::ButtonIntent;
using airhealth::fw::FactoryAccessError;
using airhealth::fw::FactoryAuthorizationService;
using airhealth::fw::FactoryCommand;
using airhealth::fw::FactoryModeController;
using airhealth::fw::FactoryProvisioningController;
using airhealth::fw::FileFactoryProvisioningStore;
using airhealth::fw::InMemoryFactoryProvisioningStore;
using airhealth::fw::factory_access_error_to_string;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_factory_commands_are_blocked_before_authorization() {
  FactoryAuthorizationService auth("factory-token");
  expect(!auth.can_run(FactoryCommand::BeginHardwareCheck),
         "factory command should be blocked before authorization");
}

void test_authorization_requires_matching_token() {
  FactoryAuthorizationService auth("factory-token");
  const auto invalid = auth.authorize("wrong-token");
  expect(!invalid.ok(), "wrong token should not authorize");
  expect(factory_access_error_to_string(invalid.error) == "unauthorized",
         "wrong token should produce stable unauthorized error");

  const auto valid = auth.authorize("factory-token");
  expect(valid.ok() && valid.authorized,
         "matching token should authorize factory access");
  expect(auth.can_run(FactoryCommand::ReadDiagnostics),
         "authorized factory command should be allowed");
}

void test_factory_mode_entry_requires_authorization() {
  FactoryModeController mode;

  const auto denied = mode.evaluate(ButtonIntent::FactoryEntry, false);
  expect(!denied.factory_mode_active,
         "factory mode should stay inactive without authorization");
  expect(denied.reason_code == "authorization_required",
         "denied entry should surface authorization requirement");

  const auto entered = mode.evaluate(ButtonIntent::FactoryEntry, true);
  expect(entered.factory_mode_active && entered.state_changed,
         "authorized entry intent should activate factory mode");
}

void test_factory_exit_uses_exit_intent() {
  FactoryModeController mode;
  static_cast<void>(mode.evaluate(ButtonIntent::FactoryEntry, true));

  const auto exited = mode.evaluate(ButtonIntent::FactoryExit, true);
  expect(!exited.factory_mode_active && exited.state_changed,
         "factory exit intent should leave factory mode");
  expect(exited.reason_code == "factory_mode_exited",
         "exit should report a stable reason");
}

void test_factory_check_runs_once_and_locks_factory_mode() {
  InMemoryFactoryProvisioningStore store;
  FactoryProvisioningController controller(store);

  const auto executed = controller.run_hardware_check(true, true);
  expect(executed.executed && executed.passed,
         "authorized hardware check should execute once");
  expect(executed.lockout_active,
         "hardware check completion should enable the provisioning lockout");

  const auto blocked_entry = controller.evaluate_entry(ButtonIntent::FactoryEntry, true);
  expect(!blocked_entry.factory_mode_active,
         "factory entry should be blocked after provisioning lockout");
  expect(blocked_entry.reason_code == "factory_mode_locked_out",
         "lockout should surface a stable re-entry rejection reason");

  const auto repeated = controller.run_hardware_check(true, true);
  expect(!repeated.executed && repeated.lockout_active,
         "hardware check must not rerun once the unit is locked out");
}

void test_factory_check_requires_authorization() {
  InMemoryFactoryProvisioningStore store;
  FactoryProvisioningController controller(store);

  const auto denied = controller.run_hardware_check(false, true);
  expect(!denied.executed,
         "hardware check should not execute without authorization");
  expect(denied.reason_code == "authorization_required",
         "unauthorized hardware check should report a stable reason");
}

void test_factory_provisioning_state_persists_across_reboot() {
  const std::string path = "/tmp/airhealth_factory_provisioning_test.state";
  std::remove(path.c_str());
  {
    FileFactoryProvisioningStore store(path);
    FactoryProvisioningController controller(store);
    const auto failed = controller.run_hardware_check(true, false);
    expect(failed.executed && !failed.passed,
           "failed hardware check should still be recorded");
  }

  {
    FileFactoryProvisioningStore rebooted_store(path);
    FactoryProvisioningController rebooted_controller(rebooted_store);
    const auto state = rebooted_controller.load_state();
    expect(state.hardware_check_completed,
           "completed hardware check should survive reboot-style reload");
    expect(!state.hardware_check_passed,
           "persisted state should retain pass or fail outcome");
    expect(state.provisioning_locked,
           "provisioning lockout should survive reboot-style reload");

    const auto entry = rebooted_controller.evaluate_entry(
        ButtonIntent::FactoryEntry,
        true
    );
    expect(entry.reason_code == "factory_mode_locked_out",
           "rebooted controller should keep re-entry blocked deterministically");
  }
  std::remove(path.c_str());
}

}  // namespace

int main() {
  try {
    test_factory_commands_are_blocked_before_authorization();
    test_authorization_requires_matching_token();
    test_factory_mode_entry_requires_authorization();
    test_factory_exit_uses_exit_intent();
    test_factory_check_runs_once_and_locks_factory_mode();
    test_factory_check_requires_authorization();
    test_factory_provisioning_state_persists_across_reboot();
  } catch (const std::exception& error) {
    std::cerr << "factory_auth_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "factory_auth_test passed\n";
  return EXIT_SUCCESS;
}
