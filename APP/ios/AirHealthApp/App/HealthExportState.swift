import Foundation

enum HealthExportPlatform: String {
    case appleHealth = "apple_health"
    case healthConnect = "health_connect"

    var title: String {
        switch self {
        case .appleHealth:
            return "Apple Health"
        case .healthConnect:
            return "Health Connect"
        }
    }
}

enum ExportAuditStatus: String {
    case succeeded = "succeeded"
    case failed = "failed"
}

enum HealthExportPermissionState: String {
    case unknown = "unknown"
    case granted = "granted"
    case denied = "denied"
}

struct CompletedSummaryExportPayload {
    let platform: HealthExportPlatform
    let sessionID: String
    let feature: String
    let resultToken: String
    let recordedAtEpochMillis: Int64
    let summaryTitle: String
    let summaryDetail: String

    var wireMap: [String: String] {
        [
            "platform": platform.rawValue,
            "session_id": sessionID,
            "feature": feature,
            "result_token": resultToken,
            "recorded_at_epoch_millis": String(recordedAtEpochMillis),
            "summary_title": summaryTitle,
            "summary_detail": summaryDetail,
        ]
    }
}

struct ExportAuditRecord {
    let auditID: String
    let feature: FeatureKind
    let sessionID: String
    let platform: HealthExportPlatform
    let status: ExportAuditStatus
    let recordedAtEpochMillis: Int64
    let exportedResultToken: String
    let failureReason: String?

    var statusLabel: String {
        switch status {
        case .succeeded:
            return "Exported"
        case .failed:
            return "Export failed"
        }
    }
}

struct ExportAuditSurfaceState {
    let permissionState: HealthExportPermissionState
    let latestStatusLabel: String?
    let latestPlatformTitle: String?
    let latestFailureReason: String?
    let records: [ExportAuditRecord]
}

struct HealthExportPermissionStoreState {
    let byPlatform: [HealthExportPlatform: HealthExportPermissionState]

    init(byPlatform: [HealthExportPlatform: HealthExportPermissionState] = [:]) {
        self.byPlatform = byPlatform
    }

    func permission(for platform: HealthExportPlatform) -> HealthExportPermissionState {
        byPlatform[platform] ?? .unknown
    }

    func setPermission(
        _ permissionState: HealthExportPermissionState,
        for platform: HealthExportPlatform
    ) -> HealthExportPermissionStoreState {
        HealthExportPermissionStoreState(byPlatform: byPlatform.merging([platform: permissionState]) { _, new in new })
    }
}

struct ExportAuditStoreState {
    let records: [ExportAuditRecord]

    init(records: [ExportAuditRecord] = []) {
        self.records = records.sorted { $0.recordedAtEpochMillis > $1.recordedAtEpochMillis }
    }

    func append(_ record: ExportAuditRecord) -> ExportAuditStoreState {
        ExportAuditStoreState(records: [record] + records)
    }

    func latestRecord(for feature: FeatureKind) -> ExportAuditRecord? {
        records.first { $0.feature == feature }
    }

    func surface(
        for feature: FeatureKind,
        permissionState: HealthExportPermissionState
    ) -> ExportAuditSurfaceState {
        let featureRecords = records.filter { $0.feature == feature }.sorted { $0.recordedAtEpochMillis > $1.recordedAtEpochMillis }
        let latest = featureRecords.first
        return ExportAuditSurfaceState(
            permissionState: permissionState,
            latestStatusLabel: latest?.statusLabel,
            latestPlatformTitle: latest?.platform.title,
            latestFailureReason: latest?.failureReason,
            records: featureRecords
        )
    }
}

enum HealthExportAdapter {
    static func payloadFor(
        record: PersistedSessionSummaryRecord,
        platform: HealthExportPlatform
    ) -> CompletedSummaryExportPayload {
        CompletedSummaryExportPayload(
            platform: platform,
            sessionID: record.sessionID,
            feature: record.feature.rawValue,
            resultToken: record.resultToken,
            recordedAtEpochMillis: record.recordedAtEpochMillis,
            summaryTitle: record.summaryTitle,
            summaryDetail: record.summaryDetail
        )
    }
}
