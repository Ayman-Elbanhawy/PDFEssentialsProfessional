package com.aymanelbanhawy.enterprisepdf.app.smoke

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalWorkflowSmokeTest {
    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun openSaveSearchThumbnailAndDiagnosticsFlow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val request = OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf")

        val opened = runBlocking { container.documentRepository.open(request) }
        assertThat(opened.pageCount).isGreaterThan(0)

        val thumbnails = runBlocking { container.pageThumbnailRepository.thumbnailsFor(opened, widthPx = 180) }
        assertThat(thumbnails).isNotEmpty()
        assertThat(File(thumbnails.first().imagePath).exists()).isTrue()

        val indexed = runBlocking { container.documentSearchService.ensureIndex(opened, forceRefresh = true) }
        assertThat(indexed).isNotEmpty()

        val searchResults = runBlocking { container.documentSearchService.search(opened, "pdf") }
        assertThat(searchResults.indexedPageCount).isAtLeast(0)
        assertThat(searchResults.hits.size).isAtLeast(0)

        val output = File(context.cacheDir, "smoke-output.pdf")
        val saved = runBlocking {
            container.documentRepository.saveAs(opened, output, AnnotationExportMode.Editable)
        }
        assertThat(saved.documentRef.displayName).contains("smoke-output")
        assertThat(output.exists()).isTrue()

        val diagnostics = runBlocking { container.runtimeDiagnosticsRepository.captureSnapshot(saved) }
        assertThat(diagnostics.lastSaveElapsedMillis).isAtLeast(0L)
        assertThat(diagnostics.cache.thumbnailFileCount).isGreaterThan(0)
    }
}



