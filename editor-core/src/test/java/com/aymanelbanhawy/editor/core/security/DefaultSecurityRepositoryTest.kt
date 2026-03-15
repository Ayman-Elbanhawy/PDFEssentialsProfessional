package com.aymanelbanhawy.editor.core.security

import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.data.AppLockSettingsDao
import com.aymanelbanhawy.editor.core.data.AppLockSettingsEntity
import com.aymanelbanhawy.editor.core.data.AuditTrailEventDao
import com.aymanelbanhawy.editor.core.data.AuditTrailEventEntity
import com.aymanelbanhawy.editor.core.data.DocumentSecurityDao
import com.aymanelbanhawy.editor.core.data.DocumentSecurityEntity
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultSecurityRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private val context = object : ContextWrapper(null) {
        private val filesRoot = File(System.getProperty("java.io.tmpdir"), "security-repository-test").apply { mkdirs() }
        override fun getFilesDir(): File = filesRoot
        override fun getCacheDir(): File = filesRoot
    }

    @Test
    fun evaluatePolicy_blocksTenantDisabledShare() {
        val repository = repository()
        val decision = repository.evaluatePolicy(
            security = SecurityDocumentModel(
                permissions = DocumentPermissionModel(allowShare = true),
                tenantPolicy = TenantPolicyHooksModel(disableShare = true),
            ),
            action = RestrictedAction.Share,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.message).contains("tenant policy")
    }

    @Test
    fun exportAuditTrail_writesJsonPayload() = runTest {
        val repository = repository()
        repository.recordAudit(
            AuditTrailEventModel(
                id = "event-1",
                documentKey = "doc-1",
                type = AuditEventType.RedactionApplied,
                actor = "tester",
                message = "Applied redactions",
                createdAtEpochMillis = 123L,
            ),
        )

        val destination = File(context.cacheDir, "audit/doc-1.json")
        repository.exportAuditTrail("doc-1", destination)

        assertThat(destination.exists()).isTrue()
        assertThat(destination.readText()).contains("Applied redactions")
        assertThat(repository.auditEvents("doc-1")).isNotEmpty()
    }

    private fun repository(): DefaultSecurityRepository {
        return DefaultSecurityRepository(
            context = context,
            appLockSettingsDao = FakeAppLockSettingsDao(),
            documentSecurityDao = FakeDocumentSecurityDao(),
            auditTrailEventDao = FakeAuditTrailEventDao(),
            json = json,
        )
    }
}

private class FakeAppLockSettingsDao : AppLockSettingsDao {
    private var entity: AppLockSettingsEntity? = null
    override suspend fun upsert(entity: AppLockSettingsEntity) { this.entity = entity }
    override suspend fun get(): AppLockSettingsEntity? = entity
}

private class FakeDocumentSecurityDao : DocumentSecurityDao {
    private val items = linkedMapOf<String, DocumentSecurityEntity>()
    override suspend fun upsert(entity: DocumentSecurityEntity) { items[entity.documentKey] = entity }
    override suspend fun get(documentKey: String): DocumentSecurityEntity? = items[documentKey]
}

private class FakeAuditTrailEventDao : AuditTrailEventDao {
    private val items = linkedMapOf<String, AuditTrailEventEntity>()
    override suspend fun upsert(entity: AuditTrailEventEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<AuditTrailEventEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
}
