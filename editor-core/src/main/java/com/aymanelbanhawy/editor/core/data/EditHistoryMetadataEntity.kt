package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "edit_history_metadata")
data class EditHistoryMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "undo_count") val undoCount: Int,
    @ColumnInfo(name = "redo_count") val redoCount: Int,
    @ColumnInfo(name = "last_command_name") val lastCommandName: String?,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
