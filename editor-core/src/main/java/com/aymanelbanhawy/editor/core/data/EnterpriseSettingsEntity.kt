package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enterprise_settings")
data class EnterpriseSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "policy_version") val policyVersion: String,
    @ColumnInfo(name = "policy_etag") val policyEtag: String?,
    @ColumnInfo(name = "last_sync_at") val lastSyncAtEpochMillis: Long?,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
