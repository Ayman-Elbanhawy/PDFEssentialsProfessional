package com.aymanelbanhawy.enterprisepdf.app.diagnostics

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeDocumentOpenBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun benchmarkDocumentOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val pdf = ensureLargePdf(context.cacheDir)

        benchmarkRule.measureRepeated {
            runBlocking {
                container.documentRepository.open(
                    OpenDocumentRequest.FromFile(
                        absolutePath = pdf.absolutePath,
                        displayNameOverride = pdf.name,
                    ),
                )
            }
        }
    }

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun benchmarkThumbnailGeneration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val pdf = ensureLargePdf(context.cacheDir)
        val opened = runBlocking {
            container.documentRepository.open(
                OpenDocumentRequest.FromFile(
                    absolutePath = pdf.absolutePath,
                    displayNameOverride = pdf.name,
                ),
            )
        }

        benchmarkRule.measureRepeated {
            runBlocking {
                container.pageThumbnailRepository.thumbnailsFor(opened, widthPx = 180)
            }
        }
    }

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun benchmarkSaveExport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val pdf = ensureLargePdf(context.cacheDir)
        val opened = runBlocking {
            container.documentRepository.open(
                OpenDocumentRequest.FromFile(
                    absolutePath = pdf.absolutePath,
                    displayNameOverride = pdf.name,
                ),
            )
        }
        val output = File(context.cacheDir, "benchmark-large-export.pdf")

        benchmarkRule.measureRepeated {
            runBlocking {
                container.documentRepository.saveAs(opened, output, AnnotationExportMode.Editable)
            }
        }
    }

    private fun ensureLargePdf(cacheDir: File): File {
        val pdf = File(cacheDir, "benchmark-large.pdf")
        if (!pdf.exists()) {
            createLargePdf(pdf)
        }
        return pdf
    }

    private fun createLargePdf(destination: File) {
        val paint = Paint().apply { textSize = 14f }
        val document = PdfDocument()
        try {
            repeat(60) { index ->
                val pageInfo = PdfDocument.PageInfo.Builder(612, 792, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawText("Benchmark page ${index + 1}", 48f, 96f, paint)
                page.canvas.drawText("This page exists to validate open, thumbnail, indexing, and save throughput.", 48f, 132f, paint)
                document.finishPage(page)
            }
            destination.outputStream().use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
    }
}



