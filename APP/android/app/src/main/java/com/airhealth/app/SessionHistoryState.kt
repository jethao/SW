package com.airhealth.app

enum class SessionSyncState(
    val wireValue: String,
) {
    PENDING("pending"),
    SYNCED("synced"),
}

data class PersistedSessionSummaryRecord(
    val sessionId: String,
    val feature: FeatureKind,
    val resultToken: String,
    val recordedAtEpochMillis: Long,
    val syncState: SessionSyncState,
    val summaryTitle: String,
    val summaryDetail: String,
) {
    fun encode(): String {
        return listOf(
            sessionId,
            feature.routeId,
            resultToken,
            recordedAtEpochMillis.toString(),
            syncState.wireValue,
            summaryTitle,
            summaryDetail,
        ).joinToString("|")
    }

    companion object {
        fun decode(encoded: String): PersistedSessionSummaryRecord {
            val parts = encoded.split("|")
            require(parts.size == 7) { "Invalid persisted session summary payload." }
            return PersistedSessionSummaryRecord(
                sessionId = parts[0],
                feature = FeatureKind.entries.first { it.routeId == parts[1] },
                resultToken = parts[2],
                recordedAtEpochMillis = parts[3].toLong(),
                syncState = SessionSyncState.entries.first { it.wireValue == parts[4] },
                summaryTitle = parts[5],
                summaryDetail = parts[6],
            )
        }

        fun fromCompletedSession(
            session: MeasurementSessionState,
            recordedAtEpochMillis: Long,
            syncState: SessionSyncState = SessionSyncState.PENDING,
        ): PersistedSessionSummaryRecord {
            require(session.phase == MeasurementSessionPhase.COMPLETE) {
                "Only completed sessions may be persisted into consumer history."
            }

            val title = when (session.feature) {
                FeatureKind.ORAL_HEALTH -> "Oral session complete"
                FeatureKind.FAT_BURNING -> "Fat-burning session complete"
            }
            val detail = when (session.feature) {
                FeatureKind.ORAL_HEALTH -> "Result token ${session.terminalSummary?.resultToken ?: "pending"} recorded for oral trend history."
                FeatureKind.FAT_BURNING -> "Result token ${session.terminalSummary?.resultToken ?: "pending"} recorded for fat-burning history."
            }

            return PersistedSessionSummaryRecord(
                sessionId = session.sessionId,
                feature = session.feature,
                resultToken = session.terminalSummary?.resultToken ?: "unknown",
                recordedAtEpochMillis = recordedAtEpochMillis,
                syncState = syncState,
                summaryTitle = title,
                summaryDetail = detail,
            )
        }
    }
}

data class HistoryProjectionItem(
    val sessionId: String,
    val title: String,
    val detail: String,
    val statusLabel: String,
    val recordedAtEpochMillis: Long,
)

data class FeatureHistoryProjection(
    val feature: FeatureKind,
    val pendingCount: Int,
    val syncedCount: Int,
    val latestItem: HistoryProjectionItem?,
    val items: List<HistoryProjectionItem>,
)

data class SessionHistoryStoreState(
    val records: List<PersistedSessionSummaryRecord> = emptyList(),
) {
    fun upsert(record: PersistedSessionSummaryRecord): SessionHistoryStoreState {
        val remaining = records.filterNot { it.sessionId == record.sessionId }
        return copy(records = (remaining + record).sortedByDescending { it.recordedAtEpochMillis })
    }

    fun markSynced(sessionId: String): SessionHistoryStoreState {
        return copy(
            records = records.map { record ->
                if (record.sessionId == sessionId) {
                    record.copy(syncState = SessionSyncState.SYNCED)
                } else {
                    record
                }
            },
        )
    }

    fun projectionFor(feature: FeatureKind): FeatureHistoryProjection {
        val filtered = records
            .filter { it.feature == feature }
            .sortedByDescending { it.recordedAtEpochMillis }

        val items = filtered.map { record ->
            HistoryProjectionItem(
                sessionId = record.sessionId,
                title = record.summaryTitle,
                detail = record.summaryDetail,
                statusLabel = when (record.syncState) {
                    SessionSyncState.PENDING -> "Pending sync"
                    SessionSyncState.SYNCED -> "Synced"
                },
                recordedAtEpochMillis = record.recordedAtEpochMillis,
            )
        }

        return FeatureHistoryProjection(
            feature = feature,
            pendingCount = filtered.count { it.syncState == SessionSyncState.PENDING },
            syncedCount = filtered.count { it.syncState == SessionSyncState.SYNCED },
            latestItem = items.firstOrNull(),
            items = items,
        )
    }

    fun encode(): String {
        return records.joinToString("\n") { it.encode() }
    }

    companion object {
        fun decode(encoded: String): SessionHistoryStoreState {
            if (encoded.isBlank()) {
                return SessionHistoryStoreState()
            }

            return SessionHistoryStoreState(
                records = encoded
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .map(PersistedSessionSummaryRecord::decode)
                    .sortedByDescending { it.recordedAtEpochMillis }
                    .toList(),
            )
        }
    }
}
