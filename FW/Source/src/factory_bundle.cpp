#include "airhealth/fw/factory_bundle.hpp"

#include <stdexcept>
#include <sstream>

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

std::string flatten_bundle_payload(const FactoryDiagnosticBundle& bundle) {
  std::ostringstream out;
  out << bundle.bundle_id << "\n";
  for (std::size_t index = 0; index < bundle.log_lines.size(); ++index) {
    if (index > 0) {
      out << "\n";
    }
    out << bundle.log_lines[index];
  }
  return out.str();
}

std::size_t acknowledged_count(const std::vector<bool>& acknowledged) {
  std::size_t count = 0;
  for (bool value : acknowledged) {
    if (value) {
      ++count;
    }
  }
  return count;
}

}  // namespace

FactoryBundleStore::FactoryBundleStore(std::size_t max_lines)
    : max_lines_(max_lines) {}

void FactoryBundleStore::save(const FactoryDiagnosticBundle& bundle) {
  bundle_ = bundle;
  if (bundle_.log_lines.size() > max_lines_) {
    bundle_.log_lines.erase(
        bundle_.log_lines.begin(),
        bundle_.log_lines.end() - static_cast<std::ptrdiff_t>(max_lines_)
    );
  }
}

FactoryDiagnosticBundle FactoryBundleStore::load() const {
  return bundle_;
}

void FactoryBundleStore::clear() {
  bundle_ = {};
}

std::size_t FactoryBundleStore::retained_line_count() const {
  return bundle_.log_lines.size();
}

bool FactoryBundleStore::empty() const {
  return bundle_.bundle_id.empty() && bundle_.log_lines.empty();
}

FactoryBundleTransferSession::FactoryBundleTransferSession(
    std::size_t chunk_size_bytes
) : chunk_size_bytes_(chunk_size_bytes) {}

void FactoryBundleTransferSession::begin(const FactoryDiagnosticBundle& bundle) {
  if (chunk_size_bytes_ == 0) {
    throw std::invalid_argument("chunk size must be non-zero");
  }

  bundle_ = bundle;
  payload_chunks_.clear();
  acknowledged_.clear();
  delivered_ = false;

  const std::string payload = flatten_bundle_payload(bundle);
  if (payload.empty()) {
    payload_chunks_.push_back("");
  } else {
    for (std::size_t offset = 0; offset < payload.size();
         offset += chunk_size_bytes_) {
      payload_chunks_.push_back(payload.substr(offset, chunk_size_bytes_));
    }
  }
  acknowledged_.assign(payload_chunks_.size(), false);
}

FactoryBundleChunk FactoryBundleTransferSession::chunk(
    std::size_t chunk_index
) const {
  if (chunk_index >= payload_chunks_.size()) {
    throw std::out_of_range("factory bundle chunk index out of range");
  }

  return FactoryBundleChunk {
      .bundle_id = bundle_.bundle_id,
      .chunk_index = chunk_index,
      .total_chunks = payload_chunks_.size(),
      .payload = payload_chunks_[chunk_index],
  };
}

FactoryTransferProgress FactoryBundleTransferSession::acknowledge(
    std::size_t chunk_index
) {
  if (chunk_index >= acknowledged_.size()) {
    return FactoryTransferProgress {
        .acknowledged_chunks = acknowledged_count(acknowledged_),
        .total_chunks = acknowledged_.size(),
        .delivered = delivered_,
        .reason_code = "chunk_out_of_range",
    };
  }

  acknowledged_[chunk_index] = true;
  const std::size_t total_acknowledged = acknowledged_count(acknowledged_);
  delivered_ = total_acknowledged == acknowledged_.size();

  return FactoryTransferProgress {
      .acknowledged_chunks = total_acknowledged,
      .total_chunks = acknowledged_.size(),
      .delivered = delivered_,
      .reason_code = delivered_ ? "bundle_delivered" : "chunk_acknowledged",
  };
}

FactoryTransferProgress FactoryBundleTransferSession::progress() const {
  return FactoryTransferProgress {
      .acknowledged_chunks = acknowledged_count(acknowledged_),
      .total_chunks = acknowledged_.size(),
      .delivered = delivered_,
      .reason_code = delivered_ ? "bundle_delivered" : "transfer_in_progress",
  };
}

bool FactoryBundleTransferSession::delivered() const {
  return delivered_;
}

std::string factory_bundle_to_json(const FactoryDiagnosticBundle& bundle) {
  std::ostringstream out;
  out << "{"
      << "\"bundle_id\":" << json_string(bundle.bundle_id) << ","
      << "\"log_lines\":[";

  for (std::size_t index = 0; index < bundle.log_lines.size(); ++index) {
    if (index > 0) {
      out << ",";
    }
    out << json_string(bundle.log_lines[index]);
  }

  out << "]}";
  return out.str();
}

}  // namespace airhealth::fw
