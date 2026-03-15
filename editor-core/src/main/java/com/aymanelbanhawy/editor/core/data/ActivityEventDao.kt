package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActivityEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActivityEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ActivityEventEntity>)

    @Query("SELECT * FROM activity_events WHERE documentKey = :documentKey ORDER BY createdAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<ActivityEventEntity>

    @Query("DELETE FROM activity_events WHERE documentKey = :documentKey")
    suspend fun deleteForDocument(documentKey: String)
}
