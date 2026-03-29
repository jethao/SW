#include "airhealth/fw/session.hpp"
#include "airhealth/fw/session_contract.hpp"
#include "airhealth/fw/session_journal.hpp"

#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::BatteryState;
using airhealth::fw::InMemorySessionJournalStore;
using airhealth::fw::FileSessionJournalStore;
using airhealth::fw::QualityGates;
using airhealth::fw::RoutingMetadata;
using airhealth::fw::SessionJournalError;
using airhealth::fw::SessionReplayService;
using airhealth::fw::SessionMode;
using airhealth::fw::session_replay_error_to_string;
using airhealth::fw::SessionResultEnvelope;
using airhealth::fw::SessionState;
using airhealth::fw::make_session_journal_entry;
using airhealth::fw::session_journal_error_to_string;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

SessionResultEnvelope make_result() {
  return SessionResultEnvelope {
      .session_id = "session-21",
      .mode = SessionMode::FatBurning,
      .terminal_state = SessionState::Complete,
      .produced_at = "2026-03-29T04:10:00Z",
      .terminal_reason = "fat_summary_ready",
      .battery =
          BatteryState {
              .percent = 63,
              .charging = false,
              .low_power = false,
          },
      .quality_gates =
          QualityGates {
              .sample_valid = true,
              .warmup_ready = true,
              .motion_stable = true,
          },
      .algorithm_version = "alg-fat-1.2.0",
      .routing =
          RoutingMetadata {
              .hardware_profile = "hw-fat",
              .voc_profile = "voc-low",
          },
  };
}

std::filesystem::path make_journal_path() {
  return std::filesystem::temp_directory_path() /
      "airhealth-session-journal-test.txt";
}

void test_in_memory_journal_round_trip() {
  InMemorySessionJournalStore store;
  const auto entry = make_session_journal_entry(make_result(), true);
  store.save(entry);

  const auto loaded = store.load();
  expect(loaded.ok(), "in-memory session journal should round-trip");
  expect(loaded.entry.result.session_id == "session-21",
         "journal should retain the session id");
  expect(loaded.entry.unreconciled,
         "journal should preserve the unreconciled flag");
}

void test_file_journal_survives_reboot() {
  const auto path = make_journal_path();
  std::filesystem::remove(path);

  const auto entry = make_session_journal_entry(make_result(), true);
  {
    FileSessionJournalStore first_store(path.string());
    first_store.save(entry);
  }

  FileSessionJournalStore rebooted_store(path.string());
  const auto loaded = rebooted_store.load();
  expect(loaded.ok(), "file journal should survive reboot");
  expect(loaded.entry.result.session_id == "session-21",
         "rebooted journal should retain the session id");
  expect(loaded.entry.result.terminal_reason == "fat_summary_ready",
         "rebooted journal should retain the terminal summary");
  expect(loaded.entry.result.algorithm_version == "alg-fat-1.2.0",
         "rebooted journal should retain the algorithm version");

  std::filesystem::remove(path);
}

void test_corruption_is_detected() {
  const auto path = make_journal_path();
  std::filesystem::remove(path);

  {
    FileSessionJournalStore store(path.string());
    store.save(make_session_journal_entry(make_result(), true));
  }

  std::ofstream output(path, std::ios::trunc);
  output << "corrupt\n";
  output.close();

  FileSessionJournalStore store(path.string());
  const auto loaded = store.load();
  expect(!loaded.ok(), "corrupt journal should not load successfully");
  expect(session_journal_error_to_string(loaded.error) == "corrupt",
         "corrupt journal should emit a stable corruption error");

  std::filesystem::remove(path);
}

void test_not_found_is_stable() {
  const auto path = make_journal_path();
  std::filesystem::remove(path);

  FileSessionJournalStore store(path.string());
  const auto loaded = store.load();
  expect(!loaded.ok(), "missing journal should not load successfully");
  expect(loaded.error == SessionJournalError::NotFound,
         "missing journal should map to not_found");
}

void test_journal_stores_only_terminal_summary_fields() {
  const auto path = make_journal_path();
  std::filesystem::remove(path);

  {
    FileSessionJournalStore store(path.string());
    store.save(make_session_journal_entry(make_result(), true));
  }

  std::ifstream input(path);
  std::string contents(
      (std::istreambuf_iterator<char>(input)),
      std::istreambuf_iterator<char>()
  );

  expect(contents.find("raw_sensor_trace") == std::string::npos,
         "journal should not store raw sensor trace content");
  expect(contents.find("fat_summary_ready") != std::string::npos,
         "journal should store terminal summary fields");

  std::filesystem::remove(path);
}

void test_replay_returns_matching_session_result() {
  InMemorySessionJournalStore store;
  store.save(make_session_journal_entry(make_result(), true));

  SessionReplayService replay(store);
  const auto result = replay.query_by_session_id("session-21");
  expect(result.ok(), "replay query should return the stored session");
  expect(result.replayed_result.terminal_reason == "fat_summary_ready",
         "replay query should return the stored terminal payload");
}

void test_replay_rejects_unknown_session_id() {
  InMemorySessionJournalStore store;
  store.save(make_session_journal_entry(make_result(), true));

  SessionReplayService replay(store);
  const auto result = replay.query_by_session_id("session-missing");
  expect(!result.ok(), "replay query should reject missing session ids");
  expect(session_replay_error_to_string(result.error) == "session_id_mismatch",
         "missing session ids should map to a stable mismatch error");
}

void test_acknowledgment_tombstones_replayed_session() {
  InMemorySessionJournalStore store;
  store.save(make_session_journal_entry(make_result(), true));

  SessionReplayService replay(store);
  expect(replay.acknowledge_session_id("session-21") ==
             airhealth::fw::SessionReplayError::None,
         "ack should clear a matching session replay record");

  const auto replay_after_ack = replay.query_by_session_id("session-21");
  expect(!replay_after_ack.ok(), "acknowledged session should not replay again");
  expect(session_replay_error_to_string(replay_after_ack.error) == "not_found",
         "ack should tombstone the journal entry");
}

}  // namespace

int main() {
  try {
    test_in_memory_journal_round_trip();
    test_file_journal_survives_reboot();
    test_corruption_is_detected();
    test_not_found_is_stable();
    test_journal_stores_only_terminal_summary_fields();
    test_replay_returns_matching_session_result();
    test_replay_rejects_unknown_session_id();
    test_acknowledgment_tombstones_replayed_session();
  } catch (const std::exception& error) {
    std::cerr << "session_journal_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "session_journal_test passed\n";
  return EXIT_SUCCESS;
}
