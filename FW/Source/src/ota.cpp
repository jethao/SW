#include "airhealth/fw/ota.hpp"

#include <sstream>
#include <stdexcept>

namespace airhealth::fw {

namespace {

std::string json_string(const std::string& value) {
  std::ostringstream out;
  out << "\"";
  for (char c : value) {
    if (c == '"' || c == '\\') {
      out << '\\';
    }
    out << c;
  }
  out << "\"";
  return out.str();
}

std::size_t total_chunks_for(const OtaManifest& manifest) {
  if (manifest.chunk_size_bytes == 0) {
    throw std::invalid_argument("chunk size must be non-zero");
  }
  return (manifest.total_size_bytes + manifest.chunk_size_bytes - 1) /
      manifest.chunk_size_bytes;
}

}  // namespace

void OtaChunkReceiver::reset() {
  manifest_ = {};
  received_.clear();
}

void OtaChunkReceiver::begin(const OtaManifest& manifest) {
  manifest_ = manifest;
  received_.assign(total_chunks_for(manifest), false);
}

OtaProgress OtaChunkReceiver::ingest(const OtaChunk& chunk) {
  if (received_.empty()) {
    throw std::invalid_argument("OTA manifest must be started before chunks");
  }

  if (chunk.index >= received_.size()) {
    return OtaProgress {
        .received_chunks = 0,
        .total_chunks = received_.size(),
        .reason_code = "chunk_out_of_range",
    };
  }

  received_[chunk.index] = true;

  std::size_t received_count = 0;
  for (bool value : received_) {
    if (value) {
      ++received_count;
    }
  }

  return OtaProgress {
      .received_chunks = received_count,
      .total_chunks = received_.size(),
      .reason_code = received_count == received_.size()
          ? "transfer_complete"
          : "chunk_accepted",
  };
}

bool OtaChunkReceiver::complete() const {
  if (received_.empty()) {
    return false;
  }

  for (bool value : received_) {
    if (!value) {
      return false;
    }
  }
  return true;
}

std::string ota_progress_to_json(const OtaProgress& progress) {
  std::ostringstream out;
  out << "{"
      << "\"received_chunks\":" << progress.received_chunks << ","
      << "\"total_chunks\":" << progress.total_chunks << ","
      << "\"reason_code\":" << json_string(progress.reason_code)
      << "}";
  return out.str();
}

}  // namespace airhealth::fw
