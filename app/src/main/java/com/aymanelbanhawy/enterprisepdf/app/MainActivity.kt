package com.aymanelbanhawy.enterprisepdf.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorViewModel
import com.aymanelbanhawy.enterprisepdf.app.open.PdfOpenIntentResolver
import com.aymanelbanhawy.enterprisepdf.app.theme.EnterprisePdfTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels {
        val container = (application as EnterprisePdfApplication).appContainer
        EditorViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent, initialize = true)
        setContent {
            EnterprisePdfTheme {
                EnterprisePdfAppFrame {
                    MainActivityContent(
                        viewModel = viewModel,
                        onShareDocument = ::shareDocument,
                        onShareText = ::shareText,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    fun handleIncomingIntent(intent: Intent?, initialize: Boolean = false) {
        takeReadPermissionIfAvailable(intent)
        val resolved = PdfOpenIntentResolver.resolve(this, intent)
        if (initialize) {
            viewModel.initializeDocument(resolved)
        } else if (resolved != null) {
            viewModel.openIncomingDocument(resolved)
        }
    }

    private fun shareDocument(event: EditorSessionEvent.ShareDocument) {
        val sharedUri = if (event.document.documentRef.uri.scheme == "file") {
            val file = File(requireNotNull(event.document.documentRef.uri.path))
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } else {
            event.document.documentRef.uri
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, sharedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, event.document.documentRef.displayName))
    }

    private fun shareText(event: EditorSessionEvent.ShareText) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, event.text)
        }
        startActivity(Intent.createChooser(sendIntent, event.title))
    }

    private fun takeReadPermissionIfAvailable(intent: Intent?) {
        val incomingIntent = intent ?: return
        val targetUri = when (incomingIntent.action) {
            Intent.ACTION_VIEW -> incomingIntent.data
            Intent.ACTION_SEND -> incomingIntent.extraStreamUri()
            else -> null
        } ?: return
        val readFlags = incomingIntent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if ((readFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
        runCatching {
            grantUriPermission(packageName, targetUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if ((readFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                contentResolver.takePersistableUriPermission(
                    targetUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}

private fun Intent.extraStreamUri(): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
}

@Composable
private fun EnterprisePdfAppFrame(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            scheme.primaryContainer.copy(alpha = 0.2f),
            scheme.background,
            scheme.surfaceVariant.copy(alpha = 0.82f),
        ),
    )
    val glowBrush = Brush.radialGradient(
        colors = listOf(
            scheme.tertiaryContainer.copy(alpha = 0.24f),
            Color.Transparent,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(glowBrush, RoundedCornerShape(34.dp)),
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.surface.copy(alpha = 0.74f),
            contentColor = scheme.onBackground,
            shape = RoundedCornerShape(34.dp),
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
        ) {
            content()
        }
    }
}
