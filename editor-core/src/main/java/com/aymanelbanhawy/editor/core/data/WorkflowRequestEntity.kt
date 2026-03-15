package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workflow_requests",
    indices = [Index(value = ["documentKey"]), Index(value = ["status"]), Index(value = ["updatedAtEpochMillis"])],
)
data class WorkflowRequestEntity(
    @PrimaryKey val id: String,
    val documentKey: String,
    val type: String,
    val title: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val recipientsJson: String,
    val assignedFieldsJson: String,
    val templateId: String?,
    val signingOrderEnforced: Boolean,
    val status: String,
    val expiresAtEpochMillis: Long?,
    val reminderScheduleJson: String?,
    val responsesJson: String,
    val submissionsJson: String,
    val metadataJson: String,
)
