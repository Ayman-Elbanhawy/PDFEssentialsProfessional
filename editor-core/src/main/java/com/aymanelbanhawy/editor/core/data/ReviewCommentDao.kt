package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReviewCommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReviewCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReviewCommentEntity>)

    @Query("SELECT * FROM review_comments WHERE threadId = :threadId ORDER BY createdAtEpochMillis ASC")
    suspend fun forThread(threadId: String): List<ReviewCommentEntity>

    @Query("DELETE FROM review_comments WHERE threadId = :threadId")
    suspend fun deleteForThread(threadId: String)

    @Query("DELETE FROM review_comments WHERE threadId IN (:threadIds)")
    suspend fun deleteForThreads(threadIds: List<String>)
}
