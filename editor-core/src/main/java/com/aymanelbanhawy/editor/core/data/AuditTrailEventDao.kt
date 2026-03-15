package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuditTrailEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AuditTrailEventEntity)

    @Query("SELECT * FROM audit_trail_events WHERE document_key = :documentKey ORDER BY created_at DESC")
    suspend fun forDocument(documentKey: String): List<AuditTrailEventEntity>
}
