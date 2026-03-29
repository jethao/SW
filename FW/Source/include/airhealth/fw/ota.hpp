#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace airhealth::fw {

struct OtaManifest {
  std::string image_id;
  std::size_t total_size_bytes = 0;
  std::size_t chunk_size_bytes = 0;
  std::string image_digest;
};

struct OtaChunk {
  std::size_t index = 0;
  std::string payload;
};

struct OtaProgress {
  std::size_t received_chunks = 0;
  std::size_t total_chunks = 0;
  std::string reason_code;
};

struct OtaApplyResult {
  bool applied = false;
  bool verified = false;
  std::string pending_slot;
  std::string reason_code;
};

class OtaChunkReceiver {
 public:
  void reset();
  void begin(const OtaManifest& manifest);
  [[nodiscard]] OtaProgress ingest(const OtaChunk& chunk);
  [[nodiscard]] bool complete() const;
  [[nodiscard]] bool has_chunk(std::size_t index) const;
  [[nodiscard]] std::string staged_image() const;
  [[nodiscard]] OtaApplyResult stage_apply(const std::string& pending_slot);
  [[nodiscard]] const std::string& pending_slot() const;

 private:
  OtaManifest manifest_ {};
  std::vector<bool> received_ {};
  std::vector<std::string> chunks_ {};
  std::string pending_slot_ {};
};

[[nodiscard]] std::string ota_image_digest(const std::string& staged_image);
[[nodiscard]] std::string ota_progress_to_json(const OtaProgress& progress);

}  // namespace airhealth::fw
