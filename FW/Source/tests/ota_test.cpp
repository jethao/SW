#include "airhealth/fw/ota.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::OtaChunk;
using airhealth::fw::OtaChunkReceiver;
using airhealth::fw::OtaManifest;
using airhealth::fw::OtaProgress;
using airhealth::fw::ota_progress_to_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

OtaManifest make_manifest() {
  return OtaManifest {
      .image_id = "ota-image-30",
      .total_size_bytes = 1024,
      .chunk_size_bytes = 256,
  };
}

void test_chunk_progress_accumulates_until_complete() {
  OtaChunkReceiver receiver;
  receiver.begin(make_manifest());

  const auto first = receiver.ingest(OtaChunk {
      .index = 0,
      .payload = "chunk0",
  });
  expect(first.received_chunks == 1 && first.total_chunks == 4,
         "first chunk should report partial OTA progress");

  static_cast<void>(receiver.ingest(OtaChunk {.index = 1, .payload = "chunk1"}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 2, .payload = "chunk2"}));
  const auto final = receiver.ingest(OtaChunk {.index = 3, .payload = "chunk3"});
  expect(receiver.complete(), "receiver should report complete after all chunks");
  expect(final.reason_code == "transfer_complete",
         "last chunk should mark transfer complete");
}

void test_out_of_range_chunks_are_rejected() {
  OtaChunkReceiver receiver;
  receiver.begin(make_manifest());

  const auto result = receiver.ingest(OtaChunk {
      .index = 99,
      .payload = "bad",
  });
  expect(result.reason_code == "chunk_out_of_range",
         "invalid chunk indexes should be rejected deterministically");
}

void test_progress_json_is_stable() {
  const auto json = ota_progress_to_json(OtaProgress {
      .received_chunks = 2,
      .total_chunks = 4,
      .reason_code = "chunk_accepted",
  });
  expect(
      json ==
          "{\"received_chunks\":2,\"total_chunks\":4,"
          "\"reason_code\":\"chunk_accepted\"}",
      "OTA progress JSON should remain deterministic"
  );
}

}  // namespace

int main() {
  try {
    test_chunk_progress_accumulates_until_complete();
    test_out_of_range_chunks_are_rejected();
    test_progress_json_is_stable();
  } catch (const std::exception& error) {
    std::cerr << "ota_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "ota_test passed\n";
  return EXIT_SUCCESS;
}
