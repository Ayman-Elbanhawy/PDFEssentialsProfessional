package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_events")
data class TelemetryEventEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "properties_json") val propertiesJson: String,
    @ColumnInfo(name = "upload_state") val uploadState: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAtEpochMillis: Long?,
    @ColumnInfo(name = "uploaded_at") val uploadedAtEpochMillis: Long?,
    @ColumnInfo(name = "failure_message") val failureMessage: String?,
)
