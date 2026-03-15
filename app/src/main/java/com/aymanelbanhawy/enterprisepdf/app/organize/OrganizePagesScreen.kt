package com.aymanelbanhawy.enterprisepdf.app.organize

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Filter2
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.organize.ThumbnailDescriptor
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorUiState
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OrganizePagesScreen(
    state: EditorUiState,
    events: Flow<EditorSessionEvent>,
    onBack: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onMoveSelectionBackward: () -> Unit,
    onMoveSelectionForward: () -> Unit,
    onRotateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onExtractSelected: () -> Unit,
    onInsertBlankPage: () -> Unit,
    onPickImagePage: () -> Unit,
    onPickMergePdfs: () -> Unit,
    onUpdateSplitRange: (String) -> Unit,
    onSplitByRange: () -> Unit,
    onSplitOddPages: () -> Unit,
    onSplitEvenPages: () -> Unit,
    onSplitSelectedPages: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var quickToolsExpanded by remember { mutableStateOf(true) }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    val quickToolsVisible = quickToolsExpanded && draggedIndex < 0

    LaunchedEffect(events) {
        events.collectLatest { event ->
            if (event is EditorSessionEvent.UserMessage) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Quick Organize")
                        Text(
                            "${state.thumbnails.size} pages • ${state.selectedPageIndexes.size} selected",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, tooltip = "Back", onClick = onBack)
                },
                actions = {
                    IconTooltipButton(icon = Icons.Outlined.Tune, tooltip = if (quickToolsVisible) "Hide Quick Tools" else "Show Quick Tools", onClick = {
                        quickToolsExpanded = !quickToolsExpanded
                    })
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Undo, tooltip = "Undo", onClick = onUndo)
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Redo, tooltip = "Redo", onClick = onRedo)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = quickToolsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.31f)
                        .testTag("quick-organize-panel"),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Quick tools", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Inspired by fast zPDF-style page labs: split, merge, and rearrange without leaving the native editor.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickToolCard(
                                title = "Split fast",
                                body = "Range, odd/even, or selected pages.",
                                actionLabel = "Split now",
                                icon = Icons.Outlined.CallSplit,
                            )
                            QuickToolCard(
                                title = "Merge fast",
                                body = "Pull in PDFs, Word, text, and images.",
                                actionLabel = "Add sources",
                                icon = Icons.Outlined.Collections,
                            )
                            QuickToolCard(
                                title = "Rearrange fast",
                                body = "Drag thumbnails or nudge selected pages forward and back.",
                                actionLabel = "Reorder",
                                icon = Icons.Outlined.GridView,
                            )
                        }
                        Text("Batch actions", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.Rotate90DegreesCw, tooltip = "Rotate Selected", onClick = onRotateSelected)
                            IconTooltipButton(icon = Icons.Outlined.Delete, tooltip = "Delete Selected", onClick = onDeleteSelected)
                            IconTooltipButton(icon = Icons.Outlined.ContentCopy, tooltip = "Duplicate Selected", onClick = onDuplicateSelected)
                            IconTooltipButton(icon = Icons.Outlined.ContentCut, tooltip = "Extract Selected", onClick = onExtractSelected)
                            IconTooltipButton(icon = Icons.Outlined.Description, tooltip = "Insert Blank Page", onClick = onInsertBlankPage)
                            IconTooltipButton(icon = Icons.Outlined.AddPhotoAlternate, tooltip = "Insert Image Page", onClick = onPickImagePage)
                            IconTooltipButton(icon = Icons.Outlined.Collections, tooltip = "Merge PDFs", onClick = onPickMergePdfs)
                            IconTooltipButton(icon = Icons.Outlined.KeyboardDoubleArrowLeft, tooltip = "Move Selection Earlier", onClick = onMoveSelectionBackward)
                            IconTooltipButton(icon = Icons.Outlined.KeyboardDoubleArrowRight, tooltip = "Move Selection Later", onClick = onMoveSelectionForward)
                        }
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Quick split", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = state.splitRangeExpression,
                                    onValueChange = onUpdateSplitRange,
                                    label = { Text("Split Range") },
                                    supportingText = { Text("Use values like 1-3,5,7-8") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconTooltipButton(icon = Icons.Outlined.CallSplit, tooltip = "Split by Range", onClick = onSplitByRange)
                                    IconTooltipButton(icon = Icons.Outlined.Filter1, tooltip = "Split Odd Pages", onClick = onSplitOddPages)
                                    IconTooltipButton(icon = Icons.Outlined.Filter2, tooltip = "Split Even Pages", onClick = onSplitEvenPages)
                                    IconTooltipButton(icon = Icons.Outlined.ContentCut, tooltip = "Split Selected Pages", onClick = onSplitSelectedPages)
                                }
                            }
                        }
                        Text(
                            "While you drag thumbnails, the tool rail fades away to keep the page canvas clear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("organize-grid-surface"),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.thumbnails, key = { it.pageIndex }) { thumbnail ->
                        val isSelected = thumbnail.pageIndex in state.selectedPageIndexes
                        PageThumbnailCard(
                            thumbnail = thumbnail,
                            label = state.session.document?.pages?.getOrNull(thumbnail.pageIndex)?.label ?: "${thumbnail.pageIndex + 1}",
                            selected = isSelected,
                            modifier = Modifier
                                .animateItem()
                                .testTag("organize-page-card-${thumbnail.pageIndex}")
                                .onGloballyPositioned { coordinates -> itemBounds[thumbnail.pageIndex] = coordinates.boundsInParent() }
                                .pointerInput(state.thumbnails) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedIndex = thumbnail.pageIndex
                                            targetIndex = thumbnail.pageIndex
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val position = change.position
                                            targetIndex = itemBounds.entries.firstOrNull { it.value.contains(position) }?.key ?: targetIndex
                                        },
                                        onDragEnd = {
                                            if (draggedIndex >= 0 && targetIndex >= 0 && draggedIndex != targetIndex) {
                                                onMovePage(draggedIndex, targetIndex)
                                            }
                                            draggedIndex = -1
                                            targetIndex = -1
                                        },
                                        onDragCancel = {
                                            draggedIndex = -1
                                            targetIndex = -1
                                        },
                                    )
                                },
                            onClick = { onSelectPage(thumbnail.pageIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    title: String,
    body: String,
    actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PageThumbnailCard(
    thumbnail: ThumbnailDescriptor,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bitmap = remember(thumbnail.imagePath) { BitmapFactory.decodeFile(thumbnail.imagePath)?.asImageBitmap() }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (selected) 7.dp else 1.dp,
        shadowElevation = if (selected) 8.dp else 2.dp,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(20.dp),
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = label, modifier = Modifier.fillMaxSize())
                } else {
                    Text("Preview", textAlign = TextAlign.Center)
                }
            }
            Text("Page $label", style = MaterialTheme.typography.labelLarge)
            Text(
                if (selected) "Selected for quick tools" else "Tap to include in quick actions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
