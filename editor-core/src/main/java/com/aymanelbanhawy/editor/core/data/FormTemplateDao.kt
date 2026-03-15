package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FormTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FormTemplateEntity)

    @Query("SELECT * FROM form_templates WHERE documentKey = :documentKey ORDER BY updatedAtEpochMillis DESC")
    suspend fun forDocument(documentKey: String): List<FormTemplateEntity>

    @Query("SELECT * FROM form_templates WHERE id = :templateId LIMIT 1")
    suspend fun byId(templateId: String): FormTemplateEntity?
}
