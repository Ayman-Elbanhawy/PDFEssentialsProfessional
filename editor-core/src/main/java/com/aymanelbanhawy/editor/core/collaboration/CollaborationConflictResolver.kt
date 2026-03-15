package com.aymanelbanhawy.editor.core.collaboration

class CollaborationConflictResolver(
    private val policy: SyncConflictPolicy = SyncConflictPolicy.Merge,
) {
    fun resolveShareLink(local: ShareLinkModel, remote: ShareLinkModel?): ShareLinkModel = when {
        remote == null -> local
        policy == SyncConflictPolicy.PreferLocal -> local
        policy == SyncConflictPolicy.PreferRemote -> remote
        policy == SyncConflictPolicy.Merge -> {
            val localWins = local.createdAtEpochMillis >= remote.createdAtEpochMillis
            val base = if (localWins) local else remote
            base.copy(
                expiresAtEpochMillis = maxOfNullable(local.expiresAtEpochMillis, remote.expiresAtEpochMillis),
                isRevoked = local.isRevoked || remote.isRevoked,
                remoteVersion = maxOfNullable(local.remoteVersion, remote.remoteVersion),
                serverUpdatedAtEpochMillis = maxOfNullable(local.serverUpdatedAtEpochMillis, remote.serverUpdatedAtEpochMillis),
                lastSyncedAtEpochMillis = maxOfNullable(local.lastSyncedAtEpochMillis, remote.lastSyncedAtEpochMillis),
            )
        }
        local.createdAtEpochMillis >= remote.createdAtEpochMillis -> local
        else -> remote
    }

    fun resolveThread(local: ReviewThreadModel, remote: ReviewThreadModel?): ReviewThreadModel = when {
        remote == null -> local
        policy == SyncConflictPolicy.PreferLocal -> local
        policy == SyncConflictPolicy.PreferRemote -> remote
        policy == SyncConflictPolicy.Merge -> mergeThread(local, remote)
        local.modifiedAtEpochMillis >= remote.modifiedAtEpochMillis -> local
        else -> remote
    }

    fun resolutionReason(localUpdatedAt: Long, remoteUpdatedAt: Long): String {
        return when {
            localUpdatedAt == remoteUpdatedAt -> "Identical timestamps"
            localUpdatedAt > remoteUpdatedAt -> "Local change is newer"
            else -> "Remote change is newer"
        }
    }

    private fun mergeThread(local: ReviewThreadModel, remote: ReviewThreadModel): ReviewThreadModel {
        val newest = if (local.modifiedAtEpochMillis >= remote.modifiedAtEpochMillis) local else remote
        val mergedComments = (local.comments + remote.comments)
            .groupBy { it.id }
            .values
            .map { duplicates -> duplicates.maxBy { it.modifiedAtEpochMillis } }
            .sortedBy { it.createdAtEpochMillis }
        val mergedState = when {
            local.state == ReviewThreadState.Open || remote.state == ReviewThreadState.Open -> ReviewThreadState.Open
            else -> ReviewThreadState.Resolved
        }
        return newest.copy(
            title = if (local.modifiedAtEpochMillis >= remote.modifiedAtEpochMillis) local.title else remote.title,
            state = mergedState,
            comments = mergedComments,
            modifiedAtEpochMillis = maxOf(local.modifiedAtEpochMillis, remote.modifiedAtEpochMillis),
            remoteVersion = maxOfNullable(local.remoteVersion, remote.remoteVersion),
            serverUpdatedAtEpochMillis = maxOfNullable(local.serverUpdatedAtEpochMillis, remote.serverUpdatedAtEpochMillis),
            lastSyncedAtEpochMillis = maxOfNullable(local.lastSyncedAtEpochMillis, remote.lastSyncedAtEpochMillis),
        )
    }
}

private fun maxOfNullable(first: Long?, second: Long?): Long? {
    return when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }
}
