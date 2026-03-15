package com.aymanelbanhawy.editor.core.write

import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.migration.FileLegacyEditCompatibilityBridge
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PdfWriteEnginePersistenceTest {
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        private val root = File(System.getProperty("java.io.tmpdir"), "pdf-write-engine-test").apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }

    init {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun load_readsMigratedMutationSession_afterCompatibilityBridgeUpgrade() = runBlocking {
        val pdfFile = createPdf("legacy-${System.nanoTime()}.pdf", listOf(PDRectangle.LETTER))
        val legacyCompatibilityFile = File(pdfFile.absolutePath + FileLegacyEditCompatibilityBridge.legacySuffix())
        legacyCompatibilityFile.writeText(
            """
            {"documentKey":"${pdfFile.absolutePath.replace("\\", "\\\\")}","editObjects":[{"_type":"com.aymanelbanhawy.editor.core.model.TextBoxEditModel","id":"text-1","pageIndex":0,"bounds":{"left":0.1,"top":0.1,"right":0.4,"bottom":0.2},"rotationDegrees":0.0,"opacity":1.0,"text":"Legacy","fontFamily":"Sans","fontSizeSp":16.0,"textColorHex":"#202124","alignment":"Start","lineSpacingMultiplier":1.2}],"updatedAtEpochMillis":1}
            """.trimIndent(),
        )
        val bridge = FileLegacyEditCompatibilityBridge(json)
        val engine = PdfBoxWriteEngine(context)
        val ref = PdfDocumentRef(
            uriString = pdfFile.toURI().toString(),
            displayName = pdfFile.name,
            sourceType = DocumentSourceType.File,
            sourceKey = pdfFile.absolutePath,
            workingCopyPath = pdfFile.absolutePath,
        )

        bridge.upgradeIfNeeded(ref)
        val loaded = engine.load(ref)

        assertThat(loaded.editObjectsByPage[0]).hasSize(1)
        assertThat((loaded.editObjectsByPage[0]!!.single() as TextBoxEditModel).text).isEqualTo("Legacy")
        assertThat(File(pdfFile.absolutePath + ".mutations.json").exists()).isTrue()
        assertThat(legacyCompatibilityFile.exists()).isFalse()
        assertThat(File(pdfFile.absolutePath + FileLegacyEditCompatibilityBridge.legacySuffix() + ".legacy-migrated").exists()).isTrue()
    }

    @Test
    fun persist_writesStructuralMutationsAndTransactionLog_withoutRecreatingLegacySidecar() = runBlocking {
        val source = createPdf("source-${System.nanoTime()}.pdf", listOf(PDRectangle.LETTER, PDRectangle.A4))
        val destination = File(context.filesDir, "out-${System.nanoTime()}.pdf")
        val imageFile = File(context.filesDir, "image-page.png")
        android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
            compress(android.graphics.Bitmap.CompressFormat.PNG, 100, imageFile.outputStream())
        }
        val document = DocumentModel(
            sessionId = "session",
            documentRef = PdfDocumentRef(
                uriString = source.toURI().toString(),
                displayName = source.name,
                sourceType = DocumentSourceType.File,
                sourceKey = source.absolutePath,
                workingCopyPath = source.absolutePath,
            ),
            pages = listOf(
                PageModel(
                    index = 0,
                    label = "1",
                    rotationDegrees = 90,
                    contentType = PageContentType.Pdf,
                    sourceDocumentPath = source.absolutePath,
                    sourcePageIndex = 1,
                    widthPoints = PDRectangle.A4.width,
                    heightPoints = PDRectangle.A4.height,
                    editObjects = listOf(TextBoxEditModel("text-1", 0, NormalizedRect(0.1f, 0.1f, 0.3f, 0.2f), text = "Persisted")),
                ),
                PageModel(
                    index = 1,
                    label = "2",
                    rotationDegrees = 0,
                    contentType = PageContentType.Blank,
                    widthPoints = 500f,
                    heightPoints = 500f,
                ),
                PageModel(
                    index = 2,
                    label = "3",
                    rotationDegrees = 0,
                    contentType = PageContentType.Image,
                    widthPoints = 300f,
                    heightPoints = 300f,
                    insertedImagePath = imageFile.absolutePath,
                    editObjects = listOf(ImageEditModel("img-1", 2, NormalizedRect(0.1f, 0.1f, 0.8f, 0.8f), imagePath = imageFile.absolutePath)),
                ),
            ),
        )
        val engine = PdfBoxWriteEngine(context)

        val result = engine.persist(document, destination, AnnotationExportMode.Editable, SaveStrategy.SaveAs)

        PDDocument.load(destination).use { saved ->
            assertThat(saved.numberOfPages).isEqualTo(3)
            assertThat(saved.getPage(0).rotation).isEqualTo(90)
            assertThat(saved.getPage(1).mediaBox.width).isEqualTo(500f)
        }
        assertThat(File(destination.absolutePath + ".mutations.json").exists()).isTrue()
        assertThat(File(destination.absolutePath + ".mutationlog.json").exists()).isTrue()
        assertThat(File(destination.absolutePath + FileLegacyEditCompatibilityBridge.legacySuffix()).exists()).isFalse()
        assertThat(File(destination.absolutePath + ".mutations.json").readText()).doesNotContain("legacyCompatibilityMigrated")
        assertThat(result.structuralMutationApplied).isTrue()
        assertThat(result.executionMode).isEqualTo(SaveExecutionMode.SaveAs)
    }

    @Test
    fun persist_restoresPdfAndMutationMetadata_whenStructuralSaveFails() = runBlocking {
        val existingPdf = createPdf("rollback-${System.nanoTime()}.pdf", listOf(PDRectangle.LETTER))
        val sessionFile = File(existingPdf.absolutePath + ".mutations.json")
        sessionFile.writeText(
            """
            {"schemaVersion":2,"documentKey":"${existingPdf.absolutePath.replace("\\", "\\\\")}","exportMode":"Editable","editObjects":[],"transactionId":"original","updatedAtEpochMillis":1,"integrity":"Verified","checksumSha256":"4f53cda18c2baa0c0354bb5f9a3ecbe5ed7c6d8fd65f4d4d7f6d1d2a2f1b0f8a"}
            """.trimIndent(),
        )
        val originalBytes = existingPdf.readBytes()
        val originalSession = sessionFile.readText()
        val engine = PdfBoxWriteEngine(context)
        val brokenDocument = DocumentModel(
            sessionId = "broken-session",
            documentRef = PdfDocumentRef(
                uriString = existingPdf.toURI().toString(),
                displayName = existingPdf.name,
                sourceType = DocumentSourceType.File,
                sourceKey = existingPdf.absolutePath,
                workingCopyPath = existingPdf.absolutePath,
            ),
            pages = listOf(
                PageModel(
                    index = 0,
                    label = "1",
                    rotationDegrees = 0,
                    contentType = PageContentType.Pdf,
                    sourceDocumentPath = File(context.filesDir, "missing-source.pdf").absolutePath,
                    sourcePageIndex = 0,
                    widthPoints = PDRectangle.LETTER.width,
                    heightPoints = PDRectangle.LETTER.height,
                ),
            ),
        )

        val failure = runCatching {
            engine.persist(brokenDocument, existingPdf, AnnotationExportMode.Editable, SaveStrategy.FullRewrite)
        }.exceptionOrNull()
        assertThat(failure).isNotNull()

        assertThat(existingPdf.readBytes()).isEqualTo(originalBytes)
        assertThat(sessionFile.readText()).isEqualTo(originalSession)
        PDDocument.load(existingPdf).use { restored ->
            assertThat(restored.numberOfPages).isEqualTo(1)
        }
    }

    @Test
    fun load_readsOlderSessionPayload_evenWhenLegacyFlagIsPresent() = runBlocking {
        val pdfFile = createPdf("legacy-session-${System.nanoTime()}.pdf", listOf(PDRectangle.LETTER))
        val engine = PdfBoxWriteEngine(context)
        val ref = PdfDocumentRef(
            uriString = pdfFile.toURI().toString(),
            displayName = pdfFile.name,
            sourceType = DocumentSourceType.File,
            sourceKey = pdfFile.absolutePath,
            workingCopyPath = pdfFile.absolutePath,
        )
        val document = DocumentModel(
            sessionId = "legacy-session",
            documentRef = ref,
            pages = listOf(
                PageModel(
                    index = 0,
                    label = "1",
                    rotationDegrees = 0,
                    contentType = PageContentType.Pdf,
                    sourceDocumentPath = pdfFile.absolutePath,
                    sourcePageIndex = 0,
                    widthPoints = PDRectangle.LETTER.width,
                    heightPoints = PDRectangle.LETTER.height,
                    editObjects = listOf(
                        TextBoxEditModel("text-1", 0, NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f), text = "Older session"),
                    ),
                ),
            ),
        )
        engine.persist(document, pdfFile, AnnotationExportMode.Editable, SaveStrategy.FullRewrite)
        val sessionFile = File(pdfFile.absolutePath + ".mutations.json")
        sessionFile.writeText(
            sessionFile.readText().replace(
                "\"integrity\":\"Verified\"",
                "\"integrity\":\"LegacyMigrated\",\"legacyCompatibilityMigrated\":true",
            ),
        )

        val loaded = engine.load(ref)

        assertThat(loaded.editObjectsByPage[0]).hasSize(1)
        assertThat((loaded.editObjectsByPage[0]!!.single() as TextBoxEditModel).text).isEqualTo("Older session")
        assertThat(loaded.integrityStatus).isEqualTo(MutationIntegrityStatus.LegacyMigrated)
    }

    private fun createPdf(name: String, pageSizes: List<PDRectangle>): File {
        val file = File(context.filesDir, name)
        PDDocument().use { document ->
            pageSizes.forEach { document.addPage(PDPage(it)) }
            document.save(file)
        }
        return file
    }
}
