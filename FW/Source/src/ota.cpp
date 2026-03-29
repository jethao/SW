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

std::size_t expected_chunk_size(
    const OtaManifest& manifest,
    std::size_t total_chunks,
    std::size_t index
) {
  if (index + 1 < total_chunks) {
    return manifest.chunk_size_bytes;
  }

  const std::size_t bytes_before_last =
      manifest.chunk_size_bytes * (total_chunks - 1);
  return manifest.total_size_bytes - bytes_before_last;
}

std::size_t received_count(const std::vector<bool>& received) {
  std::size_t count = 0;
  for (bool value : received) {
    if (value) {
      ++count;
    }
  }
  return count;
}

}  // namespace

void OtaChunkReceiver::reset() {
  manifest_ = {};
  received_.clear();
  chunks_.clear();
}

void OtaChunkReceiver::begin(const OtaManifest& manifest) {
  manifest_ = manifest;
  const std::size_t total_chunks = total_chunks_for(manifest);
  received_.assign(total_chunks, false);
  chunks_.assign(total_chunks, "");
}

OtaProgress OtaChunkReceiver::ingest(const OtaChunk& chunk) {
  if (received_.empty()) {
    throw std::invalid_argument("OTA manifest must be started before chunks");
  }

  if (chunk.index >= received_.size()) {
    return OtaProgress {
        .received_chunks = received_count(received_),
        .total_chunks = received_.size(),
        .reason_code = "chunk_out_of_range",
    };
  }

  const std::size_t expected_size =
      expected_chunk_size(manifest_, received_.size(), chunk.index);
  if (chunk.payload.size() != expected_size) {
    return OtaProgress {
        .received_chunks = received_count(received_),
        .total_chunks = received_.size(),
        .reason_code = "chunk_size_mismatch",
    };
  }

  if (received_[chunk.index]) {
    if (chunks_[chunk.index] != chunk.payload) {
      return OtaProgress {
          .received_chunks = received_count(received_),
          .total_chunks = received_.size(),
          .reason_code = "chunk_conflict",
      };
    }

    return OtaProgress {
        .received_chunks = received_count(received_),
        .total_chunks = received_.size(),
        .reason_code = complete() ? "transfer_complete" : "chunk_resumed",
    };
  }

  received_[chunk.index] = true;
  chunks_[chunk.index] = chunk.payload;

  const std::size_t total_received = received_count(received_);
  return OtaProgress {
      .received_chunks = total_received,
      .total_chunks = received_.size(),
      .reason_code = total_received == received_.size()
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

bool OtaChunkReceiver::has_chunk(std::size_t index) const {
  return index < received_.size() && received_[index];
}

std::string OtaChunkReceiver::staged_image() const {
  if (!complete()) {
    throw std::logic_error("OTA image is not complete");
  }

  std::string assembled;
  assembled.reserve(manifest_.total_size_bytes);
  for (const auto& chunk : chunks_) {
    assembled += chunk;
  }
  return assembled;
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
