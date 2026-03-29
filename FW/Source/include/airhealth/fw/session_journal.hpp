#pragma once

#include "airhealth/fw/session_contract.hpp"

#include <cstdint>
#include <string>

namespace airhealth::fw {

struct SessionJournalEntry {
  SessionResultEnvelope result {};
  bool unreconciled = true;
  std::uint32_t crc32 = 0;
};

enum class SessionJournalError {
  None,
  NotFound,
  Corrupt,
};

struct SessionJournalLoadResult {
  SessionJournalError error = SessionJournalError::None;
  SessionJournalEntry entry {};

  [[nodiscard]] bool ok() const {
    return error == SessionJournalError::None;
  }
};

class SessionJournalStore {
 public:
  virtual ~SessionJournalStore() = default;

  [[nodiscard]] virtual SessionJournalLoadResult load() const = 0;
  virtual void save(const SessionJournalEntry& entry) = 0;
  virtual void clear() = 0;
};

class InMemorySessionJournalStore final : public SessionJournalStore {
 public:
  [[nodiscard]] SessionJournalLoadResult load() const override;
  void save(const SessionJournalEntry& entry) override;
  void clear() override;

 private:
  bool present_ = false;
  SessionJournalEntry entry_ {};
};

class FileSessionJournalStore final : public SessionJournalStore {
 public:
  explicit FileSessionJournalStore(std::string storage_path);

  [[nodiscard]] SessionJournalLoadResult load() const override;
  void save(const SessionJournalEntry& entry) override;
  void clear() override;

  [[nodiscard]] const std::string& storage_path() const;

 private:
  std::string storage_path_;
};

[[nodiscard]] SessionJournalEntry make_session_journal_entry(
    const SessionResultEnvelope& result,
    bool unreconciled = true
);

[[nodiscard]] std::uint32_t session_journal_crc32(
    const SessionJournalEntry& entry
);

[[nodiscard]] std::string session_journal_error_to_string(
    SessionJournalError error
);

enum class SessionReplayError {
  None,
  NotFound,
  JournalCorrupt,
  SessionIdMismatch,
};

struct SessionReplayResult {
  SessionReplayError error = SessionReplayError::None;
  SessionResultEnvelope replayed_result {};

  [[nodiscard]] bool ok() const {
    return error == SessionReplayError::None;
  }
};

class SessionReplayService {
 public:
  explicit SessionReplayService(SessionJournalStore& store);

  [[nodiscard]] SessionReplayResult query_by_session_id(
      const std::string& session_id
  ) const;
  [[nodiscard]] SessionReplayError acknowledge_session_id(
      const std::string& session_id
  );

 private:
  SessionJournalStore& store_;
};

[[nodiscard]] std::string session_replay_error_to_string(
    SessionReplayError error
);

}  // namespace airhealth::fw
