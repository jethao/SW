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

enum DeviceProtocolFamily {
    case consumer
    case factoryOnly
    case unauthorizedInternal
}

struct DiscoveredDeviceSummary {
    let name: String
    let protocolVersion: String
    let signalLabel: String
    let protocolFamily: DeviceProtocolFamily
    let internalStateCode: String?

    init(
        name: String,
        protocolVersion: String,
        signalLabel: String,
        protocolFamily: DeviceProtocolFamily = .consumer,
        internalStateCode: String? = nil
    ) {
        self.name = name
        self.protocolVersion = protocolVersion
        self.signalLabel = signalLabel
        self.protocolFamily = protocolFamily
        self.internalStateCode = internalStateCode
    }

    func consumerFacingProtocolLabel() -> String {
        switch protocolFamily {
        case .consumer:
            return protocolVersion
        case .factoryOnly, .unauthorizedInternal:
            return "Restricted"
        }
    }

    func isFactoryOnlyFamily() -> Bool {
        protocolFamily == .factoryOnly
    }

    func hasUnauthorizedInternalState() -> Bool {
        protocolFamily == .unauthorizedInternal || internalStateCode != nil
    }
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

    static func factoryOnlyDevice() -> DiscoveredDeviceSummary {
        DiscoveredDeviceSummary(
            name: "AirHealth Service Sensor",
            protocolVersion: "factory-v1",
            signalLabel: "Strong",
            protocolFamily: .factoryOnly
        )
    }

    static func unauthorizedInternalStateDevice() -> DiscoveredDeviceSummary {
        DiscoveredDeviceSummary(
            name: "AirHealth Breath Sensor",
            protocolVersion: "consumer-v2",
            signalLabel: "Strong",
            protocolFamily: .unauthorizedInternal,
            internalStateCode: "service_hold"
        )
    }
}
