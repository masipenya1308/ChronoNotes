package com.chrononotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.chrononotes.app.data.BOARD_COLUMNS
import com.chrononotes.app.ui.components.TagChip
import com.chrononotes.app.ui.navigation.openNote
import com.chrononotes.app.viewmodel.NotesViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(vm: NotesViewModel, navController: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // В состоянии хранится только id перетаскиваемой карточки (2 изменения за drag).
    // Сам offset читается внутри graphicsLayer — это обновляет render node без рекомпозиции экрана.
    var draggingId by remember { mutableStateOf<Long?>(null) }
    val dragOffset = remember { mutableStateOf(Offset.Zero) }
    val cardBounds = remember { mutableStateMapOf<Long, Rect>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Доска заметок") },
                actions = {
                    Text(
                        "Перетаскивайте карточки между колонками",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.openNote(-1L) }) {
                Icon(Icons.Default.Add, contentDescription = "Новая заметка")
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val columnWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                (maxWidth / BOARD_COLUMNS.size).toPx()
            }
            Row(Modifier.fillMaxSize()) {
                BOARD_COLUMNS.forEach { column ->
                    val notes = ui.notes.filter {
                        it.note.boardColumn == column.id && (!it.note.isEncrypted || ui.vaultUnlocked)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            "${column.title} · ${notes.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        notes.forEach { entry ->
                            val active = draggingId == entry.note.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = if (active) 12.dp else 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .onGloballyPositioned { coords ->
                                        val position = coords.positionInRoot()
                                        val size = coords.size
                                        val rect = Rect(
                                            offset = position,
                                            size = androidx.compose.ui.geometry.Size(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                            ),
                                        )
                                        // Не перезаписываем карту без изменений — меньше лишних аллокаций.
                                        if (cardBounds[entry.note.id] != rect) {
                                            cardBounds[entry.note.id] = rect
                                        }
                                    }
                                    .graphicsLayer {
                                        // Snapshot-чтение здесь обновляет render node без рекомпозиции экрана.
                                        translationX = if (active) dragOffset.value.x else 0f
                                        translationY = if (active) dragOffset.value.y else 0f
                                        val scale = if (active) 1.03f else 1f
                                        scaleX = scale
                                        scaleY = scale
                                        shadowElevation = if (active) 18f else 0f
                                    }
                                    .pointerInput(entry.note.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragOffset.value = Offset.Zero
                                                draggingId = entry.note.id
                                            },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                dragOffset.value += delta
                                            },
                                            onDragEnd = {
                                                val currentOffset = dragOffset.value
                                                val rect = cardBounds[entry.note.id]
                                                if (rect != null) {
                                                    val centerX = rect.center.x + currentOffset.x
                                                    val centerY = rect.center.y + currentOffset.y
                                                    val targetIndex = (centerX / columnWidthPx)
                                                        .roundToInt()
                                                        .coerceIn(0, BOARD_COLUMNS.lastIndex)
                                                    val targetColumn = BOARD_COLUMNS[targetIndex].id
                                                    val position = ui.notes.count { other ->
                                                        other.note.id != entry.note.id &&
                                                            other.note.boardColumn == targetColumn &&
                                                            (cardBounds[other.note.id]?.center?.y ?: Float.MAX_VALUE) < centerY
                                                    }
                                                    vm.moveNote(entry.note.id, targetColumn, position)
                                                }
                                                draggingId = null
                                                dragOffset.value = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffset.value = Offset.Zero
                                            },
                                        )
                                    },
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        entry.note.title.ifBlank { "Без названия" },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val preview = vm.plainOf(entry).lines()
                                        .filter { it.isNotBlank() && !it.startsWith("#") }
                                        .take(2)
                                        .joinToString(" ")
                                    if (preview.isNotBlank()) {
                                        Text(
                                            preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                    if (entry.tags.isNotEmpty()) {
                                        Row(Modifier.padding(top = 8.dp)) {
                                            entry.tags.take(2).forEach { TagChip(it) }
                                        }
                                    }
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().height(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (active) {
                                    Text(
                                        "Отпустите, чтобы переместить",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
