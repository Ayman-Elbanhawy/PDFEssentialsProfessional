package com.aymanelbanhawy.enterprisepdf.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorViewModel

@Composable
fun MainActivityContent(
    viewModel: EditorViewModel,
    onShareDocument: (EditorSessionEvent.ShareDocument) -> Unit,
    onShareText: (EditorSessionEvent.ShareText) -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    if (state.organizeVisible) {
        OrganizeWorkspaceHost(
            state = state,
            viewModel = viewModel,
        )
    } else {
        EditorWorkspaceHost(
            state = state,
            viewModel = viewModel,
            onShareDocument = onShareDocument,
            onShareText = onShareText,
        )
    }
}
