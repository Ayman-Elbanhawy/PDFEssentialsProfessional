package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "version_snapshots",
    indices = [Index(value = ["documentKey"])],
)
data class VersionSnapshotEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val label: String,
    val createdAtEpochMillis: Long,
    val snapshotPath: String,
    val comparisonJson: String,
    val remoteVersion: Long?,
    val serverUpdatedAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
)
