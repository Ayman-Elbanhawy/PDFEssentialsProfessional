package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EditHistoryMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EditHistoryMetadataEntity)

    @Query("SELECT * FROM edit_history_metadata WHERE source_key = :sourceKey ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestForSource(sourceKey: String): EditHistoryMetadataEntity?

    @Query("DELETE FROM edit_history_metadata WHERE source_key = :sourceKey")
    suspend fun deleteBySource(sourceKey: String)
}
