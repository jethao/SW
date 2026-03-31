import Foundation

struct FatMeasurementResultSurface {
    let finalDeltaPercent: Int
    let bestDeltaPercent: Int
    let readingCount: Int
    let targetDeltaPercent: Int
    let finalDeltaLabel: String
    let bestDeltaLabel: String
    let progressLabel: String
    let goalStatusLabel: String
}

enum FatMeasurementFlowStep {
    case preparing
    case coaching
    case reading
    case finishPending
    case complete
    case failed
    case canceled
}

struct FatMeasurementFlowState {
    let activeSession: MeasurementSessionState?
    let readingCount: Int
    let targetDeltaPercent: Int
    let currentDeltaPercent: Int?
    let bestDeltaPercent: Int?
    let latestResult: FatMeasurementResultSurface?
    let recoveryMessage: String?
    let finishRequested: Bool

    init(
        activeSession: MeasurementSessionState? = nil,
        readingCount: Int = 0,
        targetDeltaPercent: Int = 18,
        currentDeltaPercent: Int? = nil,
        bestDeltaPercent: Int? = nil,
        latestResult: FatMeasurementResultSurface? = nil,
        recoveryMessage: String? = nil,
        finishRequested: Bool = false
    ) {
        self.activeSession = activeSession
        self.readingCount = readingCount
        self.targetDeltaPercent = targetDeltaPercent
        self.currentDeltaPercent = currentDeltaPercent
        self.bestDeltaPercent = bestDeltaPercent
        self.latestResult = latestResult
        self.recoveryMessage = recoveryMessage
        self.finishRequested = finishRequested
    }

    var step: FatMeasurementFlowStep {
        if activeSession == nil {
            return .preparing
        }
        if activeSession?.phase == .failed {
            return .failed
        }
        if activeSession?.phase == .canceled {
            return .canceled
        }
        if activeSession?.phase == .complete {
            return .complete
        }
        if finishRequested {
            return .finishPending
        }
        if readingCount == 0 {
            return .coaching
        }
        return .reading
    }

    var baselineLocked: Bool {
        readingCount > 0
    }
}

enum FatMeasurementFlowCoordinator {
    static func start(
        state: FatMeasurementFlowState,
        sessionID: String
    ) -> FatMeasurementFlowState {
        let session = MeasurementSessionCoordinator.reduce(
            state: MeasurementSessionState.begin(
                sessionID: sessionID,
                feature: .fatBurning
            ),
            event: .warmupStarted
        )
        return FatMeasurementFlowState(
            activeSession: session,
            readingCount: 0,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: nil,
            bestDeltaPercent: nil,
            latestResult: nil,
            recoveryMessage: nil,
            finishRequested: false
        )
    }

    static func completeCoachingWithFirstReading(
        state: FatMeasurementFlowState,
        deltaPercent: Int
    ) -> FatMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }
        let measuringSession = MeasurementSessionCoordinator.reduce(
            state: session,
            event: .measurementStarted
        )
        return FatMeasurementFlowState(
            activeSession: measuringSession,
            readingCount: 1,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: deltaPercent,
            bestDeltaPercent: deltaPercent,
            latestResult: nil,
            recoveryMessage: nil,
            finishRequested: false
        )
    }

    static func recordReading(
        state: FatMeasurementFlowState,
        deltaPercent: Int
    ) -> FatMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }
        return FatMeasurementFlowState(
            activeSession: session,
            readingCount: state.readingCount + 1,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: deltaPercent,
            bestDeltaPercent: max(state.bestDeltaPercent ?? deltaPercent, deltaPercent),
            latestResult: state.latestResult,
            recoveryMessage: nil,
            finishRequested: false
        )
    }

    static func requestFinish(state: FatMeasurementFlowState) -> FatMeasurementFlowState {
        guard let session = state.activeSession, state.readingCount > 0 else {
            return state
        }
        return FatMeasurementFlowState(
            activeSession: session,
            readingCount: state.readingCount,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: state.currentDeltaPercent,
            bestDeltaPercent: state.bestDeltaPercent,
            latestResult: state.latestResult,
            recoveryMessage: nil,
            finishRequested: true
        )
    }

    static func complete(
        state: FatMeasurementFlowState,
        finalDeltaPercent: Int
    ) -> FatMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }
        let bestDelta = max(state.bestDeltaPercent ?? finalDeltaPercent, finalDeltaPercent)
        let resultToken = "\(session.sessionID)-fat-\(bestDelta)-\(finalDeltaPercent)"
        let completedSession = MeasurementSessionCoordinator.reduce(
            state: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .terminalReadingAvailable(
                    MeasurementTerminalSummary(resultToken: resultToken)
                )
            ),
            event: .terminalReadingConfirmed
        )
        let resultSurface = FatMeasurementResultSurface(
            finalDeltaPercent: finalDeltaPercent,
            bestDeltaPercent: bestDelta,
            readingCount: state.readingCount,
            targetDeltaPercent: state.targetDeltaPercent,
            finalDeltaLabel: "Final Fat Burn Delta +\(finalDeltaPercent)%",
            bestDeltaLabel: "Best Fat Burn Delta +\(bestDelta)%",
            progressLabel: "\(state.readingCount) valid readings • target +\(state.targetDeltaPercent)%",
            goalStatusLabel: bestDelta >= state.targetDeltaPercent
                ? "Goal reached during this session."
                : "Keep reading until your best delta reaches +\(state.targetDeltaPercent)%."
        )
        return FatMeasurementFlowState(
            activeSession: completedSession,
            readingCount: state.readingCount,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: finalDeltaPercent,
            bestDeltaPercent: bestDelta,
            latestResult: resultSurface,
            recoveryMessage: nil,
            finishRequested: false
        )
    }

    static func fail(
        state: FatMeasurementFlowState,
        reasonCode: String,
        recoveryMessage: String
    ) -> FatMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }
        return FatMeasurementFlowState(
            activeSession: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .measurementFailed(reasonCode: reasonCode)
            ),
            readingCount: state.readingCount,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: state.currentDeltaPercent,
            bestDeltaPercent: state.bestDeltaPercent,
            latestResult: nil,
            recoveryMessage: recoveryMessage,
            finishRequested: false
        )
    }

    static func cancel(state: FatMeasurementFlowState) -> FatMeasurementFlowState {
        guard let session = state.activeSession else {
            return state
        }
        return FatMeasurementFlowState(
            activeSession: MeasurementSessionCoordinator.reduce(
                state: session,
                event: .sessionCanceled(reasonCode: "user_canceled")
            ),
            readingCount: state.readingCount,
            targetDeltaPercent: state.targetDeltaPercent,
            currentDeltaPercent: state.currentDeltaPercent,
            bestDeltaPercent: state.bestDeltaPercent,
            latestResult: nil,
            recoveryMessage: "Session canceled. No fat-burning summary was saved.",
            finishRequested: false
        )
    }
}
