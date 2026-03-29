#include "airhealth/fw/factory_bundle.hpp"

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
