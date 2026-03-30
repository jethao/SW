import Foundation

enum MeasurementSessionPhase: String {
    case ready = "ready"
    case warming = "warming"
    case measuring = "measuring"
    case paused = "paused"
    case failed = "failed"
    case canceled = "canceled"
    case complete = "complete"
}

struct MeasurementRecoveryMarker {
    let sessionID: String
    let lastStablePhase: MeasurementSessionPhase
    let replayRequired: Bool
}

struct MeasurementTerminalSummary {
    let resultToken: String
}

struct MeasurementSessionState {
    let sessionID: String
    let feature: FeatureKind
    let phase: MeasurementSessionPhase
    let recoveryMarker: MeasurementRecoveryMarker
    let terminalSummary: MeasurementTerminalSummary?
    let failureReason: String?
    let cancellationReason: String?
    let lastEventCode: String?

    static func begin(
        sessionID: String,
        feature: FeatureKind
    ) -> MeasurementSessionState {
        MeasurementSessionState(
            sessionID: sessionID,
            feature: feature,
            phase: .ready,
            recoveryMarker: MeasurementRecoveryMarker(
                sessionID: sessionID,
                lastStablePhase: .ready,
                replayRequired: false
            ),
            terminalSummary: nil,
            failureReason: nil,
            cancellationReason: nil,
            lastEventCode: "session_prepared"
        )
    }
}

enum MeasurementBleEvent {
    case warmupStarted
    case measurementStarted
    case measurementPaused
    case measurementResumed
    case terminalReadingAvailable(MeasurementTerminalSummary)
    case terminalReadingConfirmed
    case deviceDisconnected(replayRequired: Bool)
    case measurementFailed(reasonCode: String)
    case sessionCanceled(reasonCode: String)

    var code: String {
        switch self {
        case .warmupStarted:
            return "warmup_started"
        case .measurementStarted:
            return "measurement_started"
        case .measurementPaused:
            return "measurement_paused"
        case .measurementResumed:
            return "measurement_resumed"
        case .terminalReadingAvailable:
            return "terminal_reading_available"
        case .terminalReadingConfirmed:
            return "terminal_reading_confirmed"
        case .deviceDisconnected:
            return "device_disconnected"
        case .measurementFailed:
            return "measurement_failed"
        case .sessionCanceled:
            return "session_canceled"
        }
    }
}

enum MeasurementSessionCoordinator {
    static func reduce(
        state: MeasurementSessionState,
        event: MeasurementBleEvent
    ) -> MeasurementSessionState {
        switch event {
        case .warmupStarted:
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .warming,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: .warming,
                    replayRequired: false
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case .measurementStarted:
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .measuring,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: .measuring,
                    replayRequired: false
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case .measurementPaused:
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .paused,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: .paused,
                    replayRequired: false
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case .measurementResumed:
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .measuring,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: .measuring,
                    replayRequired: false
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case let .terminalReadingAvailable(summary):
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: state.phase,
                recoveryMarker: state.recoveryMarker,
                terminalSummary: summary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case .terminalReadingConfirmed:
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .complete,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: .complete,
                    replayRequired: false
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case let .deviceDisconnected(replayRequired):
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .paused,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: state.recoveryMarker.lastStablePhase,
                    replayRequired: replayRequired
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case let .measurementFailed(reasonCode):
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .failed,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: state.recoveryMarker.lastStablePhase,
                    replayRequired: true
                ),
                terminalSummary: state.terminalSummary,
                failureReason: reasonCode,
                cancellationReason: state.cancellationReason,
                lastEventCode: event.code
            )
        case let .sessionCanceled(reasonCode):
            return MeasurementSessionState(
                sessionID: state.sessionID,
                feature: state.feature,
                phase: .canceled,
                recoveryMarker: MeasurementRecoveryMarker(
                    sessionID: state.recoveryMarker.sessionID,
                    lastStablePhase: state.recoveryMarker.lastStablePhase,
                    replayRequired: false
                ),
                terminalSummary: state.terminalSummary,
                failureReason: state.failureReason,
                cancellationReason: reasonCode,
                lastEventCode: event.code
            )
        }
    }
}
