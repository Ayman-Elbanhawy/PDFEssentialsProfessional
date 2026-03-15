package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "share_links",
    indices = [Index(value = ["documentKey"])],
)
data class ShareLinkEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val token: String,
    val title: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
    val permission: String,
    val isRevoked: Boolean,
    val remoteVersion: Long?,
    val serverUpdatedAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
)
