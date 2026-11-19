package com.chrononotes.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.chrononotes.app.data.Note
import com.chrononotes.app.data.NoteWithTags
import com.chrononotes.app.ui.components.SelectableTagChip
import com.chrononotes.app.ui.components.TagChip
import com.chrononotes.app.ui.navigation.openNote
import com.chrononotes.app.ui.theme.highlight
import com.chrononotes.app.viewmodel.NotesViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val CARD_DATE_FORMAT = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
private val NOTE_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(vm: NotesViewModel, navController: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    val visible = remember(ui.notes, ui.query, ui.activeTagIds, ui.vaultUnlocked) {
        ui.notes.filter { entry ->
            val textMatch = "${entry.note.title} ${vm.plainOf(entry)}".contains(ui.query, ignoreCase = true)
            val tagMatch = ui.activeTagIds.isEmpty() || entry.tags.any { it.id in ui.activeTagIds }
            val vaultMatch = !entry.note.isEncrypted || ui.vaultUnlocked
            textMatch && tagMatch && vaultMatch
        }
    }
    val pinned = visible.filter { it.note.isPinned }
    val others = visible.filterNot { it.note.isPinned }
    val lockedCount = ui.notes.count { it.note.isEncrypted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметки") },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                    IconButton(onClick = { navController.navigate("board") }) {
                        Icon(Icons.Default.ViewAgenda, contentDescription = "Доска")
                    }
                    IconButton(onClick = { navController.navigate("trash") }) {
                        Icon(Icons.Default.Delete, contentDescription = "Корзина")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.openNote(-1L) }) {
                Icon(Icons.Default.Add, contentDescription = "Новая заметка")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    label = { Text("Поиск по заметкам") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (ui.tags.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.tags.take(6).forEach { tag ->
                        SelectableTagChip(
                            tag = tag,
                            selected = tag.id in ui.activeTagIds,
                            onToggle = { vm.toggleTagFilter(tag.id) },
                        )
                    }
                }
            }

            if (lockedCount > 0 && !ui.vaultUnlocked) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { navController.navigate("vault") },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Text(
                            "В сейфе $lockedCount замет(ок) — нажмите, чтобы открыть",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (pinned.isNotEmpty()) item { SectionTitle("Закреплённые") }
                items(pinned, key = { it.note.id }) { entry ->
                    NoteCard(
                        entry = entry,
                        plain = vm.plainOf(entry),
                        query = ui.query,
                        onOpen = { navController.openNote(entry.note.id, entry.note.isEncrypted) },
                        onPin = { vm.togglePin(entry.note) },
                        onDelete = { pendingDelete = entry.note },
                    )
                }
                if (others.isNotEmpty()) item { SectionTitle("Все заметки") }
                items(others, key = { it.note.id }) { entry ->
                    NoteCard(
                        entry = entry,
                        plain = vm.plainOf(entry),
                        query = ui.query,
                        onOpen = { navController.openNote(entry.note.id, entry.note.isEncrypted) },
                        onPin = { vm.togglePin(entry.note) },
                        onDelete = { pendingDelete = entry.note },
                    )
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            "Заметок пока нет. Нажмите «+», чтобы создать первую.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить заметку?") },
            text = { Text("Заметка «${note.title.ifBlank { "Без названия" }}» попадёт в корзину.") },
            confirmButton = {
                TextButton(onClick = { vm.trash(note); pendingDelete = null }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun NoteCard(
    entry: NoteWithTags,
    plain: String,
    query: String,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val note = entry.note
    val preview = plain.lines()
        .map { it.removePrefix("- [ ] ").removePrefix("- [x] ").trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .take(3)
        .joinToString(" ")
        .ifBlank { "Пустая заметка" }

    val titleColor = MaterialTheme.colorScheme.primary
    val annotatedTitle = remember(note.title, query, titleColor) {
        val title = note.title.ifBlank { "Без названия" }
        if (query.isBlank()) Annotated(title) else highlight(title, query, titleColor)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = "Закреплено", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onPin) {
                    Icon(Icons.Default.PushPin, contentDescription = "Закрепить")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entry.tags.take(3).forEach { TagChip(it) }
                note.noteDate?.let { day ->
                    Icon(Icons.Default.Event, contentDescription = "Дата", modifier = Modifier.size(14.dp))
                    Text(
                        NOTE_DATE_FORMAT.format(LocalDate.ofEpochDay(day)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (note.reminderAt != null) {
                    Icon(Icons.Default.Alarm, contentDescription = "Напоминание", modifier = Modifier.size(14.dp))
                }
                Text(
                    formatDate(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun Annotated(text: String) = androidx.compose.ui.text.AnnotatedString(text)

private fun formatDate(timestamp: Long): String = CARD_DATE_FORMAT.format(Date(timestamp))
