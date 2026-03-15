package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FormProfileDao {
    @Query("SELECT * FROM form_profiles ORDER BY createdAtEpochMillis DESC")
    suspend fun all(): List<FormProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FormProfileEntity)
}
