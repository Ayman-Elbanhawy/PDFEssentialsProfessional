package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentSearchEntity)

    @Query(
        """
        SELECT * FROM recent_searches
        WHERE documentKey = :documentKey
        ORDER BY searchedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun recentForDocument(documentKey: String, limit: Int): List<RecentSearchEntity>

    @Query(
        """
        DELETE FROM recent_searches
        WHERE id NOT IN (
            SELECT id FROM recent_searches
            WHERE documentKey = :documentKey
            ORDER BY searchedAtEpochMillis DESC
            LIMIT :keepCount
        )
        AND documentKey = :documentKey
        """,
    )
    suspend fun trim(documentKey: String, keepCount: Int)
}
