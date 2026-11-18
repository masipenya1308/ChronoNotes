package com.chrononotes.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrononotes.app.ui.theme.AccentColors
import com.chrononotes.app.viewmodel.NotesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NotesViewModel) {
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val data = vm.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                Toast.makeText(context, "Экспорт JSON готов", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val data = vm.exportMarkdown()
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                Toast.makeText(context, "Экспорт Markdown готов", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (text != null) {
                    vm.importNotes(text)
                    Toast.makeText(context, "Заметки импортированы", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard("Оформление") {
                Text("Тема", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("Системная", "Светлая", "Тёмная").forEachIndexed { index, label ->
                        RadioButton(
                            selected = settings.themeMode == index,
                            onClick = { vm.updateSettings { it.copy(themeMode = index) } },
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Акцент", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentColors.forEachIndexed { index, color ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(color)
                                .then(
                                    if (settings.accent == index) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else Modifier,
                                )
                                .clickable { vm.updateSettings { it.copy(accent = index) } },
                        )
                    }
                }
                Text(
                    "Размер текста: ${String.format("%.0f%%", settings.fontScale * 100)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.fontScale,
                    onValueChange = { vm.updateSettings { s -> s.copy(fontScale = it) } },
                    valueRange = 0.7f..1.6f,
                    steps = 8,
                )
            }

            SettingsCard("Безопасность") {
                Text("Блокировка сейфа при выходе из приложения", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 5, 15).forEach { minutes ->
                        FilterChip(
                            selected = settings.autoLockMinutes == minutes,
                            onClick = { vm.updateSettings { it.copy(autoLockMinutes = minutes) } },
                            label = { Text(if (minutes == 0) "Сразу" else "$minutes мин") },
                        )
                    }
                }
            }

            SettingsCard("Данные") {
                Text(
                    "Экспорт и импорт работают через системный выбор файла. " +
                        "Шифротекст сохраняется как есть — для чтения понадобится ваш пароль.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { exportJsonLauncher.launch("chrononotes-backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Экспорт в JSON") }
                OutlinedButton(
                    onClick = { exportMdLauncher.launch("chrononotes.md") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Экспорт в Markdown") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Импорт из JSON") }
            }

            SettingsCard("О приложении") {
                Text("ChronoNotes", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Аналог EasyNotes с расширенными возможностями: Markdown с интерактивными " +
                        "чек-листами, теги, закрепление, шифрованный сейф, история версий, " +
                        "канбан-доска с drag-and-drop, корзина и виджет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
