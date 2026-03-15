package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_jobs",
    indices = [
        Index(value = ["documentKey"]),
        Index(value = ["status", "updatedAtEpochMillis"]),
        Index(value = ["documentKey", "pageIndex"], unique = true),
    ],
)
data class OcrJobEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val pageIndex: Int,
    val imagePath: String,
    val status: String,
    val progressPercent: Int = 0,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 2,
    val resultText: String? = null,
    val resultBlocksJson: String? = null,
    val resultPageJson: String? = null,
    val diagnosticsJson: String? = null,
    val settingsJson: String? = null,
    val preprocessedImagePath: String? = null,
    val errorMessage: String? = null,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)