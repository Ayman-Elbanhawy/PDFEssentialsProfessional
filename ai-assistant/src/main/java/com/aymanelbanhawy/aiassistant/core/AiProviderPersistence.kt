package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json

@Entity(tableName = "ai_provider_settings")
data class AiProviderSettingsEntity(
    @PrimaryKey val singletonId: String,
    val payloadJson: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "ai_workspace_documents")
data class AiWorkspaceDocumentEntity(
    @PrimaryKey val documentKey: String,
    val displayName: String,
    val sourceType: String,
    val workingCopyPath: String,
    val pinnedAtEpochMillis: Long,
)

@Entity(tableName = "ai_workspace_messages")
data class AiWorkspaceMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val task: String,
    val text: String,
    val citationsJson: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "ai_workspace_summaries")
data class AiWorkspaceSummaryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val documentKeysJson: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "ai_recent_document_sets")
data class AiRecentDocumentSetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val documentKeysJson: String,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long,
)

@Dao
interface AiProviderSettingsDao {
    @Query("SELECT * FROM ai_provider_settings WHERE singletonId = :singletonId LIMIT 1")
    suspend fun get(singletonId: String = SINGLETON_ID): AiProviderSettingsEntity?

    @Upsert
    suspend fun upsert(entity: AiProviderSettingsEntity)

    companion object {
        const val SINGLETON_ID: String = "assistant-provider-settings"
    }
}

@Dao
interface AiWorkspaceDocumentDao {
    @Query("SELECT * FROM ai_workspace_documents ORDER BY pinnedAtEpochMillis DESC")
    suspend fun all(): List<AiWorkspaceDocumentEntity>

    @Upsert
    suspend fun upsert(entity: AiWorkspaceDocumentEntity)

    @Query("DELETE FROM ai_workspace_documents WHERE documentKey = :documentKey")
    suspend fun delete(documentKey: String)
}

@Dao
interface AiWorkspaceMessageDao {
    @Query("SELECT * FROM ai_workspace_messages ORDER BY createdAtEpochMillis ASC")
    suspend fun all(): List<AiWorkspaceMessageEntity>

    @Upsert
    suspend fun upsertAll(entities: List<AiWorkspaceMessageEntity>)

    @Query("DELETE FROM ai_workspace_messages WHERE createdAtEpochMillis < :thresholdEpochMillis")
    suspend fun deleteOlderThan(thresholdEpochMillis: Long)

    @Query("DELETE FROM ai_workspace_messages")
    suspend fun clear()
}

@Dao
interface AiWorkspaceSummaryDao {
    @Query("SELECT * FROM ai_workspace_summaries ORDER BY createdAtEpochMillis DESC")
    suspend fun all(): List<AiWorkspaceSummaryEntity>

    @Upsert
    suspend fun upsert(entity: AiWorkspaceSummaryEntity)

    @Query("DELETE FROM ai_workspace_summaries WHERE createdAtEpochMillis < :thresholdEpochMillis")
    suspend fun deleteOlderThan(thresholdEpochMillis: Long)
}

@Dao
interface AiRecentDocumentSetDao {
    @Query("SELECT * FROM ai_recent_document_sets ORDER BY lastUsedAtEpochMillis DESC")
    suspend fun all(): List<AiRecentDocumentSetEntity>

    @Upsert
    suspend fun upsert(entity: AiRecentDocumentSetEntity)

    @Query("DELETE FROM ai_recent_document_sets WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        AiProviderSettingsEntity::class,
        AiWorkspaceDocumentEntity::class,
        AiWorkspaceMessageEntity::class,
        AiWorkspaceSummaryEntity::class,
        AiRecentDocumentSetEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AiAssistantDatabase : RoomDatabase() {
    abstract fun providerSettingsDao(): AiProviderSettingsDao
    abstract fun workspaceDocumentDao(): AiWorkspaceDocumentDao
    abstract fun workspaceMessageDao(): AiWorkspaceMessageDao
    abstract fun workspaceSummaryDao(): AiWorkspaceSummaryDao
    abstract fun recentDocumentSetDao(): AiRecentDocumentSetDao

    companion object {
        const val DATABASE_NAME: String = "ai-assistant.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ai_provider_settings ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_workspace_documents (documentKey TEXT NOT NULL, displayName TEXT NOT NULL, sourceType TEXT NOT NULL, workingCopyPath TEXT NOT NULL, pinnedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(documentKey))")
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_workspace_messages (id TEXT NOT NULL, role TEXT NOT NULL, task TEXT NOT NULL, text TEXT NOT NULL, citationsJson TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))")
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_workspace_summaries (id TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL, documentKeysJson TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))")
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_recent_document_sets (id TEXT NOT NULL, title TEXT NOT NULL, documentKeysJson TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, lastUsedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}

interface AiProviderSettingsStore {
    suspend fun load(): AssistantPersistenceModel
    suspend fun save(model: AssistantPersistenceModel)
}

class RoomAiProviderSettingsStore(
    private val dao: AiProviderSettingsDao,
    private val json: Json,
) : AiProviderSettingsStore {
    override suspend fun load(): AssistantPersistenceModel {
        val decoded = dao.get()?.let { entity ->
            runCatching { json.decodeFromString(AssistantPersistenceModel.serializer(), entity.payloadJson) }
                .getOrDefault(AssistantPersistenceModel())
        } ?: AssistantPersistenceModel()
        val normalized = normalizePersistenceModel(decoded)
        if (normalized != decoded) {
            save(normalized)
        }
        return normalized
    }

    override suspend fun save(model: AssistantPersistenceModel) {
        val normalized = normalizePersistenceModel(model)
        dao.upsert(
            AiProviderSettingsEntity(
                singletonId = AiProviderSettingsDao.SINGLETON_ID,
                payloadJson = json.encodeToString(AssistantPersistenceModel.serializer(), normalized),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}

internal fun newAiAssistantDatabase(context: Context): AiAssistantDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        AiAssistantDatabase::class.java,
        AiAssistantDatabase.DATABASE_NAME,
    ).addMigrations(AiAssistantDatabase.MIGRATION_1_2, AiAssistantDatabase.MIGRATION_2_3).build()
}
