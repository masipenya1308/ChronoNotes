package com.chrononotes.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.chrononotes.app.data.BOARD_COLUMNS
import com.chrononotes.app.data.COLUMN_INBOX
import com.chrononotes.app.data.NoteDraft
import com.chrononotes.app.data.NoteWithTags
import com.chrononotes.app.ui.components.MarkdownText
import com.chrononotes.app.ui.theme.TagColors
import com.chrononotes.app.ui.theme.hexToColor
import com.chrononotes.app.viewmodel.NotesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WORD_SPLIT = Regex("\\s+")
private val EDIT_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val EDIT_REMINDER_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    vm: NotesViewModel,
    navController: NavController,
    noteId: Long,
    fromVault: Boolean,
    initialDate: Long? = null,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val entry = remember(noteId, ui.notes, ui.trashed) {
        ui.notes.firstOrNull { it.note.id == noteId }
            ?: ui.trashed.firstOrNull { it.id == noteId }?.let { NoteWithTags(it) }
    }
    val isLocked = entry?.note?.isEncrypted == true && !ui.vaultUnlocked

    var title by remember(noteId) { mutableStateOf(entry?.note?.title.orEmpty()) }
    var body by remember(noteId) {
        mutableStateOf(
            entry?.let { if (it.note.isEncrypted && !ui.vaultUnlocked) "" else vm.plainOf(it) }.orEmpty(),
        )
    }
    var pinned by remember(noteId) { mutableStateOf(entry?.note?.isPinned ?: false) }
    var encrypted by remember(noteId) {
        mutableStateOf(fromVault || entry?.note?.isEncrypted ?: false)
    }
    var colorHex by remember(noteId) { mutableStateOf(entry?.note?.colorHex) }
    var column by remember(noteId) { mutableStateOf(entry?.note?.boardColumn ?: COLUMN_INBOX) }
    var selectedTags by remember(noteId) {
        mutableStateOf(entry?.tags?.map { it.id }?.toSet() ?: emptySet())
    }
    var noteDate by remember(noteId) {
        mutableStateOf(entry?.note?.noteDate ?: initialDate)
    }
    var reminderAt by remember(noteId) { mutableStateOf(entry?.note?.reminderAt) }

    // Deep-link может открыть экран раньше, чем Flow выдаст заметку/разблокирует сейф.
    LaunchedEffect(entry?.note?.id, ui.vaultUnlocked) {
        if (entry != null && !isLocked) body = vm.plainOf(entry)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderDate by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    var pendingReminderDate by remember { mutableStateOf<LocalDate?>(null) }
    var preview by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    fun commit() {
        // Заблокированный сейф: ни в коем случае не сохраняем placebo-текст вместо заметки.
        if (done || isLocked) return
        // Заметка ещё не загрузилась из Flow — сохранять нечего.
        if (noteId > 0 && entry == null) return
        if (title.isBlank() && body.isBlank() && noteId <= 0) return
        done = true
        vm.save(
            NoteDraft(
                id = noteId,
                title = title,
                content = body,
                tagIds = selectedTags,
                colorHex = colorHex,
                boardColumn = column,
                pinned = pinned,
                encrypted = encrypted,
                archived = entry?.note?.isArchived ?: false,
                isTrashed = entry?.note?.isTrashed ?: false,
                noteDate = noteDate,
                reminderAt = reminderAt,
            ),
        )
    }

    fun leave() {
        commit()
        navController.popBackStack()
    }

    DisposableEffect(Unit) { onDispose { commit() } }
    BackHandler { leave() }

    val words = body.split(WORD_SPLIT).count { it.isNotBlank() }

    if (isLocked) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Сейф заблокирован") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text(
                    "Эта заметка находится в сейфе. Откройте сейф, чтобы посмотреть и отредактировать её.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { navController.navigate("vault") }) {
                    Text("Открыть сейф")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId > 0) "Редактирование" else "Новая заметка") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            if (preview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Предпросмотр Markdown",
                        )
                    }
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Закрепить",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (entry != null) {
                        IconButton(onClick = { navController.navigate("history/$noteId") }) {
                            Icon(Icons.Default.History, contentDescription = "История версий")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (colorHex != null) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp)
                        .clip(CircleShape)
                        .background(hexToColor(colorHex!!)),
                ) {}
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Заголовок", style = MaterialTheme.typography.titleLarge) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            if (preview) {
                MarkdownText(
                    text = body.ifBlank { "Здесь появится предпросмотр Markdown." },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    onToggleTask = { index -> body = toggleTaskInText(body, index) },
                )
            } else {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Напишите заметку…\n\nПоддерживается Markdown: **жирный**, - [ ] задачи, # заголовки") },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        noteDate?.let { LocalDate.ofEpochDay(it).format(EDIT_DATE_FORMAT) } ?: "Без даты",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (noteDate != null) {
                    TextButton(onClick = { noteDate = null }) {
                        Text("Убрать", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                TextButton(onClick = { showReminderDate = true }) {
                    Text(
                        reminderAt?.let { formatDateTime(it) } ?: "Напомнить",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (reminderAt != null) {
                    TextButton(onClick = { reminderAt = null }) {
                        Text("✕", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showDatePicker) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = noteDate?.let {
                        LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    },
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            noteDate = pickerState.selectedDateMillis?.let { millis ->
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                                    .toLocalDate().toEpochDay()
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
                    },
                ) { DatePicker(state = pickerState) }
            }

            if (showReminderDate) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = reminderAt ?: noteDate?.let {
                        LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } ?: System.currentTimeMillis(),
                )
                DatePickerDialog(
                    onDismissRequest = { showReminderDate = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingReminderDate = pickerState.selectedDateMillis?.let { millis ->
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            showReminderDate = false
                            showReminderTime = true
                        }) { Text("Далее") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReminderDate = false }) { Text("Отмена") }
                    },
                ) { DatePicker(state = pickerState) }
            }

            if (showReminderTime) {
                val timeState = rememberTimePickerState()
                AlertDialog(
                    onDismissRequest = { showReminderTime = false },
                    title = { Text("Время напоминания") },
                    text = { TimePicker(state = timeState) },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingReminderDate?.let { date ->
                                reminderAt = date.atTime(timeState.hour, timeState.minute)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            }
                            showReminderTime = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReminderTime = false }) { Text("Отмена") }
                    },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Сейф", style = MaterialTheme.typography.bodySmall)
                Switch(checked = encrypted, onCheckedChange = { encrypted = it })
                Text(
                    "$words слов · ${body.length} симв.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showTags = true }) {
                    Icon(Icons.Default.Tag, contentDescription = "Теги")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BOARD_COLUMNS.forEach { col ->
                    FilterChip(
                        selected = column == col.id,
                        onClick = { column = col.id },
                        label = { Text(col.title, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Цвет:", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(Color.Transparent)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { colorHex = null },
                ) {}
                TagColors.forEach { hex ->
                    val color = hexToColor(hex)
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(color)
                            .then(
                                if (colorHex == hex) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier,
                            )
                            .clickable { colorHex = hex },
                    ) {}
                }
            }
        }
    }

    if (showTags) {
        TagPickerDialog(
            tags = ui.tags,
            selected = selectedTags,
            onToggle = { id ->
                selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
            },
            onCreate = { name -> vm.createTag(name, TagColors[0]) },
            onDismiss = { showTags = false },
        )
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заметку?") },
            text = { Text("Заметка будет перемещена в корзину.") },
            confirmButton = {
                TextButton(onClick = {
                    done = true
                    vm.trash(entry.note)
                    navController.popBackStack()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun TagPickerDialog(
    tags: List<com.chrononotes.app.data.Tag>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Теги заметки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tags.forEach { tag ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(tag.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = tag.id in selected, onCheckedChange = { onToggle(tag.id) })
                        Spacer(Modifier.width(6.dp))
                        Text(tag.name)
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Новый тег") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newTag.isNotBlank()) onCreate(newTag)
                onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        .format(EDIT_REMINDER_FORMAT)

private fun toggleTaskInText(text: String, index: Int): String {
    var counter = -1
    return text.lines().joinToString("\n") { line ->
        when {
            line.startsWith("- [ ] ") -> {
                counter++
                if (counter == index) "- [x] " + line.removePrefix("- [ ] ") else line
            }
            line.startsWith("- [x] ") -> {
                counter++
                if (counter == index) "- [ ] " + line.removePrefix("- [x] ") else line
            }
            else -> line
        }
    }
}
