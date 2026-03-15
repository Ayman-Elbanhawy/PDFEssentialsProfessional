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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventModel
import com.aymanelbanhawy.editor.core.workflow.WorkflowRequestModel
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@Composable
fun ActivitySidebar(
    modifier: Modifier,
    events: List<ActivityEventModel>,
    requests: List<WorkflowRequestModel>,
    pendingSyncCount: Int,
    onSyncNow: () -> Unit,
    onSendReminder: (String) -> Unit,
    onMarkRequestCompleted: (String) -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Activity", style = MaterialTheme.typography.titleLarge)
                IconTooltipButton(icon = Icons.Outlined.CloudSync, tooltip = "Sync $pendingSyncCount", onClick = onSyncNow)
            }
            if (requests.isNotEmpty()) {
                Text("Requests", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(0.45f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(requests, key = { it.id }) { request ->
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(request.title, style = MaterialTheme.typography.labelLarge)
                                Text("${request.type.name} · ${request.status.name}", style = MaterialTheme.typography.bodyMedium)
                                Text("${request.responses.size}/${request.recipients.size} responses", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconTooltipButton(icon = Icons.Outlined.Notifications, tooltip = "Send Reminder", onClick = { onSendReminder(request.id) })
                                    IconTooltipButton(icon = Icons.Outlined.CheckCircle, tooltip = "Mark Complete", onClick = { onMarkRequestCompleted(request.id) })
                                }
                            }
                        }
                    }
                }
            }
            Text("Timeline", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events, key = { it.id }) { event ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.type.name, style = MaterialTheme.typography.labelLarge)
                            Text(event.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("${event.actor} · ${event.createdAtEpochMillis}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
