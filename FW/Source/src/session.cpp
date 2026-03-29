#include "airhealth/fw/session.hpp"

#include <stdexcept>

namespace airhealth::fw {

namespace {

SessionState map_terminal_disposition(TerminalDisposition disposition) {
  switch (disposition) {
    case TerminalDisposition::Complete:
      return SessionState::Complete;
    case TerminalDisposition::Canceled:
      return SessionState::Canceled;
    case TerminalDisposition::Failed:
      return SessionState::Failed;
    case TerminalDisposition::Interrupted:
      return SessionState::Interrupted;
  }

  throw std::invalid_argument("Unsupported terminal disposition");
}

bool is_terminal_state(SessionState state) {
  switch (state) {
    case SessionState::Complete:
    case SessionState::Canceled:
    case SessionState::Failed:
    case SessionState::Interrupted:
    case SessionState::Reconciled:
      return true;
    case SessionState::Ready:
    case SessionState::Active:
      return false;
  }

  throw std::invalid_argument("Unsupported session state");
}

}  // namespace

SessionSnapshot SessionOrchestrator::snapshot() const {
  return snapshot_;
}

SessionResult SessionOrchestrator::start_session(const SessionContext& context) {
  if (context.session_id.empty()) {
    return SessionResult {
        .error = SessionError::EmptySessionId,
        .snapshot = snapshot_,
    };
  }

  if (snapshot_.state != SessionState::Ready) {
    return SessionResult {
        .error = SessionError::SessionAlreadyActive,
        .snapshot = snapshot_,
    };
  }

  snapshot_ = SessionSnapshot {
      .state = SessionState::Active,
      .has_context = true,
      .context = context,
  };

  return SessionResult {
      .error = SessionError::None,
      .snapshot = snapshot_,
  };
}

SessionResult SessionOrchestrator::finish_active_session(
    TerminalDisposition disposition
) {
  if (snapshot_.state != SessionState::Active || !snapshot_.has_context) {
    return SessionResult {
        .error = SessionError::InvalidTransition,
        .snapshot = snapshot_,
    };
  }

  snapshot_.state = map_terminal_disposition(disposition);
  return SessionResult {
      .error = SessionError::None,
      .snapshot = snapshot_,
  };
}

SessionResult SessionOrchestrator::reconcile_terminal_session() {
  if (!is_terminal_state(snapshot_.state) || !snapshot_.has_context) {
    return SessionResult {
        .error = SessionError::InvalidTransition,
        .snapshot = snapshot_,
    };
  }

  snapshot_.state = SessionState::Reconciled;
  return SessionResult {
      .error = SessionError::None,
      .snapshot = snapshot_,
  };
}

SessionResult SessionOrchestrator::reset_to_ready() {
  if (!is_terminal_state(snapshot_.state)) {
    return SessionResult {
        .error = SessionError::InvalidTransition,
        .snapshot = snapshot_,
    };
  }

  snapshot_ = SessionSnapshot {
      .state = SessionState::Ready,
      .has_context = false,
      .context = {},
  };

  return SessionResult {
      .error = SessionError::None,
      .snapshot = snapshot_,
  };
}

std::string session_state_to_string(SessionState state) {
  switch (state) {
    case SessionState::Ready:
      return "ready";
    case SessionState::Active:
      return "active";
    case SessionState::Complete:
      return "complete";
    case SessionState::Canceled:
      return "canceled";
    case SessionState::Failed:
      return "failed";
    case SessionState::Interrupted:
      return "interrupted";
    case SessionState::Reconciled:
      return "reconciled";
  }

  throw std::invalid_argument("Unsupported session state");
}

std::string session_error_to_string(SessionError error) {
  switch (error) {
    case SessionError::None:
      return "none";
    case SessionError::EmptySessionId:
      return "empty_session_id";
    case SessionError::SessionAlreadyActive:
      return "session_already_active";
    case SessionError::InvalidTransition:
      return "invalid_transition";
  }

  throw std::invalid_argument("Unsupported session error");
}

}  // namespace airhealth::fw
