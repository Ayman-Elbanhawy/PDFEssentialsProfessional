package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAiWorkspaceStoreTest {
    private lateinit var database: AiAssistantDatabase
    private lateinit var store: RoomAiWorkspaceStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AiAssistantDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomAiWorkspaceStore(
            documentDao = database.workspaceDocumentDao(),
            messageDao = database.workspaceMessageDao(),
            summaryDao = database.workspaceSummaryDao(),
            recentSetDao = database.recentDocumentSetDao(),
            json = json,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun load_returnsPinnedDocumentsMessagesSummariesAndSets() = runTest {
        val now = System.currentTimeMillis()
        val document = WorkspaceDocumentReference(
            documentKey = "doc-1",
            displayName = "Proposal.pdf",
            sourceType = "FILE",
            workingCopyPath = "C:/docs/Proposal.pdf",
            pinnedAtEpochMillis = now,
        )
        val message = AssistantMessage(
            id = UUID.randomUUID().toString(),
            role = AssistantMessageRole.Assistant,
            task = AssistantTaskType.AskWorkspace,
            text = "Here is a grounded answer.",
            citations = listOf(
                AssistantCitation(
                    id = "citation-1",
                    title = "Evidence 1",
                    anchor = CitationAnchor(
                        documentKey = document.documentKey,
                        documentTitle = document.displayName,
                        pageIndex = 1,
                        bounds = com.aymanelbanhawy.editor.core.model.NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
                        quote = "Important clause.",
                    ),
                    confidence = 0.88f,
                ),
            ),
            createdAtEpochMillis = now,
        )
        val summary = WorkspaceSummaryModel(
            id = "summary-1",
            title = "Set Summary",
            summary = "Workspace summary body",
            documentKeys = listOf(document.documentKey),
            createdAtEpochMillis = now,
        )
        val recentSet = RecentDocumentSetModel(
            id = "set-1",
            title = "Renewal Review",
            documentKeys = listOf(document.documentKey),
            createdAtEpochMillis = now,
            lastUsedAtEpochMillis = now,
        )

        store.pin(document)
        store.replaceMessages(listOf(message), retentionDays = 30)
        store.saveSummary(summary)
        store.saveRecentSet(recentSet)

        val loaded = store.load()

        assertThat(loaded.pinnedDocuments).containsExactly(document)
        assertThat(loaded.conversationHistory).containsExactly(message)
        assertThat(loaded.workspaceSummaries).containsExactly(summary)
        assertThat(loaded.recentDocumentSets).containsExactly(recentSet)
    }

    @Test
    fun pruneHistory_removesExpiredMessagesAndSummaries() = runTest {
        val staleTime = System.currentTimeMillis() - 5L * 86_400_000L
        val freshTime = System.currentTimeMillis()
        store.replaceMessages(
            listOf(
                AssistantMessage(
                    id = "stale-message",
                    role = AssistantMessageRole.User,
                    task = AssistantTaskType.AskWorkspace,
                    text = "Old prompt",
                    createdAtEpochMillis = staleTime,
                ),
                AssistantMessage(
                    id = "fresh-message",
                    role = AssistantMessageRole.Assistant,
                    task = AssistantTaskType.AskWorkspace,
                    text = "Fresh prompt",
                    createdAtEpochMillis = freshTime,
                ),
            ),
            retentionDays = 365,
        )
        store.saveSummary(
            WorkspaceSummaryModel(
                id = "stale-summary",
                title = "Old",
                summary = "Old summary",
                documentKeys = listOf("doc-1"),
                createdAtEpochMillis = staleTime,
            ),
        )
        store.saveSummary(
            WorkspaceSummaryModel(
                id = "fresh-summary",
                title = "Fresh",
                summary = "Fresh summary",
                documentKeys = listOf("doc-1"),
                createdAtEpochMillis = freshTime,
            ),
        )

        store.pruneHistory(retentionDays = 1)

        assertThat(store.messages().map { it.id }).containsExactly("fresh-message")
        assertThat(store.summaries().map { it.id }).containsExactly("fresh-summary")
    }
}
