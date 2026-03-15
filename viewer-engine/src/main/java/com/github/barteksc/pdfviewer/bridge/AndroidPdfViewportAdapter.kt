package com.github.barteksc.pdfviewer.bridge

import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy

internal class AndroidPdfViewportAdapter(
    private val hostView: PdfViewportHostView,
) : PdfViewportAdapter {

    private var openedDocumentKey: String? = null

    override fun open(document: PdfDocumentRef, defaultPage: Int, callbacks: PdfViewportCallbacks) {
        val documentKey = "${document.uri}|${document.password}|$defaultPage"
        if (openedDocumentKey == documentKey) return
        openedDocumentKey = documentKey

        hostView.pdfView.fromUri(document.uri)
            .defaultPage(defaultPage)
            .password(document.password)
            .enableAnnotationRendering(true)
            .onLoad { callbacks.onDocumentLoaded(it) }
            .onPageChange { page, pageCount -> callbacks.onPageChanged(page, pageCount) }
            .onPageScroll { _, _ -> hostView.invalidate() }
            .onError { callbacks.onError(it) }
            .onPageError { _, throwable -> callbacks.onError(throwable) }
            .scrollHandle(DefaultScrollHandle(hostView.pdfView.context))
            .pageFitPolicy(FitPolicy.BOTH)
            .spacing(8)
            .load()
    }

    override fun renderDocumentState(
        pages: List<PageModel>,
        formDocument: FormDocumentModel,
        selection: SelectionModel,
        activeTool: AnnotationTool,
        searchHits: List<SearchHit>,
        selectedTextBlocks: List<ExtractedTextBlock>,
        callbacks: PdfViewportCallbacks,
    ) {
        hostView.renderDocumentState(pages, formDocument, selection, activeTool, searchHits, selectedTextBlocks, callbacks)
    }

    override fun recycle() {
        openedDocumentKey = null
        hostView.pdfView.recycle()
    }
}

