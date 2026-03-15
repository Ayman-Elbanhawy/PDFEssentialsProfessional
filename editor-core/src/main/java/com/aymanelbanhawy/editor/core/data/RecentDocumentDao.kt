package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentDocumentEntity)

    @Query("SELECT * FROM recent_documents ORDER BY opened_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentDocumentEntity>>
}
