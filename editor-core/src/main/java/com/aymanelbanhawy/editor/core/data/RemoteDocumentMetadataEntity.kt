package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_document_metadata")
data class RemoteDocumentMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "document_key") val documentKey: String,
    @ColumnInfo(name = "connector_account_id") val connectorAccountId: String,
    @ColumnInfo(name = "remote_path") val remotePath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "version_id") val versionId: String?,
    @ColumnInfo(name = "modified_at") val modifiedAtEpochMillis: Long?,
    @ColumnInfo(name = "etag") val etag: String?,
    @ColumnInfo(name = "checksum_sha256") val checksumSha256: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "provider_metadata_json") val providerMetadataJson: String = "{}",
    @ColumnInfo(name = "last_conflict_at") val lastConflictAtEpochMillis: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
