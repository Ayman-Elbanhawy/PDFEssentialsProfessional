package com.github.barteksc.pdfviewer.bridge

import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.SearchHit

data class PdfViewportCallbacks(
    val onDocumentLoaded: (pageCount: Int) -> Unit = {},
    val onPageChanged: (page: Int, pageCount: Int) -> Unit = { _, _ -> },
    val onError: (Throwable) -> Unit = {},
    val onAnnotationCreated: (AnnotationModel) -> Unit = {},
    val onAnnotationUpdated: (before: AnnotationModel, after: AnnotationModel) -> Unit = { _, _ -> },
    val onAnnotationSelectionChanged: (pageIndex: Int, annotationIds: Set<String>) -> Unit = { _, _ -> },
    val onFormFieldTapped: (fieldName: String) -> Unit = {},
    val onPageEditSelected: (pageIndex: Int, editId: String?) -> Unit = { _, _ -> },
    val onPageEditUpdated: (before: PageEditModel, after: PageEditModel) -> Unit = { _, _ -> },
)

interface PdfViewportAdapter {
    fun open(document: PdfDocumentRef, defaultPage: Int, callbacks: PdfViewportCallbacks)
    fun renderDocumentState(
        pages: List<PageModel>,
        formDocument: FormDocumentModel,
        selection: SelectionModel,
        activeTool: AnnotationTool,
        searchHits: List<SearchHit>,
        selectedTextBlocks: List<ExtractedTextBlock>,
        callbacks: PdfViewportCallbacks,
    )
    fun recycle()
}

