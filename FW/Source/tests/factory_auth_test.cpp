#include "airhealth/fw/factory_auth.hpp"

#include "airhealth/fw/led_button.hpp"

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

}  // namespace

int main() {
  try {
    test_factory_commands_are_blocked_before_authorization();
    test_authorization_requires_matching_token();
    test_factory_mode_entry_requires_authorization();
    test_factory_exit_uses_exit_intent();
  } catch (const std::exception& error) {
    std::cerr << "factory_auth_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "factory_auth_test passed\n";
  return EXIT_SUCCESS;
}
