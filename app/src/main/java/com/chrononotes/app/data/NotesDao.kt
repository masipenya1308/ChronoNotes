package com.chrononotes.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    @Transaction
    @Query(
        "SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 " +
            "ORDER BY isPinned DESC, position ASC, updatedAt DESC",
    )
    fun observeNotes(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 AND isEncrypted = 1")
    fun observeEncryptedNotes(): Flow<List<NoteWithTags>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashed(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isTrashed = 0")
    fun observeArchived(): Flow<List<Note>>

    @Transaction
    @Query(
        "SELECT * FROM notes WHERE noteDate = :day AND isTrashed = 0 " +
            "ORDER BY reminderAt ASC, updatedAt DESC",
    )
    fun observeNotesForDay(day: Long): Flow<List<NoteWithTags>>

    // Без параметра `now`: стабильный Flow, фильтрация по времени идёт в UI.
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND isTrashed = 0 ORDER BY reminderAt ASC")
    fun observeUpcomingReminders(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND isTrashed = 0")
    suspend fun notesWithReminders(): List<Note>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND isTrashed = 1")
    suspend fun trashedNotesWithReminders(): List<Note>

    @Query("SELECT * FROM notes WHERE isEncrypted = 1")
    suspend fun allEncryptedNotes(): List<Note>

    @Query("SELECT * FROM note_versions")
    suspend fun allVersions(): List<NoteVersion>

    @Query("UPDATE note_versions SET title = :title, content = :content WHERE id = :id")
    suspend fun updateVersion(id: Long, title: String, content: String)

    @Query("DELETE FROM note_versions WHERE noteId IN (SELECT id FROM notes WHERE isTrashed = 1)")
    suspend fun clearVersionsOfTrashed()

    @Query("DELETE FROM note_tags WHERE noteId IN (SELECT id FROM notes WHERE isTrashed = 1)")
    suspend fun unlinkTagsOfTrashed()

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Query("SELECT COUNT(*) FROM notes WHERE boardColumn = :column AND isTrashed = 0")
    suspend fun countInColumn(column: String): Int

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun observeVersions(noteId: Long): Flow<List<NoteVersion>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<Tag>>

    @Insert
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("UPDATE notes SET position = :position, boardColumn = :column WHERE id = :id")
    suspend fun moveNote(id: Long, column: String, position: Int)

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET isTrashed = 1 WHERE id = :id")
    suspend fun trashNote(id: Long)

    @Query("UPDATE notes SET isTrashed = 0 WHERE id = :id")
    suspend fun restoreNote(id: Long)

    @Query("UPDATE notes SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Insert
    suspend fun insertVersion(version: NoteVersion)

    @Query("DELETE FROM note_versions WHERE savedAt < :cutoff")
    suspend fun pruneVersions(cutoff: Long)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun clearVersions(noteId: Long)

    @Insert
    suspend fun insertTag(tag: Tag): Long

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTag(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun unlinkAllTags(noteId: Long)

    @Query("DELETE FROM note_tags WHERE tagId = :tagId")
    suspend fun unlinkTagFromNotes(tagId: Long)

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<Note>
}
