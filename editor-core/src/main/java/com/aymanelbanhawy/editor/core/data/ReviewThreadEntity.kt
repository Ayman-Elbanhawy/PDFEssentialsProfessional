package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_threads",
    indices = [Index(value = ["documentKey"])],
)
data class ReviewThreadEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val pageIndex: Int?,
    val anchorBoundsJson: String?,
    val title: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val state: String,
    val remoteVersion: Long?,
    val serverUpdatedAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
)
