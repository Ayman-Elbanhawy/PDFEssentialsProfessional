package com.aymanelbanhawy.editor.core.migration

import kotlinx.serialization.Serializable

@Serializable
enum class MigrationSeverity {
    Info,
    Warning,
    Error,
}

@Serializable
enum class MigrationStepStatus {
    Applied,
    Repaired,
    Skipped,
    Failed,
}

@Serializable
data class MigrationStepReport(
    val id: String,
    val title: String,
    val status: MigrationStepStatus,
    val severity: MigrationSeverity = MigrationSeverity.Info,
    val message: String,
    val migratedCount: Int = 0,
    val repairedCount: Int = 0,
)

@Serializable
data class CoreMigrationReport(
    val engineVersion: Int,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val compatibilityModeUsed: Boolean,
    val backupDirectoryPath: String?,
    val steps: List<MigrationStepReport>,
) {
    val successCount: Int
        get() = steps.count { it.status == MigrationStepStatus.Applied || it.status == MigrationStepStatus.Repaired }

    val failureCount: Int
        get() = steps.count { it.status == MigrationStepStatus.Failed }

    val notice: String?
        get() = when {
            failureCount > 0 -> "Migration completed with ${failureCount} issue(s)."
            compatibilityModeUsed -> "Older local content remains readable and was upgraded in compatibility mode."
            successCount > 0 -> "Local data was upgraded successfully."
            else -> null
        }
}

@Serializable
data class AppMigrationReport(
    val core: CoreMigrationReport,
    val aiStateNormalizedCount: Int = 0,
    val aiStateMessage: String = "",
)

@Serializable
data class MigrationState(
    val engineVersion: Int,
    val lastCompletedAtEpochMillis: Long,
)

@Serializable
data class AiAssistantMigrationSummary(
    val normalizedProfileCount: Int = 0,
    val message: String = "",
)
