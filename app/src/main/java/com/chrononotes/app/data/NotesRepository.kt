package com.chrononotes.app.data

import com.chrononotes.app.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class NotesRepository(
    private val dao: NotesDao,
    private val crypto: CryptoManager,
) {

    val hasPassword: Boolean get() = crypto.isConfigured
    val isUnlocked: Boolean get() = crypto.isUnlocked

    fun observeNotes(): Flow<List<NoteWithTags>> = dao.observeNotes()
    fun observeEncryptedNotes(): Flow<List<NoteWithTags>> = dao.observeEncryptedNotes()
    fun observeTrashed(): Flow<List<Note>> = dao.observeTrashed()
    fun observeArchived(): Flow<List<Note>> = dao.observeArchived()
    fun observeNotesForDay(day: Long): Flow<List<NoteWithTags>> = dao.observeNotesForDay(day)
    fun observeUpcomingReminders(): Flow<List<Note>> = dao.observeUpcomingReminders()
    suspend fun notesWithReminders(): List<Note> = dao.notesWithReminders()
    fun observeTags(): Flow<List<Tag>> = dao.observeTags()
    fun observeVersions(noteId: Long): Flow<List<NoteVersion>> = dao.observeVersions(noteId)

    /** Возвращает прочитаемый текст заметки, если сейф разблокирован. */
    fun plain(note: Note): String = when {
        !note.isEncrypted -> note.content
        crypto.isUnlocked -> runCatching { crypto.decrypt(note.content) }
            .getOrDefault("Не удалось расшифровать заметку")
        else -> "Заметка находится в сейфе"
    }

    suspend fun save(draft: NoteDraft): Long {
        val existing = if (draft.id > 0L) dao.getById(draft.id) else null
        val storedContent = if (draft.encrypted) crypto.encrypt(draft.content) else draft.content
        val now = System.currentTimeMillis()
        val note = Note(
            id = draft.id,
            title = draft.title.trim(),
            content = storedContent,
            isPinned = draft.pinned,
            isEncrypted = draft.encrypted,
            isArchived = draft.archived,
            boardColumn = draft.boardColumn,
            position = existing?.position ?: dao.countInColumn(draft.boardColumn),
            noteDate = draft.noteDate,
            reminderAt = draft.reminderAt,
            colorHex = draft.colorHex,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        val id = if (existing != null) {
            dao.updateNote(note)
            existing.id
        } else {
            dao.insertNote(note)
        }

        // История версий: снимок каждого нового состояния, но не для пустых сохранений.
        if (existing == null || existing.title != note.title || existing.content != note.content) {
            dao.insertVersion(
                NoteVersion(noteId = id, title = note.title, content = note.content, savedAt = now),
            )
        }
        dao.pruneVersions(now - THIRTY_DAYS_MS)

        dao.unlinkAllTags(id)
        draft.tagIds.forEach { tagId -> dao.linkTag(NoteTagCrossRef(id, tagId)) }
        return id
    }

    suspend fun moveNote(id: Long, column: String, position: Int) =
        dao.moveNote(id, column, position)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun trash(id: Long) = dao.trashNote(id)
    suspend fun restore(id: Long) = dao.restoreNote(id)
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun deleteForever(id: Long) {
        dao.clearVersions(id)
        dao.unlinkAllTags(id)
        dao.deleteNote(id)
    }
    suspend fun emptyTrash() = dao.emptyTrash()

    suspend fun createTag(name: String, colorHex: String): Long =
        dao.insertTag(Tag(name = name.trim(), colorHex = colorHex))

    suspend fun deleteTag(tag: Tag) {
        dao.unlinkTagFromNotes(tag.id)
        dao.deleteTag(tag)
    }

    suspend fun allNotes(): List<Note> = dao.allNotes()

    /** Импорт сохраняет исходное представление контента (в том числе шифротекст). */
    suspend fun importNotes(notes: List<Note>) {
        notes.forEach { dao.insertNote(it) }
    }

    fun unlock(password: CharArray): Boolean = crypto.unlock(password)
    fun setupPassword(password: CharArray) = crypto.setPassword(password)
    fun lock() = crypto.lock()
    fun removePassword() = crypto.clearPassword()

    /** Смена пароля перешифровывает все заметки сейфа новым ключом. */
    suspend fun changePassword(old: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        if (!crypto.unlock(old)) return@withContext false
        val entries = dao.observeEncryptedNotes().first()
        val decrypted = entries.map { it to crypto.decrypt(it.note.content) }
        crypto.setPassword(new)
        decrypted.forEach { (entry, plain) ->
            save(NoteDraft.from(entry.note, plain, entry.tags.map { it.id }.toSet()))
        }
        true
    }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
