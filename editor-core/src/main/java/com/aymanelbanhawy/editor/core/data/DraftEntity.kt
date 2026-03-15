package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "draft_file_path") val draftFilePath: String,
    @ColumnInfo(name = "working_copy_path") val workingCopyPath: String,
    @ColumnInfo(name = "is_autosave") val isAutosave: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
