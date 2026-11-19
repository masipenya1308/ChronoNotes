package com.chrononotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.chrononotes.app.data.Note
import com.chrononotes.app.data.NoteWithTags
import com.chrononotes.app.ui.components.TagChip
import com.chrononotes.app.ui.navigation.openNote
import com.chrononotes.app.ui.theme.hexToColor
import com.chrononotes.app.viewmodel.NotesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val REMINDER_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: NotesViewModel, navController: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val allReminders by vm.upcomingReminders.collectAsStateWithLifecycle(initialValue = emptyList())
    val reminders = remember(allReminders) {
        val now = System.currentTimeMillis()
        allReminders.filter { it.reminderAt != null && it.reminderAt!! > now }
    }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }

    val notesByDay = remember(ui.notes) {
        ui.notes
            .filter { it.note.noteDate != null }
            .groupBy { LocalDate.ofEpochDay(it.note.noteDate!!) }
    }
    val selectedNotes = notesByDay[selectedDay].orEmpty()

    val first = month.atDay(1)
    val cells: List<LocalDate?> =
        List(first.dayOfWeek.value - 1) { null } +
            (1..month.lengthOfMonth()).map { month.atDay(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(month.format(MONTH_FORMAT).replaceFirstChar { it.uppercase() }) },
                actions = {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц")
                    }
                    TextButton(onClick = {
                        month = YearMonth.now()
                        selectedDay = LocalDate.now()
                    }) { Text("Сегодня") }
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                DAY_NAMES.forEach { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            selected = date == selectedDay,
                            notes = date?.let { notesByDay[it].orEmpty() }.orEmpty(),
                            onClick = { if (date != null) selectedDay = date },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Заметки на ${selectedDay.format(DAY_FORMAT)}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            selectedNotes.forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { navController.openNote(entry.note.id, entry.note.isEncrypted) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = entry.note.colorHex?.let { hexToColor(it) }
                                ?: entry.tags.firstOrNull()?.colorHex?.let { hexToColor(it) }
                                ?: MaterialTheme.colorScheme.primary
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(
                                entry.note.title.ifBlank { "Без названия" },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                        }
                        val at = entry.note.reminderAt
                        if (at != null) {
                            Text(
                                "🔔 ${formatReminder(at)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                            )
                        }
                        if (entry.tags.isNotEmpty()) {
                            Row(Modifier.padding(top = 6.dp)) {
                                entry.tags.take(3).forEach { TagChip(it) }
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = { navController.openNote(-1L, date = selectedDay.toEpochDay()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Добавить заметку на этот день")
            }

            if (reminders.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Ближайшие напоминания", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                reminders.forEach { note ->
                    ReminderRow(note, navController)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: Boolean,
    notes: List<NoteWithTags>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.height(56.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (date != null) {
            val today = date == LocalDate.now()
            Box(
                Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            today -> MaterialTheme.colorScheme.primaryContainer
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            // Один кружок на уникальный тег — одинаковые теги у нескольких заметок не дублируются.
            val dots = notes.flatMap { it.tags }.distinctBy { it.id }.take(4)
            Row(
                Modifier.height(10.dp).padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            ) {
                if (dots.isEmpty() && notes.isNotEmpty()) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                dots.forEach { tag ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(hexToColor(tag.colorHex)))
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(note: Note, navController: NavController) {
    val at = note.reminderAt ?: return
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clickable { navController.openNote(note.id, note.isEncrypted) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(note.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatReminder(at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatReminder(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        .format(REMINDER_FORMAT)
