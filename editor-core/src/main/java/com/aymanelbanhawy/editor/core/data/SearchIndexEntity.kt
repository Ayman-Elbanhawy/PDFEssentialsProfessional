package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "search_index_pages",
    primaryKeys = ["documentKey", "pageIndex"],
    indices = [
        Index(value = ["documentKey"]),
    ],
)
data class SearchIndexEntity(
    val documentKey: String,
    val pageIndex: Int,
    val pageText: String,
    val textBlocksJson: String,
    val ocrText: String? = null,
    val ocrBlocksJson: String? = null,
    val updatedAtEpochMillis: Long,
)
