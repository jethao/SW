#include "airhealth/fw/session_journal.hpp"

#include <fstream>
#include <iomanip>
#include <sstream>
#include <stdexcept>

namespace airhealth::fw {

namespace {

std::string bool_to_storage(bool value) {
  return value ? "1" : "0";
}

bool parse_bool(const std::string& value) {
  return value == "1";
}

std::string canonicalize(const SessionJournalEntry& entry) {
  std::ostringstream out;
  out << entry.result.session_id << "\n"
      << session_mode_to_string(entry.result.mode) << "\n"
      << session_state_to_string(entry.result.terminal_state) << "\n"
      << entry.result.produced_at << "\n"
      << entry.result.terminal_reason << "\n"
      << entry.result.battery.percent << "\n"
      << bool_to_storage(entry.result.battery.charging) << "\n"
      << bool_to_storage(entry.result.battery.low_power) << "\n"
      << bool_to_storage(entry.result.quality_gates.sample_valid) << "\n"
      << bool_to_storage(entry.result.quality_gates.warmup_ready) << "\n"
      << bool_to_storage(entry.result.quality_gates.motion_stable) << "\n"
      << entry.result.algorithm_version << "\n"
      << entry.result.routing.hardware_profile << "\n"
      << entry.result.routing.voc_profile << "\n"
      << bool_to_storage(entry.unreconciled) << "\n";
  return out.str();
}

std::uint32_t crc32(const std::string& data) {
  std::uint32_t crc = 0xFFFFFFFFu;
  for (unsigned char byte : data) {
    crc ^= static_cast<std::uint32_t>(byte);
    for (int bit = 0; bit < 8; ++bit) {
      const std::uint32_t mask = -(crc & 1u);
      crc = (crc >> 1u) ^ (0xEDB88320u & mask);
    }
  }
  return ~crc;
}

std::string to_hex(std::uint32_t value) {
  std::ostringstream out;
  out << std::hex << std::setw(8) << std::setfill('0') << value;
  return out.str();
}

std::string require_line(std::ifstream& input) {
  std::string line;
  if (!std::getline(input, line)) {
    throw std::runtime_error("Incomplete session journal record");
  }
  return line;
}

SessionMode parse_mode(const std::string& value) {
  if (value == "oral_health") {
    return SessionMode::OralHealth;
  }
  if (value == "fat_burning") {
    return SessionMode::FatBurning;
  }
  throw std::runtime_error("Unsupported stored session mode");
}

SessionState parse_state(const std::string& value) {
  if (value == "ready") {
    return SessionState::Ready;
  }
  if (value == "active") {
    return SessionState::Active;
  }
  if (value == "complete") {
    return SessionState::Complete;
  }
  if (value == "canceled") {
    return SessionState::Canceled;
  }
  if (value == "failed") {
    return SessionState::Failed;
  }
  if (value == "interrupted") {
    return SessionState::Interrupted;
  }
  if (value == "reconciled") {
    return SessionState::Reconciled;
  }
  throw std::runtime_error("Unsupported stored session state");
}

}  // namespace

SessionJournalLoadResult InMemorySessionJournalStore::load() const {
  if (!present_) {
    return SessionJournalLoadResult {
        .error = SessionJournalError::NotFound,
    };
  }

  return SessionJournalLoadResult {
      .error = SessionJournalError::None,
      .entry = entry_,
  };
}

void InMemorySessionJournalStore::save(const SessionJournalEntry& entry) {
  present_ = true;
  entry_ = entry;
}

FileSessionJournalStore::FileSessionJournalStore(std::string storage_path)
    : storage_path_(std::move(storage_path)) {}

SessionJournalLoadResult FileSessionJournalStore::load() const {
  std::ifstream input(storage_path_);
  if (!input.is_open()) {
    return SessionJournalLoadResult {
        .error = SessionJournalError::NotFound,
    };
  }

  try {
    SessionJournalEntry entry {};
    entry.result.session_id = require_line(input);
    entry.result.mode = parse_mode(require_line(input));
    entry.result.terminal_state = parse_state(require_line(input));
    entry.result.produced_at = require_line(input);
    entry.result.terminal_reason = require_line(input);
    entry.result.battery.percent = std::stoi(require_line(input));
    entry.result.battery.charging = parse_bool(require_line(input));
    entry.result.battery.low_power = parse_bool(require_line(input));
    entry.result.quality_gates.sample_valid = parse_bool(require_line(input));
    entry.result.quality_gates.warmup_ready = parse_bool(require_line(input));
    entry.result.quality_gates.motion_stable = parse_bool(require_line(input));
    entry.result.algorithm_version = require_line(input);
    entry.result.routing.hardware_profile = require_line(input);
    entry.result.routing.voc_profile = require_line(input);
    entry.unreconciled = parse_bool(require_line(input));
    entry.crc32 = static_cast<std::uint32_t>(
        std::stoul(require_line(input), nullptr, 16)
    );

    if (session_journal_crc32(entry) != entry.crc32) {
      return SessionJournalLoadResult {
          .error = SessionJournalError::Corrupt,
      };
    }

    return SessionJournalLoadResult {
        .error = SessionJournalError::None,
        .entry = entry,
    };
  } catch (const std::exception&) {
    return SessionJournalLoadResult {
        .error = SessionJournalError::Corrupt,
    };
  }
}

void FileSessionJournalStore::save(const SessionJournalEntry& entry) {
  std::ofstream output(storage_path_, std::ios::trunc);
  if (!output.is_open()) {
    throw std::runtime_error("Unable to open session journal for write");
  }

  output << canonicalize(entry) << to_hex(entry.crc32) << "\n";
}

const std::string& FileSessionJournalStore::storage_path() const {
  return storage_path_;
}

SessionJournalEntry make_session_journal_entry(
    const SessionResultEnvelope& result,
    bool unreconciled
) {
  SessionJournalEntry entry {
      .result = result,
      .unreconciled = unreconciled,
      .crc32 = 0,
  };
  entry.crc32 = session_journal_crc32(entry);
  return entry;
}

std::uint32_t session_journal_crc32(const SessionJournalEntry& entry) {
  return crc32(canonicalize(entry));
}

std::string session_journal_error_to_string(SessionJournalError error) {
  switch (error) {
    case SessionJournalError::None:
      return "none";
    case SessionJournalError::NotFound:
      return "not_found";
    case SessionJournalError::Corrupt:
      return "corrupt";
  }

  throw std::invalid_argument("Unsupported session journal error");
}

}  // namespace airhealth::fw
