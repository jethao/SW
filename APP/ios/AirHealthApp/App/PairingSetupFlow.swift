import Foundation

enum PairingStep: String {
    case permissionPrimer = "permission_primer"
    case permissionDenied = "permission_denied"
    case discovering = "discovering"
    case deviceDiscovered = "device_discovered"
    case connecting = "connecting"
    case connected = "connected"
    case timeout = "timeout"
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

    static func permissionPrimer() -> PairingFlowState {
        PairingFlowState(step: .permissionPrimer, discoveredDevice: nil, recoveryMessage: nil)
    }

    static func discovering() -> PairingFlowState {
        PairingFlowState(step: .discovering, discoveredDevice: nil, recoveryMessage: nil)
    }

    static func defaultDevice() -> DiscoveredDeviceSummary {
        DiscoveredDeviceSummary(
            name: "AirHealth Breath Sensor",
            protocolVersion: "consumer-v2",
            signalLabel: "Strong"
        )
    }
}
