package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "form_profiles")
data class FormProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
)
