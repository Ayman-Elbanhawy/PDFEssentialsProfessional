package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShareLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShareLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ShareLinkEntity>)

    @Query("SELECT * FROM share_links WHERE documentKey = :documentKey ORDER BY createdAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<ShareLinkEntity>

    @Query("DELETE FROM share_links WHERE documentKey = :documentKey")
    suspend fun deleteForDocument(documentKey: String)

    @Query("DELETE FROM share_links WHERE id = :id")
    suspend fun deleteById(id: String)
}
