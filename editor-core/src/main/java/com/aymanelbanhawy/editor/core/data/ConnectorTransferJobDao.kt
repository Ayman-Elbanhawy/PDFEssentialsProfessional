package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConnectorTransferJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConnectorTransferJobEntity)

    @Query("SELECT * FROM connector_transfer_jobs WHERE id = :id")
    suspend fun get(id: String): ConnectorTransferJobEntity?

    @Query("SELECT * FROM connector_transfer_jobs WHERE status IN ('Pending', 'Failed', 'Paused') ORDER BY created_at ASC")
    suspend fun pending(): List<ConnectorTransferJobEntity>

    @Query("SELECT * FROM connector_transfer_jobs ORDER BY updated_at DESC")
    suspend fun all(): List<ConnectorTransferJobEntity>

    @Query("DELETE FROM connector_transfer_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM connector_transfer_jobs WHERE status = 'Completed' AND updated_at < :thresholdEpochMillis")
    suspend fun deleteCompletedBefore(thresholdEpochMillis: Long)
}
