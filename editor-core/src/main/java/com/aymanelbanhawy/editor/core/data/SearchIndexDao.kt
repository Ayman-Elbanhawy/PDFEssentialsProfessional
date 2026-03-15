package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SearchIndexEntity>)

    @Query("SELECT DISTINCT documentKey FROM search_index_pages ORDER BY documentKey ASC")
    suspend fun documentKeys(): List<String>

    @Query(
        """
        SELECT * FROM search_index_pages
        WHERE documentKey = :documentKey
        ORDER BY pageIndex ASC
        """,
    )
    suspend fun indexForDocument(documentKey: String): List<SearchIndexEntity>

    @Query(
        """
        UPDATE search_index_pages
        SET ocrText = :ocrText,
            ocrBlocksJson = :ocrBlocksJson,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE documentKey = :documentKey AND pageIndex = :pageIndex
        """,
    )
    suspend fun updateOcrPayload(
        documentKey: String,
        pageIndex: Int,
        ocrText: String?,
        ocrBlocksJson: String?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        DELETE FROM search_index_pages
        WHERE documentKey = :documentKey
        """,
    )
    suspend fun deleteForDocument(documentKey: String)
}
