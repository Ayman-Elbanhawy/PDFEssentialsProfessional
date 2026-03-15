package com.aymanelbanhawy.enterprisepdf.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorUiState
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorViewModel
import com.aymanelbanhawy.enterprisepdf.app.organize.OrganizePagesScreen

@Composable
fun OrganizeWorkspaceHost(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val organizeImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(viewModel::insertImagePage)
    }
    val mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.mergeDocuments(uris)
    }

    OrganizePagesScreen(
        state = state,
        events = viewModel.events,
        onBack = viewModel::showEditor,
        onSelectPage = viewModel::selectPage,
        onMovePage = viewModel::movePage,
        onMoveSelectionBackward = viewModel::moveSelectionBackward,
        onMoveSelectionForward = viewModel::moveSelectionForward,
        onRotateSelected = viewModel::rotateSelectedPages,
        onDeleteSelected = viewModel::deleteSelectedPages,
        onDuplicateSelected = viewModel::duplicateSelectedPages,
        onExtractSelected = viewModel::extractSelectedPages,
        onInsertBlankPage = viewModel::insertBlankPage,
        onPickImagePage = { organizeImageLauncher.launch("image/*") },
        onPickMergePdfs = {
            mergeLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "text/markdown",
                    "image/*",
                ),
            )
        },
        onUpdateSplitRange = viewModel::updateSplitRangeExpression,
        onSplitByRange = viewModel::splitByRange,
        onSplitOddPages = viewModel::splitOddPages,
        onSplitEvenPages = viewModel::splitEvenPages,
        onSplitSelectedPages = viewModel::splitSelectedPages,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
    )
}
