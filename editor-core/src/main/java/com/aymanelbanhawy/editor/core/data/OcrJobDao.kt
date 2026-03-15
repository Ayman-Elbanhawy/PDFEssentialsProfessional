package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OcrJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OcrJobEntity>)

    @Query("SELECT * FROM ocr_jobs WHERE id = :id LIMIT 1")
    suspend fun job(id: String): OcrJobEntity?

    @Query("SELECT * FROM ocr_jobs ORDER BY updatedAtEpochMillis DESC")
    suspend fun all(): List<OcrJobEntity>

    @Query(
        """
        SELECT * FROM ocr_jobs
        WHERE documentKey = :documentKey
        ORDER BY pageIndex ASC, createdAtEpochMillis ASC
        """,
    )
    suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity>

    @Query(
        """
        SELECT * FROM ocr_jobs
        WHERE documentKey = :documentKey
        ORDER BY pageIndex ASC, createdAtEpochMillis ASC
        """,
    )
    fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>>

    @Query(
        """
        SELECT * FROM ocr_jobs
        WHERE documentKey = :documentKey
          AND pageIndex = :pageIndex
        LIMIT 1
        """,
    )
    suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity?

    @Query(
        """
        SELECT * FROM ocr_jobs
        WHERE documentKey = :documentKey
          AND (
                status IN ('Pending', 'RetryScheduled')
                OR (status = 'Running' AND updatedAtEpochMillis < :staleBeforeEpochMillis)
          )
        ORDER BY pageIndex ASC, updatedAtEpochMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity>
}
