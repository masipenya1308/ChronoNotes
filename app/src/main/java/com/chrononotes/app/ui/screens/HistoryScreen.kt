package com.chrononotes.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.chrononotes.app.data.NoteVersion
import com.chrononotes.app.viewmodel.NotesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VERSION_DATE_FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: NotesViewModel, navController: NavController, noteId: Long) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val versionsFlow = remember(noteId) { vm.versions(noteId) }
    val versions by versionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<NoteVersion?>(null) }
    val entry = ui.notes.firstOrNull { it.note.id == noteId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История версий") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Автосохранения за последние 30 дней. Нажмите на версию, чтобы посмотреть и восстановить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(versions, key = { it.id }) { version ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().clickable { selected = version },
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                VERSION_DATE_FORMAT.format(Date(version.savedAt)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                version.title.ifBlank { "Без названия" },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val preview = if (entry != null) vm.plainVersion(entry, version) else version.content
                            Text(
                                preview.lines().firstOrNull { it.isNotBlank() }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            if (versions.isEmpty()) {
                item {
                    Text(
                        "Версий пока нет. Они появятся после первого сохранения заметки.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    selected?.let { version ->
        val text = if (entry != null) vm.plainVersion(entry, version) else version.content
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(version.title.ifBlank { "Версия заметки" }) },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (entry != null) vm.restoreVersion(entry, version)
                    selected = null
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } },
        )
    }
}
