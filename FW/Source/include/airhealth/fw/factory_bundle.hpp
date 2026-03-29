#pragma once

#include <string>
#include <vector>

namespace airhealth::fw {

struct FactoryDiagnosticBundle {
  std::string bundle_id;
  std::vector<std::string> log_lines;
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

[[nodiscard]] std::string factory_bundle_to_json(
    const FactoryDiagnosticBundle& bundle
);

}  // namespace airhealth::fw
