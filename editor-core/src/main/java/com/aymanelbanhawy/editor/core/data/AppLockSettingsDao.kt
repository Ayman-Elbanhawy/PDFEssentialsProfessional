package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppLockSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppLockSettingsEntity)

    @Query("SELECT * FROM app_lock_settings LIMIT 1")
    suspend fun get(): AppLockSettingsEntity?
}
