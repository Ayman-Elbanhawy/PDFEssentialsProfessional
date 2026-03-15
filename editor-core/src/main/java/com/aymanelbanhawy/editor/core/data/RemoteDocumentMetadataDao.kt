package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteDocumentMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RemoteDocumentMetadataEntity)

    @Query("SELECT * FROM remote_document_metadata WHERE document_key = :documentKey")
    suspend fun get(documentKey: String): RemoteDocumentMetadataEntity?

    @Query("DELETE FROM remote_document_metadata WHERE document_key = :documentKey")
    suspend fun deleteByDocumentKey(documentKey: String)
}
