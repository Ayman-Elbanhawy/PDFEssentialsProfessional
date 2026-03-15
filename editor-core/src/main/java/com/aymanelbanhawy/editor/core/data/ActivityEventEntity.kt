package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_events",
    indices = [Index(value = ["documentKey"]), Index(value = ["documentKey", "createdAtEpochMillis"])],
)
data class ActivityEventEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val type: String,
    val actor: String,
    val summary: String,
    val createdAtEpochMillis: Long,
    val threadId: String?,
    val metadataJson: String,
    val remoteVersion: Long?,
    val serverUpdatedAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
)
