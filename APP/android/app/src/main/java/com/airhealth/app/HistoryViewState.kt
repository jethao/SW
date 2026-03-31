package com.airhealth.app

private const val ORAL_BASELINE_REQUIRED_SESSIONS = 3

data class OralHistoryViewItem(
    val sessionId: String,
    val title: String,
    val detail: String,
    val syncLabel: String,
    val progressLabel: String,
)

data class OralHistorySurfaceState(
    val baselineProgressLabel: String,
    val progressDetail: String,
    val latestStatusLabel: String?,
    val items: List<OralHistoryViewItem>,
)

data class FatHistoryViewItem(
    val sessionId: String,
    val title: String,
    val detail: String,
    val syncLabel: String,
    val finalDeltaLabel: String,
    val bestDeltaLabel: String,
)

data class FatHistorySurfaceState(
    val latestFinalDeltaLabel: String?,
    val bestDeltaLabel: String?,
    val pendingCount: Int,
    val syncedCount: Int,
    val items: List<FatHistoryViewItem>,
)

fun SessionHistoryStoreState.oralHistorySurface(): OralHistorySurfaceState {
    val oralRecords = records
        .filter { it.feature == FeatureKind.ORAL_HEALTH }
        .sortedByDescending { it.recordedAtEpochMillis }

    val chronologicalProgressLabels = oralRecords
        .sortedBy { it.recordedAtEpochMillis }
        .mapIndexed { index, record ->
            record.sessionId to if (index < ORAL_BASELINE_REQUIRED_SESSIONS) {
                "Baseline session ${index + 1} of $ORAL_BASELINE_REQUIRED_SESSIONS"
            } else {
                "Post-baseline trend check"
            }
        }
        .toMap()

    val items = oralRecords.map { record ->
        OralHistoryViewItem(
            sessionId = record.sessionId,
            title = record.summaryTitle,
            detail = record.consumerSafeSummaryDetail(),
            syncLabel = record.syncState.displayLabel(),
            progressLabel = chronologicalProgressLabels.getValue(record.sessionId),
        )
    }

    val completedCount = oralRecords.size.coerceAtMost(ORAL_BASELINE_REQUIRED_SESSIONS)
    val baselineProgressLabel = if (completedCount < ORAL_BASELINE_REQUIRED_SESSIONS) {
        "Baseline building $completedCount/$ORAL_BASELINE_REQUIRED_SESSIONS"
    } else {
        "Baseline ready"
    }
    val progressDetail = if (completedCount < ORAL_BASELINE_REQUIRED_SESSIONS) {
        "${ORAL_BASELINE_REQUIRED_SESSIONS - completedCount} more oral sessions are needed before post-baseline trend views unlock."
    } else {
        "Baseline is established. New oral sessions now extend the post-baseline trend view."
    }

    return OralHistorySurfaceState(
        baselineProgressLabel = baselineProgressLabel,
        progressDetail = progressDetail,
        latestStatusLabel = items.firstOrNull()?.syncLabel,
        items = items,
    )
}

fun SessionHistoryStoreState.fatHistorySurface(): FatHistorySurfaceState {
    val fatRecords = records
        .filter { it.feature == FeatureKind.FAT_BURNING }
        .sortedByDescending { it.recordedAtEpochMillis }

    var runningBestDelta = 0
    val items = fatRecords
        .sortedBy { it.recordedAtEpochMillis }
        .map { record ->
            val finalDelta = deriveFinalDelta(record.resultToken)
            runningBestDelta = maxOf(runningBestDelta, finalDelta)
            record to FatHistoryViewItem(
                sessionId = record.sessionId,
                title = record.summaryTitle,
                detail = record.consumerSafeSummaryDetail(),
                syncLabel = record.syncState.displayLabel(),
                finalDeltaLabel = "Final delta +$finalDelta",
                bestDeltaLabel = "Best delta +$runningBestDelta",
            )
        }
        .reversed()
        .map { it.second }

    return FatHistorySurfaceState(
        latestFinalDeltaLabel = items.firstOrNull()?.finalDeltaLabel,
        bestDeltaLabel = items.maxByOrNull { deriveLabelScore(it.bestDeltaLabel) }?.bestDeltaLabel,
        pendingCount = fatRecords.count { it.syncState == SessionSyncState.PENDING },
        syncedCount = fatRecords.count { it.syncState == SessionSyncState.SYNCED },
        items = items,
    )
}

private fun SessionSyncState.displayLabel(): String {
    return when (this) {
        SessionSyncState.PENDING -> "Pending sync"
        SessionSyncState.SYNCED -> "Synced"
    }
}

private fun deriveFinalDelta(resultToken: String): Int {
    return resultToken.fold(0) { accumulator, character ->
        ((accumulator * 31) + character.code) % 21
    } + 5
}

private fun deriveLabelScore(label: String): Int {
    return label.substringAfter("+").toIntOrNull() ?: 0
}
