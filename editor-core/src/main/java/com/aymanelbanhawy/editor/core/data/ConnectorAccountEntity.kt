package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connector_accounts")
data class ConnectorAccountEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "connector_type") val connectorType: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "credential_type") val credentialType: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "secret_alias") val secretAlias: String?,
    @ColumnInfo(name = "capabilities_json") val capabilitiesJson: String = "[]",
    @ColumnInfo(name = "configuration_json") val configurationJson: String = "{}",
    @ColumnInfo(name = "supports_open") val supportsOpen: Boolean,
    @ColumnInfo(name = "supports_save") val supportsSave: Boolean,
    @ColumnInfo(name = "supports_share") val supportsShare: Boolean,
    @ColumnInfo(name = "supports_import") val supportsImport: Boolean,
    @ColumnInfo(name = "supports_metadata_sync") val supportsMetadataSync: Boolean,
    @ColumnInfo(name = "supports_resumable_transfer") val supportsResumableTransfer: Boolean,
    @ColumnInfo(name = "is_enterprise_managed") val isEnterpriseManaged: Boolean,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
