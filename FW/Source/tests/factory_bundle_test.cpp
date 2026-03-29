#include "airhealth/fw/factory_bundle.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

using airhealth::fw::FactoryBundleStore;
using airhealth::fw::FactoryBundleTransferSession;
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

void test_chunk_transfer_can_resume_without_restarting_bundle() {
  FactoryBundleTransferSession transfer(8);
  transfer.begin(FactoryDiagnosticBundle {
      .bundle_id = "bundle-29",
      .log_lines = {
          "boot_reason=brownout",
          "hw_check=pass",
          "sensor=stable",
      },
  });

  const auto first = transfer.chunk(0);
  const auto third = transfer.chunk(2);
  expect(first.total_chunks >= 3,
         "bundle should be split into resumable chunks");
  expect(first.bundle_id == "bundle-29",
         "chunk metadata should carry the bundle identifier");
  expect(!third.payload.empty(),
         "later chunks should be addressable before intermediate ACKs");

  static_cast<void>(transfer.acknowledge(0));
  static_cast<void>(transfer.acknowledge(2));
  const auto resumed = transfer.progress();
  expect(resumed.acknowledged_chunks == 2 && !resumed.delivered,
         "interrupted transfer should retain prior acknowledgments");

  for (std::size_t index = 1; index < resumed.total_chunks; ++index) {
    static_cast<void>(transfer.acknowledge(index));
  }
  expect(transfer.delivered(),
         "transfer should complete after the missing chunks are acknowledged");
}

void test_acknowledgment_marks_bundle_delivered() {
  FactoryBundleTransferSession transfer(64);
  transfer.begin(FactoryDiagnosticBundle {
      .bundle_id = "bundle-ack",
      .log_lines = {"line-1", "line-2"},
  });

  const auto only_chunk = transfer.chunk(0);
  expect(only_chunk.total_chunks == 1,
         "small bundle should fit within a single transfer chunk");

  const auto ack = transfer.acknowledge(0);
  expect(ack.delivered,
         "acknowledging the final chunk should mark the bundle delivered");
  expect(ack.reason_code == "bundle_delivered",
         "delivery completion should emit a stable reason code");
}

void test_invalid_ack_is_rejected_without_losing_progress() {
  FactoryBundleTransferSession transfer(12);
  transfer.begin(FactoryDiagnosticBundle {
      .bundle_id = "bundle-invalid-ack",
      .log_lines = {"alpha", "beta", "gamma"},
  });

  static_cast<void>(transfer.acknowledge(0));
  const auto invalid = transfer.acknowledge(99);
  expect(invalid.reason_code == "chunk_out_of_range",
         "invalid ACK indexes should fail deterministically");
  expect(invalid.acknowledged_chunks == 1,
         "invalid ACKs should not erase prior transfer progress");
}

}  // namespace

int main() {
  try {
    test_bundle_store_limits_retention();
    test_bundle_store_is_isolated_from_consumer_journal_shape();
    test_clear_removes_bundle();
    test_chunk_transfer_can_resume_without_restarting_bundle();
    test_acknowledgment_marks_bundle_delivered();
    test_invalid_ack_is_rejected_without_losing_progress();
  } catch (const std::exception& error) {
    std::cerr << "factory_bundle_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "factory_bundle_test passed\n";
  return EXIT_SUCCESS;
}
