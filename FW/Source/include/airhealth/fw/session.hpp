#pragma once

#include <string>

namespace airhealth::fw {

enum class SessionMode {
  OralHealth,
  FatBurning,
};

enum class SessionState {
  Ready,
  Active,
  Complete,
  Canceled,
  Failed,
  Interrupted,
  Reconciled,
};

enum class TerminalDisposition {
  Complete,
  Canceled,
  Failed,
  Interrupted,
};

enum class SessionError {
  None,
  EmptySessionId,
  SessionAlreadyActive,
  InvalidTransition,
};

struct RoutingMetadata {
  std::string hardware_profile;
  std::string voc_profile;
};

struct SessionContext {
  std::string session_id;
  SessionMode mode = SessionMode::OralHealth;
  RoutingMetadata routing {};
};

struct SessionSnapshot {
  SessionState state = SessionState::Ready;
  bool has_context = false;
  SessionContext context {};
};

struct SessionResult {
  SessionError error = SessionError::None;
  SessionSnapshot snapshot {};

  [[nodiscard]] bool ok() const {
    return error == SessionError::None;
  }
};

class SessionOrchestrator {
 public:
  [[nodiscard]] SessionSnapshot snapshot() const;

  [[nodiscard]] SessionResult start_session(const SessionContext& context);
  [[nodiscard]] SessionResult finish_active_session(
      TerminalDisposition disposition
  );
  [[nodiscard]] SessionResult reconcile_terminal_session();
  [[nodiscard]] SessionResult reset_to_ready();

 private:
  SessionSnapshot snapshot_ {};
};

[[nodiscard]] std::string session_state_to_string(SessionState state);
[[nodiscard]] std::string session_error_to_string(SessionError error);

}  // namespace airhealth::fw
