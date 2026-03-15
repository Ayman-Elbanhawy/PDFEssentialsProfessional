package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_trail_events")
data class AuditTrailEventEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "document_key") val documentKey: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "actor") val actor: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "metadata_json") val metadataJson: String,
)
