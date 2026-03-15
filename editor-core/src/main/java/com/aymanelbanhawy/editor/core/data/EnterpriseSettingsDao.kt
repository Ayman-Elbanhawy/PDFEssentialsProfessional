package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EnterpriseSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EnterpriseSettingsEntity)

    @Query("SELECT * FROM enterprise_settings LIMIT 1")
    suspend fun get(): EnterpriseSettingsEntity?
}
