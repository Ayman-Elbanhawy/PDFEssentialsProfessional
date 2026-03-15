package com.aymanelbanhawy.enterprisepdf.app.open

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import java.io.File
import java.util.Locale

object PdfOpenIntentResolver {
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
        if (displayName.lowercase(Locale.US).endsWith(".pdf")) {
            return true
        }
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.lowercase(Locale.US)?.endsWith(".pdf") == true
        }
        val mimeType = runCatching { getType(uri) }.getOrNull()?.lowercase(Locale.US)
        return mimeType == "application/pdf"
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
