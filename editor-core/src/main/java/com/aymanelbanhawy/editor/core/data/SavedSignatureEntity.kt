package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_signatures")
data class SavedSignatureEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val imagePath: String,
    val createdAtEpochMillis: Long,
    val sourceType: String = "Handwritten",
    val signingIdentityId: String = "",
    val signerDisplayName: String = "",
    val certificateSubject: String = "",
    val certificateSha256: String = "",
)
