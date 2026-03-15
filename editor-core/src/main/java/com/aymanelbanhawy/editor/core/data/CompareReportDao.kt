package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CompareReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompareReportEntity)

    @Query("SELECT * FROM compare_reports WHERE documentKey = :documentKey ORDER BY createdAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<CompareReportEntity>

    @Query("SELECT * FROM compare_reports WHERE id = :reportId LIMIT 1")
    suspend fun byId(reportId: String): CompareReportEntity?
}
