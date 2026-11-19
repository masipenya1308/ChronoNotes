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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.chrononotes.app.ui.navigation.openNote
import com.chrononotes.app.viewmodel.NotesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(vm: NotesViewModel, navController: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showChange by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сейф") },
                actions = {
                    if (ui.vaultUnlocked) {
                        IconButton(onClick = { vm.lockVault(); password = "" }) {
                            Icon(Icons.Default.Lock, contentDescription = "Заблокировать")
                        }
                        IconButton(onClick = { showChange = true }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Сменить пароль")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (ui.vaultUnlocked) {
                FloatingActionButton(onClick = { navController.openNote(-1L, fromVault = true) }) {
                    Icon(Icons.Default.Add, contentDescription = "Новая заметка в сейфе")
                }
            }
        },
    ) { padding ->
        when {
            !ui.vaultConfigured -> {
                VaultForm(
                    modifier = Modifier.padding(padding),
                    title = "Создайте сейф",
                    description = "Пароль используется для шифрования заметок (AES-256-GCM). " +
                        "Восстановить его будет невозможно — сохраните его надёжно.",
                    password = password,
                    confirm = confirm,
                    error = error,
                    onPassword = { password = it },
                    onConfirm = { confirm = it },
                    buttonText = "Создать сейф",
                    onSubmit = {
                        when {
                            password.length < 8 -> error = "Минимум 8 символов"
                            password != confirm -> error = "Пароли не совпадают"
                            else -> scope.launch {
                                vm.setupPassword(password)
                                password = ""
                                confirm = ""
                                error = null
                            }
                        }
                    },
                )
            }

            !ui.vaultUnlocked -> {
                VaultForm(
                    modifier = Modifier.padding(padding),
                    title = "Сейф заблокирован",
                    description = "Введите пароль, чтобы увидеть скрытые заметки.",
                    password = password,
                    confirm = null,
                    error = error,
                    onPassword = { password = it },
                    onConfirm = {},
                    buttonText = "Открыть сейф",
                    onSubmit = {
                        scope.launch {
                            if (vm.unlock(password)) {
                                password = ""
                                error = null
                            } else {
                                error = "Неверный пароль"
                            }
                        }
                    },
                )
            }

            else -> {
                val secretNotes = ui.notes.filter { it.note.isEncrypted }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Сейф открыт. Содержимое заметок шифруется на устройстве; " +
                                    "заголовки остаются видимыми в списке.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    items(secretNotes, key = { it.note.id }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { navController.openNote(entry.note.id, fromVault = true) },
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(entry.note.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    vm.plainOf(entry).lines().firstOrNull { it.isNotBlank() }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    if (secretNotes.isEmpty()) {
                        item {
                            Text(
                                "В сейфе пока пусто. Нажмите «+», чтобы добавить первую заметку.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    item {
                        TextButton(
                            onClick = { showRemove = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text("Сбросить пароль и расшифровать заметки")
                        }
                    }
                }
            }
        }
    }

    if (showChange) {
        ChangePasswordDialog(
            onDismiss = { showChange = false },
            onSubmit = { old, new ->
                scope.launch {
                    val ok = vm.changePassword(old, new)
                    error = if (ok) null else "Неверный текущий пароль"
                    if (ok) showChange = false
                }
            },
        )
    }

    if (showRemove) {
        AlertDialog(
            onDismissRequest = { showRemove = false },
            title = { Text("Сбросить пароль?") },
            text = { Text("Все зашифрованные заметки будут расшифрованы и станут обычными.") },
            confirmButton = {
                TextButton(onClick = { vm.removePassword(); showRemove = false }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { showRemove = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun VaultForm(
    modifier: Modifier,
    title: String,
    description: String,
    password: String,
    confirm: String?,
    error: String?,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    buttonText: String,
    onSubmit: () -> Unit,
    extraButton: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (confirm != null) {
            OutlinedTextField(
                value = confirm,
                onValueChange = onConfirm,
                label = { Text("Повторите пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text(buttonText)
        }
        if (extraButton != null) extraButton()
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена пароля") },
        text = {
            Column {
                OutlinedTextField(old, { old = it }, label = { Text("Текущий пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(new, { new = it }, label = { Text("Новый пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(repeat, { repeat = it }, label = { Text("Повторите новый") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (new.length >= 8 && new == repeat) onSubmit(old, new) else onDismiss()
            }) { Text("Сменить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
