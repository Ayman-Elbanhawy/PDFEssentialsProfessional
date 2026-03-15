package com.aymanelbanhawy.enterprisepdf.app.forms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.forms.SignatureCapture
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.forms.SignatureStroke
import com.aymanelbanhawy.editor.core.model.NormalizedPoint

@Composable
fun SignatureCaptureDialog(
    onDismiss: () -> Unit,
    onSave: (String, SignatureKind, SignatureCapture) -> Unit,
) {
    var signerName by remember { mutableStateOf("Ayman Elbanhawy") }
    val strokeColor = MaterialTheme.colorScheme.primary
    var kind by remember { mutableStateOf(SignatureKind.Signature) }
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = strokes.isNotEmpty() && size.width > 0 && size.height > 0,
                onClick = {
                    onSave(
                        signerName,
                        kind,
                        SignatureCapture(
                            strokes = strokes.map { stroke ->
                                SignatureStroke(
                                    stroke.map { point ->
                                        NormalizedPoint(
                                            x = (point.x / size.width).coerceIn(0f, 1f),
                                            y = (point.y / size.height).coerceIn(0f, 1f),
                                        )
                                    },
                                )
                            },
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { strokes.clear() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        title = { Text("Capture signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = signerName,
                    onValueChange = { signerName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == SignatureKind.Signature, onClick = { kind = SignatureKind.Signature }, label = { Text("Signature") })
                    FilterChip(selected = kind == SignatureKind.Initials, onClick = { kind = SignatureKind.Initials }, label = { Text("Initials") })
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .onSizeChanged { size = it },
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        strokes.add(mutableListOf(offset))
                                    },
                                    onDrag = { change, _ ->
                                        if (strokes.isEmpty()) {
                                            strokes.add(mutableListOf(change.position))
                                        } else {
                                            strokes.last().add(change.position)
                                        }
                                        change.consume()
                                    },
                                )
                            },
                    ) {
                        strokes.forEach { stroke ->
                            if (stroke.isEmpty()) return@forEach
                            val path = Path().apply {
                                moveTo(stroke.first().x, stroke.first().y)
                                stroke.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path = path, color = strokeColor, style = Stroke(width = 6f))
                        }
                    }
                }
            }
        },
    )
}
