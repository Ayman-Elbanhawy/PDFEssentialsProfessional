package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TelemetryEventEntity)

    @Query("SELECT * FROM telemetry_events ORDER BY created_at DESC")
    suspend fun all(): List<TelemetryEventEntity>

    @Query("SELECT * FROM telemetry_events WHERE upload_state IN ('Pending', 'Failed') ORDER BY created_at ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<TelemetryEventEntity>

    @Query("UPDATE telemetry_events SET upload_state = :state, attempt_count = :attemptCount, last_attempt_at = :lastAttemptAt, uploaded_at = :uploadedAt, failure_message = :failureMessage WHERE id = :id")
    suspend fun markState(
        id: String,
        state: String,
        attemptCount: Int,
        lastAttemptAt: Long?,
        uploadedAt: Long?,
        failureMessage: String?,
    )

    @Query("DELETE FROM telemetry_events WHERE upload_state = 'Uploaded' AND uploaded_at IS NOT NULL AND uploaded_at < :thresholdEpochMillis")
    suspend fun deleteUploadedBefore(thresholdEpochMillis: Long)
}
