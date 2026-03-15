package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_searches",
    indices = [
        Index(value = ["documentKey"]),
        Index(value = ["documentKey", "searchedAtEpochMillis"]),
    ],
)
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val documentKey: String,
    val query: String,
    val searchedAtEpochMillis: Long,
)
