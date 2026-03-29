#include "airhealth/fw/session.hpp"

#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::RoutingMetadata;
using airhealth::fw::SessionContext;
using airhealth::fw::SessionMode;
using airhealth::fw::SessionOrchestrator;
using airhealth::fw::SessionState;
using airhealth::fw::TerminalDisposition;
using airhealth::fw::session_error_to_string;
using airhealth::fw::session_state_to_string;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

SessionContext make_context(std::string session_id) {
  return SessionContext {
      .session_id = std::move(session_id),
      .mode = SessionMode::OralHealth,
      .routing =
          RoutingMetadata {
              .hardware_profile = "hw-a",
              .voc_profile = "voc-low",
          },
  };
}

void test_start_session_from_ready() {
  SessionOrchestrator orchestrator;

  const auto result = orchestrator.start_session(make_context("session-1"));

  expect(result.ok(), "start_session should succeed from ready");
  expect(result.snapshot.state == SessionState::Active,
         "successful start should move to active");
  expect(result.snapshot.has_context, "active session should retain context");
  expect(result.snapshot.context.session_id == "session-1",
         "active context should retain session_id");
}

void test_reject_empty_or_concurrent_start() {
  SessionOrchestrator orchestrator;

  const auto empty = orchestrator.start_session(make_context(""));
  expect(!empty.ok(), "empty session ids should be rejected");
  expect(session_error_to_string(empty.error) == "empty_session_id",
         "empty session ids should map to a stable error");

  const auto first = orchestrator.start_session(make_context("session-2"));
  expect(first.ok(), "first session start should succeed");

  const auto second = orchestrator.start_session(make_context("session-3"));
  expect(!second.ok(), "concurrent start should be rejected");
  expect(session_error_to_string(second.error) == "session_already_active",
         "concurrent starts should return a deterministic error");
  expect(second.snapshot.context.session_id == "session-2",
         "rejected start should preserve the current active session");
}

void test_terminal_transitions_and_reset() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context("session-4")).ok(),
         "session should start before terminal transitions");

  const auto complete =
      orchestrator.finish_active_session(TerminalDisposition::Complete);
  expect(complete.ok(), "active session should complete cleanly");
  expect(session_state_to_string(complete.snapshot.state) == "complete",
         "terminal disposition should map to complete");

  const auto reconciled = orchestrator.reconcile_terminal_session();
  expect(reconciled.ok(), "terminal session should reconcile");
  expect(reconciled.snapshot.state == SessionState::Reconciled,
         "reconcile should move terminal sessions to reconciled");

  const auto reset = orchestrator.reset_to_ready();
  expect(reset.ok(), "reconciled session should reset to ready");
  expect(reset.snapshot.state == SessionState::Ready,
         "reset should move the orchestrator back to ready");
  expect(!reset.snapshot.has_context,
         "reset should clear the previous session context");
}

void test_routing_metadata_survives_terminal_path() {
  SessionOrchestrator orchestrator;
  expect(orchestrator.start_session(make_context("session-5")).ok(),
         "session should start before routing preservation checks");

  const auto interrupted =
      orchestrator.finish_active_session(TerminalDisposition::Interrupted);
  expect(interrupted.ok(), "interrupt transition should succeed from active");
  expect(interrupted.snapshot.context.routing.hardware_profile == "hw-a",
         "routing metadata should survive terminal transitions");
  expect(interrupted.snapshot.context.routing.voc_profile == "voc-low",
         "routing metadata should preserve voc routing");
}

void test_illegal_terminal_transition_is_rejected() {
  SessionOrchestrator orchestrator;

  const auto invalid =
      orchestrator.finish_active_session(TerminalDisposition::Failed);
  expect(!invalid.ok(), "terminal transitions should fail from ready");
  expect(session_error_to_string(invalid.error) == "invalid_transition",
         "illegal terminal transitions should use a stable error");
}

}  // namespace

int main() {
  try {
    test_start_session_from_ready();
    test_reject_empty_or_concurrent_start();
    test_terminal_transitions_and_reset();
    test_routing_metadata_survives_terminal_path();
    test_illegal_terminal_transition_is_rejected();
  } catch (const std::exception& error) {
    std::cerr << "session_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "session_test passed\n";
  return EXIT_SUCCESS;
}
