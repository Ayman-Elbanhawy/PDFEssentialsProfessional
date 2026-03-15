package com.aymanelbanhawy.enterprisepdf.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltipButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    }
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    }
    val animatedContainerColor = animateColorAsState(containerColor, label = "iconButtonContainer")
    val animatedBorderColor = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outlineVariant,
        label = "iconButtonBorder",
    )
    val animatedElevation = animateDpAsState(if (selected) 8.dp else 2.dp, label = "iconButtonElevation")
    val animatedScale = animateFloatAsState(if (selected) 1.02f else 1f, label = "iconButtonScale")
    val containerShape = RoundedCornerShape(if (compact) 18.dp else 22.dp)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(tooltip)
            }
        },
        state = rememberTooltipState(),
    ) {
        Surface(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .sizeIn(minWidth = if (compact) 52.dp else 58.dp, minHeight = if (compact) 52.dp else 58.dp)
                .scale(animatedScale.value)
                .semantics {
                    role = Role.Button
                    contentDescription = tooltip
                },
            shape = containerShape,
            color = animatedContainerColor.value,
            contentColor = iconTint,
            tonalElevation = animatedElevation.value,
            shadowElevation = animatedElevation.value,
            border = BorderStroke(
                width = 1.dp,
                color = animatedBorderColor.value,
            ),
        ) {
            IconButton(onClick = onClick, enabled = enabled) {
                Column {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 22.dp else 24.dp),
                        tint = LocalContentColor.current,
                    )
                    Spacer(modifier = Modifier.size(0.dp))
                }
            }
        }
    }
}
