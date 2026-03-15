package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runtime_breadcrumbs")
data class RuntimeBreadcrumbEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "event_name") val eventName: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "metadata_json") val metadataJson: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
)
