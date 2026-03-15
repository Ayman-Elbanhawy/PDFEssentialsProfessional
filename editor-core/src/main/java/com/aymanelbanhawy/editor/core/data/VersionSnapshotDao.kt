package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VersionSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VersionSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VersionSnapshotEntity>)

    @Query("SELECT * FROM version_snapshots WHERE documentKey = :documentKey ORDER BY createdAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<VersionSnapshotEntity>

    @Query("DELETE FROM version_snapshots WHERE documentKey = :documentKey")
    suspend fun deleteForDocument(documentKey: String)
}
