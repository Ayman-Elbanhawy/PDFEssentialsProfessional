package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DocumentSecurityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DocumentSecurityEntity)

    @Query("SELECT * FROM document_security WHERE document_key = :documentKey")
    suspend fun get(documentKey: String): DocumentSecurityEntity?
}
