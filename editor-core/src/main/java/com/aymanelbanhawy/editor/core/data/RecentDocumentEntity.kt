package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "working_copy_path") val workingCopyPath: String,
    @ColumnInfo(name = "opened_at") val openedAtEpochMillis: Long,
)
