import Foundation

private let oralBaselineRequiredSessions = 3

struct OralHistoryViewItem {
    let sessionID: String
    let title: String
    let detail: String
    let syncLabel: String
    let progressLabel: String
}

struct OralHistorySurfaceState {
    let baselineProgressLabel: String
    let progressDetail: String
    let latestStatusLabel: String?
    let items: [OralHistoryViewItem]
}

struct FatHistoryViewItem {
    let sessionID: String
    let title: String
    let detail: String
    let syncLabel: String
    let finalDeltaLabel: String
    let bestDeltaLabel: String
}

struct FatHistorySurfaceState {
    let latestFinalDeltaLabel: String?
    let bestDeltaLabel: String?
    let pendingCount: Int
    let syncedCount: Int
    let items: [FatHistoryViewItem]
}

extension SessionHistoryStoreState {
    func oralHistorySurface() -> OralHistorySurfaceState {
        let oralRecords = records
            .filter { $0.feature == .oralHealth }
            .sorted { $0.recordedAtEpochMillis > $1.recordedAtEpochMillis }

        let chronologicalProgress = Dictionary(
            uniqueKeysWithValues: oralRecords
                .sorted { $0.recordedAtEpochMillis < $1.recordedAtEpochMillis }
                .enumerated()
                .map { index, record in
                    let label = index < oralBaselineRequiredSessions
                        ? "Baseline session \(index + 1) of \(oralBaselineRequiredSessions)"
                        : "Post-baseline trend check"
                    return (record.sessionID, label)
                }
        )

        let items = oralRecords.map { record in
            OralHistoryViewItem(
                sessionID: record.sessionID,
                title: record.summaryTitle,
                detail: record.summaryDetail,
                syncLabel: record.syncState.displayLabel,
                progressLabel: chronologicalProgress[record.sessionID] ?? "Baseline session"
            )
        }

        let completedCount = min(oralRecords.count, oralBaselineRequiredSessions)
        let baselineProgressLabel = completedCount < oralBaselineRequiredSessions
            ? "Baseline building \(completedCount)/\(oralBaselineRequiredSessions)"
            : "Baseline ready"
        let progressDetail = completedCount < oralBaselineRequiredSessions
            ? "\(oralBaselineRequiredSessions - completedCount) more oral sessions are needed before post-baseline trend views unlock."
            : "Baseline is established. New oral sessions now extend the post-baseline trend view."

        return OralHistorySurfaceState(
            baselineProgressLabel: baselineProgressLabel,
            progressDetail: progressDetail,
            latestStatusLabel: items.first?.syncLabel,
            items: items
        )
    }

    func fatHistorySurface() -> FatHistorySurfaceState {
        let fatRecords = records
            .filter { $0.feature == .fatBurning }
            .sorted { $0.recordedAtEpochMillis > $1.recordedAtEpochMillis }

        var runningBestDelta = 0
        let items = fatRecords
            .sorted { $0.recordedAtEpochMillis < $1.recordedAtEpochMillis }
            .map { record -> FatHistoryViewItem in
                let finalDelta = deriveFinalDelta(from: record.resultToken)
                runningBestDelta = max(runningBestDelta, finalDelta)
                return FatHistoryViewItem(
                    sessionID: record.sessionID,
                    title: record.summaryTitle,
                    detail: record.summaryDetail,
                    syncLabel: record.syncState.displayLabel,
                    finalDeltaLabel: "Final delta +\(finalDelta)",
                    bestDeltaLabel: "Best delta +\(runningBestDelta)"
                )
            }
            .reversed()

        return FatHistorySurfaceState(
            latestFinalDeltaLabel: items.first?.finalDeltaLabel,
            bestDeltaLabel: items.map(\.bestDeltaLabel).max(by: { labelScore($0) < labelScore($1) }),
            pendingCount: fatRecords.filter { $0.syncState == .pending }.count,
            syncedCount: fatRecords.filter { $0.syncState == .synced }.count,
            items: Array(items)
        )
    }
}

private extension SessionSyncState {
    var displayLabel: String {
        switch self {
        case .pending:
            return "Pending sync"
        case .synced:
            return "Synced"
        }
    }
}

private func deriveFinalDelta(from resultToken: String) -> Int {
    resultToken.unicodeScalars.reduce(0) { accumulator, scalar in
        ((accumulator * 31) + Int(scalar.value)) % 21
    } + 5
}

private func labelScore(_ label: String) -> Int {
    Int(label.split(separator: "+").last ?? "0") ?? 0
}
