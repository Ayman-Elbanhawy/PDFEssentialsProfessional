package com.aymanelbanhawy.enterprisepdf.app.open

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.aymanelbanhawy.enterprisepdf.app.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 35)
class PdfOpenFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun actionView_contentPdfIntent_replacesSampleDocument() {
        val uri = createPdfUri("intent-open.pdf")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingIntent(intent)
        }

        assertDocumentTitle("intent-open.pdf")
    }

    @Test
    fun actionSend_pdfStream_replacesSampleDocument() {
        val uri = createPdfUri("shared-open.pdf")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingIntent(intent)
        }

        assertDocumentTitle("shared-open.pdf")
    }

    @Test
    fun unsupportedFileType_keepsCurrentPdfActive() {
        val uri = createTextUri("not-a-pdf.txt")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingIntent(intent)
        }

        assertDocumentTitle("sample.pdf")
    }

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun fileOpenMenu_launchesSafPickerAndOpensReturnedPdf() {
        val uri = createPdfUri("picker-open.pdf")
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(
                Activity.RESULT_OK,
                Intent().setData(uri).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                ),
            ),
        )

        assertDocumentTitle("sample.pdf")
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Open PDF").performClick()

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        assertDocumentTitle("picker-open.pdf")
    }

    private fun createPdfUri(displayName: String): android.net.Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, displayName)
        context.assets.open("sample.pdf").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun createTextUri(displayName: String): android.net.Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, displayName)
        file.writeText("not a pdf", Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun assertDocumentTitle(title: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
