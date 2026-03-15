package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "form_templates",
    indices = [Index(value = ["documentKey"]), Index(value = ["updatedAtEpochMillis"])],
)
data class FormTemplateEntity(
    @PrimaryKey val id: String,
    val documentKey: String,
    val name: String,
    val schemaJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val exportedSchemaPath: String?,
)
