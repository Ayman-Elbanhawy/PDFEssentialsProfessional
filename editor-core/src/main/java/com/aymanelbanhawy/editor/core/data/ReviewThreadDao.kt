package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReviewThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReviewThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReviewThreadEntity>)

    @Query("SELECT * FROM review_threads WHERE documentKey = :documentKey ORDER BY modifiedAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<ReviewThreadEntity>

    @Query("SELECT * FROM review_threads WHERE id = :threadId LIMIT 1")
    suspend fun thread(threadId: String): ReviewThreadEntity?

    @Query("DELETE FROM review_threads WHERE documentKey = :documentKey")
    suspend fun deleteForDocument(documentKey: String)

    @Query("DELETE FROM review_threads WHERE id = :threadId")
    suspend fun deleteById(threadId: String)
}
