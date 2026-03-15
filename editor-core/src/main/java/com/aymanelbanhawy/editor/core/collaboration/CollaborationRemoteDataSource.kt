package com.aymanelbanhawy.editor.core.collaboration

interface CollaborationRemoteDataSource {
    suspend fun healthCheck(): RemoteServiceHealth
    suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot
    suspend fun push(request: RemoteMutationRequest): RemoteMutationResult
}
