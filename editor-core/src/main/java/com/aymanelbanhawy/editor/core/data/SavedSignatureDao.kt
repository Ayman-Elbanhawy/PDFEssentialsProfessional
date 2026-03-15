package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedSignatureDao {
    @Query("SELECT * FROM saved_signatures ORDER BY createdAtEpochMillis DESC")
    suspend fun all(): List<SavedSignatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedSignatureEntity)
}
