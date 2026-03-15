package com.aymanelbanhawy.editor.core.collaboration

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test

class CollaborationConflictResolverTest {
    @Test
    fun preferNewestChoosesRemoteWhenRemoteIsNewer() {
        val resolver = CollaborationConflictResolver(SyncConflictPolicy.PreferNewest)
        val local = ShareLinkModel("local", "doc", "token", "Local", "Ayman", 10L, null, SharePermission.Comment)
        val remote = local.copy(title = "Remote", createdAtEpochMillis = 20L)

        val resolved = resolver.resolveShareLink(local, remote)

        assertThat(resolved.title).isEqualTo("Remote")
    }

    @Test
    fun mergeCombinesThreadComments() {
        val resolver = CollaborationConflictResolver(SyncConflictPolicy.Merge)
        val local = ReviewThreadModel(
            id = "thread",
            documentKey = "doc",
            pageIndex = 0,
            anchorBounds = null,
            title = "Local",
            createdBy = "Ayman",
            createdAtEpochMillis = 10L,
            modifiedAtEpochMillis = 40L,
            comments = listOf(
                ReviewCommentModel(UUID.randomUUID().toString(), "thread", "Ayman", "Local reply", 20L, 40L),
            ),
        )
        val remote = local.copy(
            title = "Remote",
            modifiedAtEpochMillis = 30L,
            comments = listOf(
                ReviewCommentModel(UUID.randomUUID().toString(), "thread", "Sam", "Remote reply", 15L, 30L),
            ),
        )

        val resolved = resolver.resolveThread(local, remote)

        assertThat(resolved.comments).hasSize(2)
        assertThat(resolved.comments.map { it.message }).containsExactly("Remote reply", "Local reply").inOrder()
        assertThat(resolved.title).isEqualTo("Local")
    }
}
