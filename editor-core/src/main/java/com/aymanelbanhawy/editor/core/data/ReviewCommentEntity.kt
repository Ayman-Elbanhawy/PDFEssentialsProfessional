package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_comments",
    indices = [Index(value = ["threadId"]), Index(value = ["threadId", "createdAtEpochMillis"])],
)
data class ReviewCommentEntity(
    @PrimaryKey
    val id: String,
    val threadId: String,
    val author: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val mentionsJson: String,
    val voiceAttachmentJson: String? = null,
)

