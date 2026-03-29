#include "airhealth/fw/factory_bundle.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

using airhealth::fw::FactoryBundleStore;
using airhealth::fw::FactoryDiagnosticBundle;
using airhealth::fw::factory_bundle_to_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_bundle_store_limits_retention() {
  FactoryBundleStore store(3);
  store.save(FactoryDiagnosticBundle {
      .bundle_id = "bundle-28",
      .log_lines = {"l1", "l2", "l3", "l4", "l5"},
  });

  const auto bundle = store.load();
  expect(store.retained_line_count() == 3,
         "factory bundle retention should stay bounded");
  expect(bundle.log_lines.front() == "l3" && bundle.log_lines.back() == "l5",
         "factory bundle should retain only the newest bounded lines");
}

void test_bundle_store_is_isolated_from_consumer_journal_shape() {
  FactoryBundleStore store;
  store.save(FactoryDiagnosticBundle {
      .bundle_id = "factory-run",
      .log_lines = {"boot_reason=brownout", "hw_check=pass"},
  });

  const auto json = factory_bundle_to_json(store.load());
  expect(json.find("session_id") == std::string::npos,
         "factory bundle payload should not reuse consumer journal fields");
  expect(json.find("\"bundle_id\":\"factory-run\"") != std::string::npos,
         "factory bundle payload should identify the stored bundle");
}

void test_clear_removes_bundle() {
  FactoryBundleStore store;
  store.save(FactoryDiagnosticBundle {
      .bundle_id = "bundle-clear",
      .log_lines = {"line"},
  });
  store.clear();
  expect(store.empty(), "clearing the factory bundle should remove stored data");
}

}  // namespace

int main() {
  try {
    test_bundle_store_limits_retention();
    test_bundle_store_is_isolated_from_consumer_journal_shape();
    test_clear_removes_bundle();
  } catch (const std::exception& error) {
    std::cerr << "factory_bundle_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "factory_bundle_test passed\n";
  return EXIT_SUCCESS;
}
