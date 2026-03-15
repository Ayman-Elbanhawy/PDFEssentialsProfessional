package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signing_identities")
data class SigningIdentityEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val subjectCommonName: String,
    val issuerCommonName: String,
    val serialNumberHex: String,
    val certificateSha256: String,
    val validFromEpochMillis: Long,
    val validToEpochMillis: Long,
    val encryptedPkcs12Path: String,
    val encryptedPasswordPath: String,
    val certificateAlias: String,
    val createdAtEpochMillis: Long,
)
