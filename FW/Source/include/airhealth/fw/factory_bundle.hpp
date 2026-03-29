#pragma once

#include <string>
#include <vector>

namespace airhealth::fw {

struct FactoryDiagnosticBundle {
  std::string bundle_id;
  std::vector<std::string> log_lines;
};

struct FactoryBundleChunk {
  std::string bundle_id;
  std::size_t chunk_index = 0;
  std::size_t total_chunks = 0;
  std::string payload;
};

struct FactoryTransferProgress {
  std::size_t acknowledged_chunks = 0;
  std::size_t total_chunks = 0;
  bool delivered = false;
  std::string reason_code;
};

class FactoryBundleStore {
 public:
  explicit FactoryBundleStore(std::size_t max_lines = 16);

  void save(const FactoryDiagnosticBundle& bundle);
  [[nodiscard]] FactoryDiagnosticBundle load() const;
  void clear();

  [[nodiscard]] std::size_t retained_line_count() const;
  [[nodiscard]] bool empty() const;

 private:
  std::size_t max_lines_ = 16;
  FactoryDiagnosticBundle bundle_ {};
};

class FactoryBundleTransferSession {
 public:
  explicit FactoryBundleTransferSession(std::size_t chunk_size_bytes = 16);

  void begin(const FactoryDiagnosticBundle& bundle);
  [[nodiscard]] FactoryBundleChunk chunk(std::size_t chunk_index) const;
  [[nodiscard]] FactoryTransferProgress acknowledge(std::size_t chunk_index);
  [[nodiscard]] FactoryTransferProgress progress() const;
  [[nodiscard]] bool delivered() const;

 private:
  std::size_t chunk_size_bytes_ = 16;
  FactoryDiagnosticBundle bundle_ {};
  std::vector<std::string> payload_chunks_ {};
  std::vector<bool> acknowledged_ {};
  bool delivered_ = false;
};

[[nodiscard]] std::string factory_bundle_to_json(
    const FactoryDiagnosticBundle& bundle
);

}  // namespace airhealth::fw
