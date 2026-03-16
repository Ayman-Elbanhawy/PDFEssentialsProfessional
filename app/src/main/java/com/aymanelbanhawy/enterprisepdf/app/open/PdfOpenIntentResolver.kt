package com.aymanelbanhawy.enterprisepdf.app.open

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import java.io.BufferedInputStream
import java.io.File
import java.util.Locale

object PdfOpenIntentResolver {

    private val pdfHeader = byteArrayOf(
        '%'.code.toByte(),
        'P'.code.toByte(),
        'D'.code.toByte(),
        'F'.code.toByte(),
        '-'.code.toByte(),
    )

    private val acceptedPdfMimeTypes = setOf(
        "application/pdf",
        "application/x-pdf",
        "application/acrobat",
        "applications/vnd.pdf",
        "text/pdf",
    )

    fun resolve(context: Context, intent: Intent?): PendingPdfOpenRequest? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> resolveUri(context, intent.data, PdfOpenSource.ExternalViewIntent)
            Intent.ACTION_SEND -> resolveUri(
                context = context,
                uri = intent.extraStreamUri(),
                source = PdfOpenSource.ExternalSendIntent,
            )
            else -> null
        }
    }

    fun resolveSafSelection(context: Context, uri: Uri?): PendingPdfOpenRequest? {
        return resolveUri(context, uri, PdfOpenSource.SafPicker)
    }

    private fun resolveUri(
        context: Context,
        uri: Uri?,
        source: PdfOpenSource,
    ): PendingPdfOpenRequest? {
        if (uri == null) return null

        val displayName = context.contentResolver.displayNameFor(uri)
            ?: uri.lastPathSegment
            ?: "document.pdf"

        if (!context.contentResolver.looksLikePdf(uri, displayName)) {
            return null
        }

        val request = when (uri.scheme?.lowercase(Locale.US)) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path ?: return null
                OpenDocumentRequest.FromFile(
                    absolutePath = File(path).absolutePath,
                    displayNameOverride = displayName,
                )
            }

            ContentResolver.SCHEME_CONTENT -> {
                OpenDocumentRequest.FromUri(
                    uriString = uri.toString(),
                    displayName = displayName,
                )
            }

            else -> return null
        }

        return PendingPdfOpenRequest(
            request = request,
            source = source,
            activeUri = uri.toString(),
            displayName = displayName,
        )
    }

    private fun ContentResolver.displayNameFor(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.lastPathSegment?.substringAfterLast(File.separatorChar)
        }

        return runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                cursor.firstStringOrNull(OpenableColumns.DISPLAY_NAME)
            }
        }.getOrNull()
    }

    private fun ContentResolver.looksLikePdf(uri: Uri, displayName: String): Boolean {
        val normalizedDisplayName = displayName.lowercase(Locale.US)
        if (normalizedDisplayName.endsWith(".pdf")) {
            return true
        }

        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.lowercase(Locale.US)?.endsWith(".pdf") == true
        }

        val mimeType = runCatching { getType(uri) }
            .getOrNull()
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)

        if (mimeType in acceptedPdfMimeTypes) {
            return true
        }

        if (mimeType == null || mimeType == "application/octet-stream" || mimeType == "*/*") {
            return hasPdfHeader(uri)
        }

        return mimeType.startsWith("application/") && hasPdfHeader(uri)
    }

    private fun ContentResolver.hasPdfHeader(uri: Uri): Boolean {
        return runCatching {
            openInputStream(uri)?.use { rawStream ->
                val input =
                    if (rawStream.markSupported()) rawStream else BufferedInputStream(rawStream)
                val header = ByteArray(pdfHeader.size)
                val bytesRead = input.read(header)
                bytesRead == pdfHeader.size && header.contentEquals(pdfHeader)
            } ?: false
        }.getOrDefault(false)
    }

    private fun Cursor.firstStringOrNull(columnName: String): String? {
        val columnIndex = getColumnIndex(columnName)
        if (columnIndex < 0 || !moveToFirst()) return null
        return getString(columnIndex)
    }

    private fun Intent.extraStreamUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }
}
