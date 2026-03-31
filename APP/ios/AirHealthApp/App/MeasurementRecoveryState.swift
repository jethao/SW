import Foundation

enum MeasurementRecoveryStage {
    case interrupted
    case reconnecting
    case recovered
    case failed
}

struct MeasurementRecoveryState {
    let feature: FeatureKind
    let sessionID: String
    let stage: MeasurementRecoveryStage
    let replayRequired: Bool
    let statusMessage: String
    let recoveredResultToken: String?

    init(
        feature: FeatureKind,
        sessionID: String,
        stage: MeasurementRecoveryStage,
        replayRequired: Bool,
        statusMessage: String,
        recoveredResultToken: String? = nil
    ) {
        self.feature = feature
        self.sessionID = sessionID
        self.stage = stage
        self.replayRequired = replayRequired
        self.statusMessage = statusMessage
        self.recoveredResultToken = recoveredResultToken
    }

    static func interrupted(
        feature: FeatureKind,
        sessionID: String
    ) -> MeasurementRecoveryState {
        MeasurementRecoveryState(
            feature: feature,
            sessionID: sessionID,
            stage: .interrupted,
            replayRequired: true,
            statusMessage: "Connection lost. Reconnect to query the device for the terminal result before deciding whether this session failed."
        )
    }
}
