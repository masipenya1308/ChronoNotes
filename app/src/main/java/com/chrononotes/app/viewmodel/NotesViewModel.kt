package com.chrononotes.app.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chrononotes.app.data.AppSettings
import com.chrononotes.app.data.Note
import com.chrononotes.app.data.NoteDraft
import com.chrononotes.app.data.NoteVersion
import com.chrononotes.app.data.NoteWithTags
import com.chrononotes.app.data.NotesRepository
import com.chrononotes.app.data.SettingsRepository
import com.chrononotes.app.data.Tag
import com.chrononotes.app.reminders.ReminderScheduler
import com.chrononotes.app.util.JsonIO
import com.chrononotes.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewMode { LIST, BOARD }

data class UiState(
    val notes: List<NoteWithTags> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val trashed: List<Note> = emptyList(),
    val archived: List<Note> = emptyList(),
    val query: String = "",
    val activeTagIds: Set<Long> = emptySet(),
    val viewMode: ViewMode = ViewMode.LIST,
    val vaultConfigured: Boolean = false,
    val vaultUnlocked: Boolean = false,
    val decrypted: Map<Long, String> = emptyMap(),
    val error: String? = null,
)

class NotesViewModel(
    private val repo: NotesRepository,
    private val settings: SettingsRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        UiState(
            vaultConfigured = repo.hasPassword,
            vaultUnlocked = repo.isUnlocked,
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settings.settings

    private var backgroundAt: Long = 0L
    private val reminders = ReminderScheduler(appContext)

    init {
        // Перепланируем напоминания при каждом запуске приложения.
        viewModelScope.launch {
            runCatching { reminders.rescheduleAll(repo.notesWithReminders()) }
        }
        viewModelScope.launch {
            combine(
                repo.observeNotes(),
                repo.observeTags(),
                repo.observeTrashed(),
                repo.observeArchived(),
            ) { notes, tags, trashed, archived ->
                _ui.update { it.copy(notes = notes, tags = tags, trashed = trashed, archived = archived) }
            }.collect { }
        }
        refreshVault()
    }

    private fun refreshVault() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (repo.isUnlocked) {
                        repo.observeEncryptedNotes().first()
                            .associate { it.note.id to repo.plain(it.note) }
                    } else {
                        emptyMap()
                    }
                }
            }.onSuccess { decrypted ->
                _ui.update {
                    it.copy(
                        vaultConfigured = repo.hasPassword,
                        vaultUnlocked = repo.isUnlocked,
                        decrypted = decrypted,
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(error = e.message?.take(200) ?: "Ошибка сейфа") }
            }
        }
    }

    // --- Чтение -------------------------------------------------------------

    fun plainOf(entry: NoteWithTags): String =
        _ui.value.decrypted[entry.note.id] ?: repo.plain(entry.note)

    fun plainOf(note: Note): String =
        _ui.value.decrypted[note.id] ?: repo.plain(note)

    fun versions(noteId: Long): Flow<List<NoteVersion>> = repo.observeVersions(noteId)

    /** Заметки, привязанные к конкретному дню (epoch day). */
    fun notesForDay(day: Long): Flow<List<NoteWithTags>> = repo.observeNotesForDay(day)

    /** Стабильный Flow: не пересоздаётся при рекомпозиции и не дёргает БД повторно. */
    val upcomingReminders: Flow<List<Note>> = repo.observeUpcomingReminders()

    /** Текст конкретной версии (с учётом расшифровки, если сейф открыт). */
    fun plainVersion(entry: NoteWithTags, version: NoteVersion): String =
        repo.plain(entry.note.copy(content = version.content))

    // --- Фильтры и состояние интерфейса --------------------------------------

    fun setQuery(query: String) = _ui.update { it.copy(query = query) }

    fun toggleTagFilter(tagId: Long) = _ui.update { state ->
        val next = if (tagId in state.activeTagIds) state.activeTagIds - tagId else state.activeTagIds + tagId
        state.copy(activeTagIds = next)
    }

    fun clearFilters() = _ui.update { it.copy(query = "", activeTagIds = emptySet()) }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun setViewMode(mode: ViewMode) = _ui.update { it.copy(viewMode = mode) }

    // --- Заметки ------------------------------------------------------------

    fun save(draft: NoteDraft) = write(refreshVault = true) {
        val id = repo.save(draft)
        val at = draft.reminderAt
        if (at != null && at > System.currentTimeMillis()) {
            reminders.schedule(id, at, draft.title, draft.encrypted)
        } else {
            reminders.cancel(id)
        }
    }

    fun togglePin(note: Note) = write { repo.setPinned(note.id, !note.isPinned) }

    fun moveNote(id: Long, column: String, position: Int) = write(refreshWidget = false) {
        repo.moveNote(id, column, position)
    }

    fun trash(note: Note) = write {
        reminders.cancel(note.id)
        repo.trash(note.id)
    }

    fun restore(note: Note) = write {
        repo.restore(note.id)
        val at = note.reminderAt
        if (at != null && at > System.currentTimeMillis()) {
            reminders.schedule(note.id, at, note.title, note.isEncrypted)
        }
    }

    fun setArchived(note: Note, archived: Boolean) = write { repo.setArchived(note.id, archived) }

    fun deleteForever(note: Note) = write {
        reminders.cancel(note.id)
        repo.deleteForever(note.id)
    }

    fun emptyTrash() = write {
        // Отменяем напоминания только у заметок из корзины.
        repo.trashedNotesWithReminders().forEach { reminders.cancel(it.id) }
        repo.emptyTrash()
    }

    fun toggleTask(entry: NoteWithTags, index: Int) {
        val source = plainOf(entry)
        var counter = -1
        val updated = source.lines().joinToString("\n") { line ->
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
        if (updated != source) {
            save(NoteDraft.from(entry.note, updated, entry.tags.map { it.id }.toSet()))
        }
    }

    fun restoreVersion(entry: NoteWithTags, version: NoteVersion) {
        val plain = plainVersion(entry, version)
        save(NoteDraft.from(entry.note, plain, entry.tags.map { it.id }.toSet()).copy(title = version.title))
    }

    // --- Теги ----------------------------------------------------------------

    fun createTag(name: String, colorHex: String) = write(refreshWidget = false) {
        repo.createTag(name, colorHex)
    }
    fun deleteTag(tag: Tag) = write(refreshWidget = false) { repo.deleteTag(tag) }

    // --- Сейф ----------------------------------------------------------------

    // PBKDF2 — дорогая операция: только вне main-потока. Массив пароля зануляем после использования.
    suspend fun unlock(password: String): Boolean = withContext(Dispatchers.Default) {
        if (repo.lockRemainingMs > 0) {
            val seconds = (repo.lockRemainingMs + 999) / 1000
            _ui.update { it.copy(error = "Слишком много попыток. Попробуйте через $seconds с") }
            return@withContext false
        }
        val chars = password.toCharArray()
        try {
            val ok = repo.unlock(chars)
            if (ok) {
                _ui.update { it.copy(vaultUnlocked = true) }
                refreshVault()
            }
            ok
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun setupPassword(password: String) = withContext(Dispatchers.Default) {
        val chars = password.toCharArray()
        try {
            repo.setupPassword(chars)
            refreshVault()
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun changePassword(old: String, new: String): Boolean = withContext(Dispatchers.Default) {
        val oldChars = old.toCharArray()
        val newChars = new.toCharArray()
        try {
            val ok = repo.changePassword(oldChars, newChars)
            if (ok) refreshVault()
            ok
        } finally {
            oldChars.fill('\u0000')
            newChars.fill('\u0000')
        }
    }

    fun lockVault() {
        repo.lock()
        _ui.update { it.copy(vaultUnlocked = false, decrypted = emptyMap()) }
    }

    /** Сброс доступен только при открытом сейфе; при ошибке пароль и данные остаются нетронутыми. */
    fun removePassword() = write(refreshVault = true, refreshWidget = false) {
        if (!repo.isUnlocked) {
            _ui.update { it.copy(error = "Сначала откройте сейф") }
            return@write
        }
        if (!repo.removePasswordSafely()) {
            _ui.update { it.copy(error = "Не удалось расшифровать все заметки — пароль не сброшен") }
        }
    }

    private var autoLockJob: Job? = null

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
        autoLockJob?.cancel()
        // Блокируем сейф даже пока приложение в фоне, а не только при возврате.
        autoLockJob = viewModelScope.launch {
            delay(appSettings.value.autoLockMinutes * 60_000L)
            lockVault()
        }
    }

    fun onForeground(autoLockMinutes: Int) {
        autoLockJob?.cancel()
        if (backgroundAt == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - backgroundAt
        backgroundAt = 0L
        if (elapsed >= autoLockMinutes * 60_000L) lockVault()
    }

    // --- Импорт / экспорт ----------------------------------------------------

    suspend fun exportJson(): String = JsonIO.exportNotes(repo.allNotes())

    suspend fun exportMarkdown(): String = JsonIO.exportMarkdown(repo.allNotes()) { repo.plain(it) }

    fun importNotes(text: String) = write(refreshVault = true) {
        repo.importNotes(JsonIO.importNotes(text))
        reminders.rescheduleAll(repo.notesWithReminders())
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    private var widgetRefreshJob: Job? = null

    /**
     * Все операции записи идут через единую точку.
     * Сейф перешифровываем только при реальном изменении содержимого,
     * а обновление виджета дебаунсим, чтобы серия быстрых действий не вызвала шторм broadcaster'ов.
     */
    private fun write(
        refreshVault: Boolean = false,
        refreshWidget: Boolean = true,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        try {
            block()
            if (refreshVault) refreshVault()
            if (refreshWidget) {
                widgetRefreshJob?.cancel()
                widgetRefreshJob = launch {
                    delay(300)
                    WidgetUpdater.refresh(appContext)
                }
            }
        } catch (e: Exception) {
            _ui.update {
                it.copy(error = e.message?.take(200) ?: "Не удалось выполнить операцию")
            }
        }
    }
}
