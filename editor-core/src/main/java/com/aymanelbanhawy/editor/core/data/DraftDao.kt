package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity)

    @Query("SELECT * FROM drafts WHERE source_key = :sourceKey ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestForSource(sourceKey: String): DraftEntity?

    @Query("DELETE FROM drafts WHERE source_key = :sourceKey")
    suspend fun deleteBySource(sourceKey: String)

    @Query("DELETE FROM drafts WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
