package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connector_transfer_jobs")
data class ConnectorTransferJobEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "connector_account_id") val connectorAccountId: String,
    @ColumnInfo(name = "document_key") val documentKey: String,
    @ColumnInfo(name = "remote_path") val remotePath: String,
    @ColumnInfo(name = "local_cache_path") val localCachePath: String,
    @ColumnInfo(name = "temp_materialized_path") val tempMaterializedPath: String? = null,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "bytes_transferred") val bytesTransferred: Long,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "resumable_token") val resumableToken: String? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    @ColumnInfo(name = "remote_version_id") val remoteVersionId: String? = null,
    @ColumnInfo(name = "conflict_strategy") val conflictStrategy: String = "Fail",
    @ColumnInfo(name = "cache_expires_at") val cacheExpiresAtEpochMillis: Long? = null,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
