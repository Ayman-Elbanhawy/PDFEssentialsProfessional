package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OcrSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OcrSettingsEntity)

    @Query("SELECT * FROM ocr_settings WHERE id = :id LIMIT 1")
    suspend fun get(id: String = OcrSettingsEntity.GLOBAL_ID): OcrSettingsEntity?
}