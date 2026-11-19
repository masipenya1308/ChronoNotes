package com.chrononotes.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chrononotes.app.data.Tag
import com.chrononotes.app.ui.theme.hexToColor

@Composable
fun TagChip(tag: Tag, onClick: (() -> Unit)? = null) {
    AssistChip(
        onClick = onClick ?: {},
        enabled = onClick != null,
        label = { Text(tag.name, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                androidx.compose.material.icons.Icons.Default.Tag,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = hexToColor(tag.colorHex),
            )
        },
        colors = AssistChipDefaults.assistChipColors(),
        modifier = Modifier,
    )
}

@Composable
fun SelectableTagChip(tag: Tag, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(tag.name, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                androidx.compose.material.icons.Icons.Default.Tag,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) Color.Unspecified else hexToColor(tag.colorHex),
            )
        },
    )
}
