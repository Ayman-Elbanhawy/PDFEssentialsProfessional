package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RuntimeBreadcrumbDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RuntimeBreadcrumbEntity)

    @Query("SELECT * FROM runtime_breadcrumbs ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RuntimeBreadcrumbEntity>

    @Query("SELECT * FROM runtime_breadcrumbs WHERE level = 'Error' OR category = 'Failure' ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentFailures(limit: Int): List<RuntimeBreadcrumbEntity>

    @Query("DELETE FROM runtime_breadcrumbs WHERE created_at < :thresholdEpochMillis")
    suspend fun trimOlderThan(thresholdEpochMillis: Long)
}
