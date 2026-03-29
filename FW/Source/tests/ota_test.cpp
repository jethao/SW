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
using airhealth::fw::ota_image_digest;
using airhealth::fw::ota_progress_to_json;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

OtaManifest make_manifest() {
  const std::string image =
      std::string(256, 'A') + std::string(256, 'B') +
      std::string(256, 'C') + std::string(256, 'D');
  return OtaManifest {
      .image_id = "ota-image-30",
      .total_size_bytes = 1024,
      .chunk_size_bytes = 256,
      .image_digest = ota_image_digest(image),
  };
}

OtaManifest make_resume_manifest() {
  const std::string image = "ABCDEFGHIJ";
  return OtaManifest {
      .image_id = "ota-image-resume",
      .total_size_bytes = 10,
      .chunk_size_bytes = 4,
      .image_digest = ota_image_digest(image),
  };
}

void test_chunk_progress_accumulates_until_complete() {
  OtaChunkReceiver receiver;
  receiver.begin(make_manifest());

  const auto first = receiver.ingest(OtaChunk {
      .index = 0,
      .payload = std::string(256, 'A'),
  });
  expect(first.received_chunks == 1 && first.total_chunks == 4,
         "first chunk should report partial OTA progress");

  static_cast<void>(receiver.ingest(OtaChunk {.index = 1, .payload = std::string(256, 'B')}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 2, .payload = std::string(256, 'C')}));
  const auto final = receiver.ingest(OtaChunk {.index = 3, .payload = std::string(256, 'D')});
  expect(receiver.complete(), "receiver should report complete after all chunks");
  expect(final.reason_code == "transfer_complete",
         "last chunk should mark transfer complete");
  expect(receiver.staged_image().size() == 1024,
         "complete OTA image should be retained for handoff");
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

void test_malformed_chunk_sizes_are_rejected() {
  OtaChunkReceiver receiver;
  receiver.begin(make_manifest());

  const auto result = receiver.ingest(OtaChunk {
      .index = 0,
      .payload = "too-short",
  });
  expect(result.reason_code == "chunk_size_mismatch",
         "invalid chunk sizes should fail with a stable reason");
  expect(!receiver.has_chunk(0),
         "malformed chunks must not be retained as staged data");
}

void test_interrupted_transfer_can_resume_with_retained_chunks() {
  OtaChunkReceiver receiver;
  receiver.begin(make_resume_manifest());

  static_cast<void>(receiver.ingest(OtaChunk {.index = 0, .payload = "ABCD"}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 2, .payload = "IJ"}));
  expect(!receiver.complete(), "transfer should remain incomplete with a gap");
  expect(receiver.has_chunk(0) && receiver.has_chunk(2),
         "receiver should retain earlier chunks across an interrupted transfer");

  const auto resumed = receiver.ingest(OtaChunk {.index = 0, .payload = "ABCD"});
  expect(resumed.reason_code == "chunk_resumed",
         "duplicate chunk upload during resume should be idempotent");

  const auto completed = receiver.ingest(OtaChunk {.index = 1, .payload = "EFGH"});
  expect(completed.reason_code == "transfer_complete",
         "supplying the missing chunk should complete the resumed transfer");
  expect(receiver.staged_image() == "ABCDEFGHIJ",
         "staged OTA image should be assembled in chunk order after resume");
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

void test_invalid_image_digest_fails_validation() {
  OtaChunkReceiver receiver;
  auto manifest = make_resume_manifest();
  manifest.image_digest = "bad-digest";
  receiver.begin(manifest);

  static_cast<void>(receiver.ingest(OtaChunk {.index = 0, .payload = "ABCD"}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 1, .payload = "EFGH"}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 2, .payload = "IJ"}));

  const auto result = receiver.stage_apply("slot-b");
  expect(!result.applied && !result.verified,
         "invalid OTA digest should block staged apply");
  expect(result.reason_code == "validation_failed",
         "digest mismatch should emit a stable validation failure reason");
}

void test_verified_image_records_pending_slot_metadata() {
  OtaChunkReceiver receiver;
  receiver.begin(make_resume_manifest());

  static_cast<void>(receiver.ingest(OtaChunk {.index = 0, .payload = "ABCD"}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 1, .payload = "EFGH"}));
  static_cast<void>(receiver.ingest(OtaChunk {.index = 2, .payload = "IJ"}));

  const auto apply = receiver.stage_apply("slot-b");
  expect(apply.applied && apply.verified,
         "verified OTA image should be staged for apply");
  expect(apply.pending_slot == "slot-b",
         "apply result should retain pending-slot metadata for handoff");
  expect(receiver.pending_slot() == "slot-b",
         "receiver should retain pending-slot metadata after staging");
  expect(apply.reason_code == "apply_staged",
         "successful apply staging should emit a stable reason code");
}

}  // namespace

int main() {
  try {
    test_chunk_progress_accumulates_until_complete();
    test_out_of_range_chunks_are_rejected();
    test_malformed_chunk_sizes_are_rejected();
    test_interrupted_transfer_can_resume_with_retained_chunks();
    test_progress_json_is_stable();
    test_invalid_image_digest_fails_validation();
    test_verified_image_records_pending_slot_metadata();
  } catch (const std::exception& error) {
    std::cerr << "ota_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "ota_test passed\n";
  return EXIT_SUCCESS;
}
