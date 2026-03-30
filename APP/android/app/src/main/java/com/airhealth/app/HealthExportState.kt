package com.airhealth.app

enum class HealthExportPlatform(
    val wireValue: String,
    val title: String,
) {
    APPLE_HEALTH("apple_health", "Apple Health"),
    HEALTH_CONNECT("health_connect", "Health Connect"),
}

enum class ExportAuditStatus(
    val wireValue: String,
) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
}

enum class HealthExportPermissionState(
    val wireValue: String,
) {
    UNKNOWN("unknown"),
    GRANTED("granted"),
    DENIED("denied"),
}

data class CompletedSummaryExportPayload(
    val platform: HealthExportPlatform,
    val sessionId: String,
    val feature: String,
    val resultToken: String,
    val recordedAtEpochMillis: Long,
    val summaryTitle: String,
    val summaryDetail: String,
) {
    fun asWireMap(): Map<String, String> {
        return linkedMapOf(
            "platform" to platform.wireValue,
            "session_id" to sessionId,
            "feature" to feature,
            "result_token" to resultToken,
            "recorded_at_epoch_millis" to recordedAtEpochMillis.toString(),
            "summary_title" to summaryTitle,
            "summary_detail" to summaryDetail,
        )
    }
}

data class ExportAuditRecord(
    val auditId: String,
    val feature: FeatureKind,
    val sessionId: String,
    val platform: HealthExportPlatform,
    val status: ExportAuditStatus,
    val recordedAtEpochMillis: Long,
    val exportedResultToken: String,
    val failureReason: String?,
) {
    val statusLabel: String
        get() = when (status) {
            ExportAuditStatus.SUCCEEDED -> "Exported"
            ExportAuditStatus.FAILED -> "Export failed"
        }
}

data class ExportAuditSurfaceState(
    val permissionState: HealthExportPermissionState,
    val latestStatusLabel: String?,
    val latestPlatformTitle: String?,
    val latestFailureReason: String?,
    val records: List<ExportAuditRecord>,
)

data class HealthExportPermissionStoreState(
    val byPlatform: Map<HealthExportPlatform, HealthExportPermissionState> = emptyMap(),
) {
    fun permissionFor(platform: HealthExportPlatform): HealthExportPermissionState {
        return byPlatform[platform] ?: HealthExportPermissionState.UNKNOWN
    }

    fun setPermission(
        platform: HealthExportPlatform,
        permissionState: HealthExportPermissionState,
    ): HealthExportPermissionStoreState {
        return copy(byPlatform = byPlatform + (platform to permissionState))
    }
}

data class ExportAuditStoreState(
    val records: List<ExportAuditRecord> = emptyList(),
) {
    fun append(record: ExportAuditRecord): ExportAuditStoreState {
        return copy(records = (listOf(record) + records).sortedByDescending { it.recordedAtEpochMillis })
    }

    fun latestRecordFor(feature: FeatureKind): ExportAuditRecord? {
        return records.firstOrNull { it.feature == feature }
    }

    fun surfaceFor(
        feature: FeatureKind,
        permissionState: HealthExportPermissionState,
    ): ExportAuditSurfaceState {
        val featureRecords = records.filter { it.feature == feature }.sortedByDescending { it.recordedAtEpochMillis }
        val latest = featureRecords.firstOrNull()
        return ExportAuditSurfaceState(
            permissionState = permissionState,
            latestStatusLabel = latest?.statusLabel,
            latestPlatformTitle = latest?.platform?.title,
            latestFailureReason = latest?.failureReason,
            records = featureRecords,
        )
    }
}

object HealthExportAdapter {
    fun payloadFor(
        record: PersistedSessionSummaryRecord,
        platform: HealthExportPlatform,
    ): CompletedSummaryExportPayload {
        return CompletedSummaryExportPayload(
            platform = platform,
            sessionId = record.sessionId,
            feature = record.feature.routeId,
            resultToken = record.resultToken,
            recordedAtEpochMillis = record.recordedAtEpochMillis,
            summaryTitle = record.summaryTitle,
            summaryDetail = record.summaryDetail,
        )
    }
}
