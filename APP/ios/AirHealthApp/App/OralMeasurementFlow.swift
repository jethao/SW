import Foundation

private let oralBaselineRequiredSessions = 5

struct OralBaselineProgress {
    let completedValidSessions: Int
    let requiredValidSessions: Int

    init(
        completedValidSessions: Int = 0,
        requiredValidSessions: Int = oralBaselineRequiredSessions
    ) {
        self.completedValidSessions = completedValidSessions
        self.requiredValidSessions = requiredValidSessions
    }

    var clampedCompletedSessions: Int {
        min(max(completedValidSessions, 0), requiredValidSessions)
    }

    var progressLabel: String {
        "\(clampedCompletedSessions)/\(requiredValidSessions) baseline sessions"
    }

    var detailLabel: String {
        if clampedCompletedSessions >= requiredValidSessions {
            return "Baseline locked. Future oral scores compare against your established average."
        }

        return "Complete \(requiredValidSessions - clampedCompletedSessions) more valid oral sessions to lock your baseline."
    }
}

struct OralMeasurementResultSurface {
    let oralHealthScore: Int
    let scoreLabel: String
    let baselineProgressLabel: String
    let baselineDetail: String
    let completionLabel: String
}

enum OralMeasurementFlowStep {
    case preparing
    case warming
    case measuring
    case complete
    case failed
    case canceled
}

struct OralMeasurementFlowState {
    let activeSession: MeasurementSessionState?
    let baselineProgress: OralBaselineProgress
    let latestResult: OralMeasurementResultSurface?
    let recoveryMessage: String?

    init(
        activeSession: MeasurementSessionState? = nil,
        baselineProgress: OralBaselineProgress = OralBaselineProgress(),
        latestResult: OralMeasurementResultSurface? = nil,
        recoveryMessage: String? = nil
    ) {
        self.activeSession = activeSession
        self.baselineProgress = baselineProgress
        self.latestResult = latestResult
        self.recoveryMessage = recoveryMessage
    }

    var step: OralMeasurementFlowStep {
        switch activeSession?.phase {
        case nil:
            return .preparing
        case .ready, .warming:
            return .warming
        case .measuring, .paused:
            return .measuring
        case .complete:
            return .complete
        case .failed:
            return .failed
        case .canceled:
            return .canceled
        }
    }
}

enum OralMeasurementFlowCoordinator {
    static func start(
        state: OralMeasurementFlowState,
        sessionID: String
    ) -> OralMeasurementFlowState {
        let session = MeasurementSessionCoordinator.reduce(
            state: MeasurementSessionState.begin(
                sessionID: sessionID,
                feature: .oralHealth
            ),
            event: .warmupStarted
        )
        return OralMeasurementFlowState(
            activeSession: session,
            baselineProgress: state.baselineProgress,
            latestResult: nil,
            recoveryMessage: nil
        )
    }

    static func markWarmupPassed(state: OralMeasurementFlowState) -> OralMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }

        return OralMeasurementFlowState(
            activeSession: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .measurementStarted
            ),
            baselineProgress: state.baselineProgress,
            latestResult: state.latestResult,
            recoveryMessage: nil
        )
    }

    static func markWarmupFailed(state: OralMeasurementFlowState) -> OralMeasurementFlowState {
        fail(
            state: state,
            reasonCode: "warmup_failed",
            recoveryMessage: "Warm-up did not complete cleanly. Retry before recording an oral score."
        )
    }

    static func markInvalidSample(state: OralMeasurementFlowState) -> OralMeasurementFlowState {
        fail(
            state: state,
            reasonCode: "invalid_sample",
            recoveryMessage: "The sample was not valid. Retry without saving a completed result."
        )
    }

    static func cancel(state: OralMeasurementFlowState) -> OralMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }

        return OralMeasurementFlowState(
            activeSession: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .sessionCanceled(reasonCode: "user_canceled")
            ),
            baselineProgress: state.baselineProgress,
            latestResult: nil,
            recoveryMessage: "Session canceled. No completed oral result was saved."
        )
    }

    static func complete(
        state: OralMeasurementFlowState,
        oralHealthScore: Int
    ) -> OralMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }

        let resultToken = "\(session.sessionID)-score-\(oralHealthScore)"
        let completedSession = MeasurementSessionCoordinator.reduce(
            state: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .terminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken: resultToken)
                )
            ),
            event: .terminalReadingConfirmed
        )
        let nextCompletedCount = min(
            state.baselineProgress.completedValidSessions + 1,
            state.baselineProgress.requiredValidSessions
        )
        let nextBaselineProgress = OralBaselineProgress(
            completedValidSessions: nextCompletedCount,
            requiredValidSessions: state.baselineProgress.requiredValidSessions
        )
        let resultSurface = OralMeasurementResultSurface(
            oralHealthScore: oralHealthScore,
            scoreLabel: "Oral Health Score \(oralHealthScore)",
            baselineProgressLabel: nextBaselineProgress.progressLabel,
            baselineDetail: nextBaselineProgress.detailLabel,
            completionLabel: "Valid oral sample confirmed by the device."
        )

        return OralMeasurementFlowState(
            activeSession: completedSession,
            baselineProgress: nextBaselineProgress,
            latestResult: resultSurface,
            recoveryMessage: nil
        )
    }

    private static func fail(
        state: OralMeasurementFlowState,
        reasonCode: String,
        recoveryMessage: String
    ) -> OralMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }

        return OralMeasurementFlowState(
            activeSession: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .measurementFailed(reasonCode: reasonCode)
            ),
            baselineProgress: state.baselineProgress,
            latestResult: nil,
            recoveryMessage: recoveryMessage
        )
    }
}
