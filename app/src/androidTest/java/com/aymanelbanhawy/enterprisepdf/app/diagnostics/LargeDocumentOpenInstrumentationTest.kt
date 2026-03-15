package com.aymanelbanhawy.enterprisepdf.app.diagnostics

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeDocumentOpenInstrumentationTest {
    @Test
    fun openLargeDocument_andCollectDiagnostics() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val pdf = File(context.cacheDir, "instrumented-large.pdf")
        createLargePdf(pdf)

        val opened = runBlocking {
            container.documentRepository.open(OpenDocumentRequest.FromFile(pdf.absolutePath, displayNameOverride = pdf.name))
        }
        val snapshot = runBlocking {
            container.runtimeDiagnosticsRepository.captureSnapshot(opened)
        }

        assertThat(opened.pageCount).isEqualTo(40)
        assertThat(snapshot.lastDocumentOpenElapsedMillis).isAtLeast(0)
    }

    private fun createLargePdf(destination: File) {
        val paint = Paint().apply { textSize = 14f }
        val document = PdfDocument()
        try {
            repeat(40) { index ->
                val pageInfo = PdfDocument.PageInfo.Builder(612, 792, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawText("Instrumentation page ${index + 1}", 48f, 96f, paint)
                document.finishPage(page)
            }
            destination.outputStream().use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
    }
}
