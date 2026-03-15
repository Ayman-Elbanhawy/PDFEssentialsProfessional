package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkflowRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkflowRequestEntity)

    @Query("SELECT * FROM workflow_requests WHERE documentKey = :documentKey ORDER BY updatedAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<WorkflowRequestEntity>

    @Query("SELECT * FROM workflow_requests WHERE id = :requestId LIMIT 1")
    suspend fun byId(requestId: String): WorkflowRequestEntity?
}
