package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_settings")
data class OcrSettingsEntity(
    @PrimaryKey
    val id: String = GLOBAL_ID,
    val payloadJson: String,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val GLOBAL_ID: String = "global"
    }
}