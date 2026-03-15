package com.aymanelbanhawy.enterprisepdf.app.scan

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.enterprisepdf.app.EnterprisePdfApplication
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrScanImportFlowTest {

    @Test
    fun importImagesCreatesPdfAndQueuesOcrJobs() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<EnterprisePdfApplication>()
        val container = application.appContainer
        val imageFile = createImage(application.cacheDir)

        val request = container.scanImportService.importImages(
            imageFiles = listOf(imageFile),
            options = ScanImportOptions(displayName = "scan-import-test.pdf"),
        )

        val fileRequest = request as OpenDocumentRequest.FromFile
        val outputFile = File(fileRequest.absolutePath)
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.name).isEqualTo("scan-import-test.pdf")

        val queued = container.ocrJobPipeline.pendingWork(
            documentKey = outputFile.absolutePath,
            limit = 8,
            staleAfterMillis = 60_000L,
        )
        assertThat(queued).isNotEmpty()
        assertThat(queued.first().documentKey).isEqualTo(outputFile.absolutePath)
    }

    private fun createImage(cacheDir: File): File {
        val bitmap = Bitmap.createBitmap(640, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val file = File(cacheDir, "ocr-scan-import-source.png")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }
}
