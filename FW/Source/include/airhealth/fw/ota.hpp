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

class OtaChunkReceiver {
 public:
  void reset();
  void begin(const OtaManifest& manifest);
  [[nodiscard]] OtaProgress ingest(const OtaChunk& chunk);
  [[nodiscard]] bool complete() const;

 private:
  OtaManifest manifest_ {};
  std::vector<bool> received_ {};
};

[[nodiscard]] std::string ota_progress_to_json(const OtaProgress& progress);

}  // namespace airhealth::fw
