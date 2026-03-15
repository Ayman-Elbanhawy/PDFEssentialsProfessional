package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConnectorAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConnectorAccountEntity)

    @Query("SELECT * FROM connector_accounts ORDER BY display_name ASC")
    suspend fun all(): List<ConnectorAccountEntity>

    @Query("SELECT * FROM connector_accounts WHERE id = :id")
    suspend fun get(id: String): ConnectorAccountEntity?

    @Query("DELETE FROM connector_accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}
