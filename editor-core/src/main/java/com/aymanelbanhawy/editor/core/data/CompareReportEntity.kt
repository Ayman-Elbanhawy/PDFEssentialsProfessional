package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "compare_reports",
    indices = [Index(value = ["documentKey"]), Index(value = ["createdAtEpochMillis"])],
)
data class CompareReportEntity(
    @PrimaryKey val id: String,
    val documentKey: String,
    val baselineDisplayName: String,
    val comparedDisplayName: String,
    val createdAtEpochMillis: Long,
    val summaryJson: String,
    val pageChangesJson: String,
    val comparedFilePath: String,
)
