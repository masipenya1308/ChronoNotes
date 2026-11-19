package com.chrononotes.app.data

import androidx.room.withTransaction
import com.chrononotes.app.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NotesRepository(
    private val dao: NotesDao,
    private val crypto: CryptoManager,
    private val database: NotesDatabase,
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
    suspend fun trashedNotesWithReminders(): List<Note> = dao.trashedNotesWithReminders()
    fun observeTags(): Flow<List<Tag>> = dao.observeTags()
    fun observeVersions(noteId: Long): Flow<List<NoteVersion>> = dao.observeVersions(noteId)

    /** null означает, что текст расшифровать нельзя (сейф закрыт или данные повреждены). */
    fun decryptOrNull(note: Note): String? = when {
        !note.isEncrypted -> note.content
        crypto.isUnlocked -> runCatching { crypto.decrypt(note.content) }.getOrNull()
        else -> null
    }

    fun plain(note: Note): String = when {
        !note.isEncrypted -> note.content
        !crypto.isUnlocked -> "Заметка находится в сейфе"
        else -> decryptOrNull(note) ?: "Не удалось расшифровать заметку"
    }

    suspend fun save(draft: NoteDraft): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = if (draft.id > 0L) dao.getById(draft.id) else null
            val storedContent = if (draft.encrypted) crypto.encrypt(draft.content) else draft.content
            val now = System.currentTimeMillis()
            val note = Note(
                // -1 из маршрута «новая заметка» НЕ должен попадать в PRIMARY KEY.
                id = existing?.id ?: 0L,
                title = draft.title.trim(),
                content = storedContent,
                isPinned = draft.pinned,
                isEncrypted = draft.encrypted,
                isArchived = draft.archived,
                isTrashed = draft.isTrashed,
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

            if (existing == null || existing.title != note.title || existing.content != note.content) {
                dao.insertVersion(
                    NoteVersion(noteId = id, title = note.title, content = note.content, savedAt = now),
                )
                // Чистим историю только когда она реально пополнилась.
                dao.pruneVersions(now - THIRTY_DAYS_MS)
            }

            dao.unlinkAllTags(id)
            draft.tagIds.forEach { tagId -> dao.linkTag(NoteTagCrossRef(id, tagId)) }
            id
        }
    }

    suspend fun moveNote(id: Long, column: String, position: Int) =
        dao.moveNote(id, column, position)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun trash(id: Long) = dao.trashNote(id)
    suspend fun restore(id: Long) = dao.restoreNote(id)
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun deleteForever(id: Long) = database.withTransaction {
        dao.clearVersions(id)
        dao.unlinkAllTags(id)
        dao.deleteNote(id)
    }

    suspend fun emptyTrash() = database.withTransaction {
        dao.clearVersionsOfTrashed()
        dao.unlinkTagsOfTrashed()
        dao.emptyTrash()
    }

    suspend fun createTag(name: String, colorHex: String): Long =
        dao.insertTag(Tag(name = name.trim(), colorHex = colorHex))

    suspend fun deleteTag(tag: Tag) = database.withTransaction {
        dao.unlinkTagFromNotes(tag.id)
        dao.deleteTag(tag)
    }

    suspend fun allNotes(): List<Note> = dao.allNotes()

    /** Импорт сохраняет исходное представление контента (в том числе шифротекст). */
    suspend fun importNotes(notes: List<Note>) = database.withTransaction {
        notes.forEach { dao.insertNote(it.copy(id = 0L)) }
    }

    fun unlock(password: CharArray): Boolean = crypto.unlock(password)
    fun setupPassword(password: CharArray) = crypto.setPassword(password)
    fun lock() = crypto.lock()
    fun removePassword() = crypto.clearPassword()

    /**
     * Сброс сейфа: расшифровывает ВСЕ заметки и версии (включая корзину и архив).
     * Если что-то расшифровать нельзя — пароль НЕ удаляется, данные не перезаписываются.
     */
    suspend fun removePasswordSafely(): Boolean = withContext(Dispatchers.IO) {
        if (!crypto.isUnlocked) return@withContext false
        val notes = dao.allEncryptedNotes()
        val ids = notes.map { it.id }.toSet()
        val versions = dao.allVersions().filter { it.noteId in ids }
        val plainNotes = runCatching { notes.map { it to crypto.decrypt(it.content) } }
            .getOrNull() ?: return@withContext false
        val plainVersions = runCatching { versions.map { it to crypto.decrypt(it.content) } }
            .getOrNull() ?: return@withContext false
        database.withTransaction {
            plainNotes.forEach { (note, plain) ->
                dao.updateNote(note.copy(content = plain, isEncrypted = false))
            }
            plainVersions.forEach { (version, plain) ->
                dao.updateVersion(version.id, version.title, plain)
            }
            crypto.clearPassword()
        }
        true
    }

    /** Смена пароля перешифровывает все заметки сейфа, включая корзину, архив и историю версий. */
    suspend fun changePassword(old: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        if (!crypto.unlock(old)) return@withContext false

        val notes = dao.allEncryptedNotes()
        val ids = notes.map { it.id }.toSet()
        val versions = dao.allVersions().filter { it.noteId in ids }
        val plainNotes = runCatching { notes.map { it to crypto.decrypt(it.content) } }
            .getOrNull() ?: return@withContext false
        val plainVersions = runCatching { versions.map { it to crypto.decrypt(it.content) } }
            .getOrNull() ?: return@withContext false

        val result = runCatching {
            database.withTransaction {
                crypto.setPassword(new)
                plainNotes.forEach { (note, plain) ->
                    dao.updateNote(note.copy(content = crypto.encrypt(plain)))
                }
                plainVersions.forEach { (version, plain) ->
                    dao.updateVersion(version.id, version.title, crypto.encrypt(plain))
                }
            }
        }
        if (result.isFailure) {
            // Откатываем и ключ, если транзакция БД не прошла.
            runCatching { crypto.setPassword(old) }
            false
        } else {
            true
        }
    }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
