package com.aymanelbanhawy.enterprisepdf.app.collaboration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MarkUnreadChatAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.collaboration.ReviewFilterModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadState
import com.aymanelbanhawy.editor.core.collaboration.ShareLinkModel
import com.aymanelbanhawy.editor.core.collaboration.VersionSnapshotModel
import com.aymanelbanhawy.editor.core.collaboration.VoiceCommentAttachmentModel
import com.aymanelbanhawy.editor.core.workflow.CompareMarkerModel
import com.aymanelbanhawy.editor.core.workflow.CompareReportModel
import com.aymanelbanhawy.editor.core.workflow.FormTemplateModel
import com.aymanelbanhawy.editor.core.workflow.WorkflowRequestModel
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@Composable
fun ReviewSidebar(
    modifier: Modifier,
    shareLinks: List<ShareLinkModel>,
    reviewThreads: List<ReviewThreadModel>,
    snapshots: List<VersionSnapshotModel>,
    compareReports: List<CompareReportModel>,
    formTemplates: List<FormTemplateModel>,
    workflowRequests: List<WorkflowRequestModel>,
    filter: ReviewFilterModel,
    pendingSyncCount: Int,
    pendingThreadVoiceComment: VoiceCommentAttachmentModel?,
    pendingReplyVoiceComments: Map<String, VoiceCommentAttachmentModel>,
    activeVoiceCommentPlaybackId: String?,
    onCreateShareLink: () -> Unit,
    onCreateSnapshot: () -> Unit,
    onSyncNow: () -> Unit,
    onCompareAgainstLatestSnapshot: () -> Unit,
    onCreateFormTemplate: (String) -> Unit,
    onCreateSignatureRequest: (String) -> Unit,
    onCreateFormRequest: (String, String) -> Unit,
    onFilterChanged: (ReviewFilterModel) -> Unit,
    onAddThread: (String, String) -> Unit,
    onAddReply: (String, String) -> Unit,
    onToggleResolved: (String, Boolean) -> Unit,
    onStartThreadVoiceComment: () -> Unit,
    onStopThreadVoiceComment: () -> Unit,
    onCancelThreadVoiceComment: () -> Unit,
    onStartReplyVoiceComment: (String) -> Unit,
    onStopReplyVoiceComment: (String) -> Unit,
    onCancelReplyVoiceComment: (String) -> Unit,
    onPlayVoiceComment: (String) -> Unit,
    onStopVoiceCommentPlayback: () -> Unit,
    onOpenCompareMarker: (CompareMarkerModel) -> Unit,
) {
    var draftTitle by remember { mutableStateOf("") }
    var draftMessage by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf("") }
    var recipientCsv by remember { mutableStateOf("") }
    var replyByThreadId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Review", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.IosShare, tooltip = "Create Share Link", onClick = onCreateShareLink)
                IconTooltipButton(icon = Icons.Outlined.Save, tooltip = "Create Snapshot", onClick = onCreateSnapshot)
                IconTooltipButton(icon = Icons.Outlined.CompareArrows, tooltip = "Compare With Latest Snapshot", onClick = onCompareAgainstLatestSnapshot)
                IconTooltipButton(icon = Icons.Outlined.CloudSync, tooltip = "Sync $pendingSyncCount", onClick = onSyncNow)
            }

            if (compareReports.isNotEmpty()) {
                Text("Recent compare", style = MaterialTheme.typography.titleMedium)
                compareReports.take(3).forEach { report ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${report.baselineDisplayName} vs ${report.comparedDisplayName}", style = MaterialTheme.typography.labelLarge)
                            Text(report.summary.summaryText, style = MaterialTheme.typography.bodySmall)
                            report.pageChanges.take(3).forEach { change ->
                                Text(change.summary, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    change.markers.take(3).forEachIndexed { index, marker ->
                                        IconTooltipButton(
                                            icon = Icons.Outlined.CompareArrows,
                                            tooltip = "Open diff ${change.pageIndex + 1}.${index + 1}",
                                            onClick = { onOpenCompareMarker(marker) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text("Thread filters", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.FilterAlt, tooltip = "Show All Threads", selected = filter.state == null, onClick = { onFilterChanged(filter.copy(state = null)) })
                IconTooltipButton(icon = Icons.Outlined.MarkUnreadChatAlt, tooltip = "Show Open Threads", selected = filter.state == ReviewThreadState.Open, onClick = { onFilterChanged(filter.copy(state = ReviewThreadState.Open)) })
                IconTooltipButton(icon = Icons.Outlined.Done, tooltip = "Show Resolved Threads", selected = filter.state == ReviewThreadState.Resolved, onClick = { onFilterChanged(filter.copy(state = ReviewThreadState.Resolved)) })
            }
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = filter.query, onValueChange = { onFilterChanged(filter.copy(query = it)) }, label = { Text("Search threads") }, singleLine = true)

            Text("Workflow prep", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = templateName, onValueChange = { templateName = it }, label = { Text("Template name") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.Description, tooltip = "Save Form Template", onClick = { onCreateFormTemplate(templateName); templateName = "" })
                formTemplates.firstOrNull()?.let { template ->
                    IconTooltipButton(icon = Icons.Outlined.DriveFileRenameOutline, tooltip = "Send Form Request", onClick = { onCreateFormRequest(template.id, recipientCsv) })
                }
            }
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = recipientCsv, onValueChange = { recipientCsv = it }, label = { Text("Recipients (comma separated)") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.VerifiedUser, tooltip = "Send Signature Request", onClick = { onCreateSignatureRequest(recipientCsv) })
            }

            if (formTemplates.isNotEmpty()) {
                Text("Templates", style = MaterialTheme.typography.titleMedium)
                formTemplates.take(4).forEach { template ->
                    Text("${template.name} � ${template.schema.fieldMappings.size} field(s)", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (workflowRequests.isNotEmpty()) {
                Text("Active requests", style = MaterialTheme.typography.titleMedium)
                workflowRequests.take(4).forEach { request ->
                    Text("${request.title} � ${request.status.name} � ${request.recipients.size} recipient(s)", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("New thread", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = draftTitle, onValueChange = { draftTitle = it }, label = { Text("Title") })
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = draftMessage, onValueChange = { draftMessage = it }, label = { Text("Comment with @mentions") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.RecordVoiceOver, tooltip = "Record Thread Voice Comment", onClick = onStartThreadVoiceComment)
                IconTooltipButton(icon = Icons.Outlined.Stop, tooltip = "Stop Thread Voice Comment", onClick = onStopThreadVoiceComment)
                IconTooltipButton(icon = Icons.Outlined.Replay, tooltip = "Cancel Thread Voice Comment", onClick = onCancelThreadVoiceComment)
                IconTooltipButton(icon = Icons.Outlined.ContentCopy, tooltip = "Add Review Thread", onClick = {
                    onAddThread(draftTitle, draftMessage)
                    draftTitle = ""
                    draftMessage = ""
                })
            }
            pendingThreadVoiceComment?.let {
                Text("Voice comment attached (${it.durationMillis} ms)", style = MaterialTheme.typography.bodySmall)
            }

            if (shareLinks.isNotEmpty()) {
                Text("Share links", style = MaterialTheme.typography.titleMedium)
                shareLinks.take(6).forEach { link ->
                    Text("${link.title} � ${link.permission.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (snapshots.isNotEmpty()) {
                Text("Versions", style = MaterialTheme.typography.titleMedium)
                snapshots.take(6).forEach { snapshot ->
                    Text("${snapshot.label} � comments ${snapshot.comparison.commentDelta}", style = MaterialTheme.typography.bodySmall)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reviewThreads, key = { it.id }) { thread ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(thread.title, style = MaterialTheme.typography.titleSmall)
                            Text("${thread.createdBy} � ${thread.state.name}", style = MaterialTheme.typography.labelMedium)
                            thread.comments.forEach { comment ->
                                Text("${comment.author}: ${comment.message}", style = MaterialTheme.typography.bodySmall)
                                comment.voiceAttachment?.let { attachment ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Voice comment (${attachment.durationMillis} ms)", style = MaterialTheme.typography.bodySmall)
                                        if (activeVoiceCommentPlaybackId == comment.id) {
                                            IconTooltipButton(icon = Icons.Outlined.Stop, tooltip = "Stop Voice Comment", onClick = onStopVoiceCommentPlayback)
                                        } else {
                                            IconTooltipButton(icon = Icons.Outlined.PlayArrow, tooltip = "Play Voice Comment", onClick = { onPlayVoiceComment(comment.id) })
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = replyByThreadId[thread.id].orEmpty(), onValueChange = { replyByThreadId = replyByThreadId + (thread.id to it) }, label = { Text("Reply") })
                            pendingReplyVoiceComments[thread.id]?.let { voice ->
                                Text("Reply voice attached (${voice.durationMillis} ms)", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconTooltipButton(icon = Icons.Outlined.RecordVoiceOver, tooltip = "Record Reply Voice Comment", onClick = { onStartReplyVoiceComment(thread.id) })
                                IconTooltipButton(icon = Icons.Outlined.Stop, tooltip = "Stop Reply Voice Comment", onClick = { onStopReplyVoiceComment(thread.id) })
                                IconTooltipButton(icon = Icons.Outlined.Replay, tooltip = "Cancel Reply Voice Comment", onClick = { onCancelReplyVoiceComment(thread.id) })
                                IconTooltipButton(icon = Icons.Outlined.ContentCopy, tooltip = "Reply", onClick = {
                                    val reply = replyByThreadId[thread.id].orEmpty()
                                    if (reply.isNotBlank()) {
                                        onAddReply(thread.id, reply)
                                        replyByThreadId = replyByThreadId + (thread.id to "")
                                    }
                                })
                                IconTooltipButton(icon = if (thread.state == ReviewThreadState.Resolved) Icons.Outlined.Replay else Icons.Outlined.Done, tooltip = if (thread.state == ReviewThreadState.Resolved) "Reopen Thread" else "Resolve Thread", onClick = { onToggleResolved(thread.id, thread.state != ReviewThreadState.Resolved) })
                            }
                        }
                    }
                }
            }
        }
    }
}
