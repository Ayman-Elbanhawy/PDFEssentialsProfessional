package com.github.barteksc.pdfviewer.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.SearchHit

@Composable
fun PdfSessionViewport(
    document: DocumentModel?,
    selection: SelectionModel,
    activeTool: AnnotationTool,
    currentPage: Int,
    searchHits: List<SearchHit>,
    selectedTextBlocks: List<ExtractedTextBlock>,
    modifier: Modifier = Modifier,
    callbacks: PdfViewportCallbacks = PdfViewportCallbacks(),
) {
    val latestCallbacks by rememberUpdatedState(callbacks)
    val adapterHolder = remember { AdapterHolder() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PdfViewportHostView(context).also { hostView ->
                adapterHolder.adapter = AndroidPdfViewportAdapter(hostView)
            }
        },
        update = {
            document?.let { loadedDocument ->
                adapterHolder.adapter?.open(loadedDocument.documentRef, currentPage, latestCallbacks)
                adapterHolder.adapter?.renderDocumentState(
                    pages = loadedDocument.pages,
                    formDocument = loadedDocument.formDocument,
                    selection = selection,
                    activeTool = activeTool,
                    searchHits = searchHits,
                    selectedTextBlocks = selectedTextBlocks,
                    callbacks = latestCallbacks,
                )
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose { adapterHolder.adapter?.recycle() }
    }
}

private class AdapterHolder {
    var adapter: PdfViewportAdapter? = null
}

