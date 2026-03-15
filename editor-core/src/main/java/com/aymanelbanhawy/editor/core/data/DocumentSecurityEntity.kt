package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_security")
data class DocumentSecurityEntity(
    @PrimaryKey @ColumnInfo(name = "document_key") val documentKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
