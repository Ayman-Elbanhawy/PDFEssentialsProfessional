package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import com.aymanelbanhawy.editor.core.migration.AiAssistantMigrationSummary
import kotlinx.serialization.json.Json

object AiAssistantMigrationSupport {
    suspend fun normalizePersistedState(context: Context): AiAssistantMigrationSummary {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val database = newAiAssistantDatabase(context)
        val store = RoomAiProviderSettingsStore(database.providerSettingsDao(), json)
        val current = store.load()
        val normalized = normalizePersistenceModel(current)
        val changed = normalized != current
        if (changed) {
            store.save(normalized)
        }
        return AiAssistantMigrationSummary(
            normalizedProfileCount = if (changed) normalized.profiles.size else 0,
            message = if (changed) {
                "Normalized ${normalized.profiles.size} AI provider profile(s), upgraded legacy endpoints, and preserved provider selection."
            } else {
                "AI assistant settings already matched the current schema."
            },
        )
    }
}
