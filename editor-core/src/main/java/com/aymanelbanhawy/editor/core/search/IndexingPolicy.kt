package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.model.DocumentModel

class IndexingPolicy(
    private val backgroundThreshold: Int = 24,
) {
    fun shouldIndexInBackground(document: DocumentModel): Boolean = document.pageCount >= backgroundThreshold
}
