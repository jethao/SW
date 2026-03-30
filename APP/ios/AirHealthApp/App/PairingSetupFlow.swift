import Foundation

enum PairingStep: String {
    case permissionPrimer = "permission_primer"
    case permissionDenied = "permission_denied"
    case discovering = "discovering"
    case deviceDiscovered = "device_discovered"
    case connecting = "connecting"
    case incompatible = "incompatible"
    case notReady = "not_ready"
    case connected = "connected"
    case claiming = "claiming"
    case claimFailed = "claim_failed"
    case modeSelection = "mode_selection"
    case setupComplete = "setup_complete"
    case timeout = "timeout"
}

enum PairingRecoveryAction: String {
    case surfaced = "surfaced"
    case retry = "retry"
    case exit = "exit"
}

struct PairingRecoveryEvent {
    let failureStep: String
    let recoveryAction: String

    var payload: [String: String] {
        [
            "failure_step": failureStep,
            "recovery_action": recoveryAction,
        ]
    }
}

enum SetupMode: String, CaseIterable {
    case oralHealth = "oral_health"
    case fatBurning = "fat_burning"

    var title: String {
        switch self {
        case .oralHealth:
            return "Oral Health"
        case .fatBurning:
            return "Fat Burning"
        }
    }
}

struct DiscoveredDeviceSummary {
    let name: String
    let protocolVersion: String
    let signalLabel: String
}

struct PairingFlowState {
    let step: PairingStep
    let discoveredDevice: DiscoveredDeviceSummary?
    let recoveryMessage: String?
    let claimOwnerLabel: String?
    let selectedMode: SetupMode?

    static func permissionPrimer() -> PairingFlowState {
        PairingFlowState(step: .permissionPrimer, discoveredDevice: nil, recoveryMessage: nil, claimOwnerLabel: nil, selectedMode: nil)
    }

    static func discovering() -> PairingFlowState {
        PairingFlowState(step: .discovering, discoveredDevice: nil, recoveryMessage: nil, claimOwnerLabel: nil, selectedMode: nil)
    }

    static func defaultDevice() -> DiscoveredDeviceSummary {
        DiscoveredDeviceSummary(
            name: "AirHealth Breath Sensor",
            protocolVersion: "consumer-v2",
            signalLabel: "Strong"
        )
    }
}
