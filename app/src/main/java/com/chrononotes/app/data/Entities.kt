package com.chrononotes.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

const val COLUMN_INBOX = "inbox"
const val COLUMN_PROGRESS = "progress"
const val COLUMN_DONE = "done"

val BOARD_COLUMNS = listOf(
    ColumnDef(COLUMN_INBOX, "Идеи"),
    ColumnDef(COLUMN_PROGRESS, "В работе"),
    ColumnDef(COLUMN_DONE, "Готово"),
)

data class ColumnDef(val id: String, val title: String)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** Для зашифрованных заметок здесь хранится шифротекст (AES-GCM). */
    val content: String = "",
    val isPinned: Boolean = false,
    val isEncrypted: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val colorHex: String? = null,
    val boardColumn: String = COLUMN_INBOX,
    val position: Int = 0,
    /** Дата заметки в календаре: LocalDate.toEpochDay(), null — без даты. */
    val noteDate: Long? = null,
    /** Напоминание в миллисекундах (epoch), null — не установлено. */
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "note_versions")
data class NoteVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val content: String,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#5B7CFA",
)

@Entity(tableName = "note_tags", primaryKeys = ["noteId", "tagId"])
data class NoteTagCrossRef(val noteId: Long, val tagId: Long)

data class NoteWithTags(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "id",
        entityColumn = "tagId",
        associateBy = Junction(NoteTagCrossRef::class),
    )
    val tags: List<Tag> = emptyList(),
)

/** Черновик для сохранения: открытый текст + метаданные. */
data class NoteDraft(
    val id: Long = 0,
    val title: String,
    val content: String,
    val tagIds: Set<Long> = emptySet(),
    val colorHex: String? = null,
    val boardColumn: String = COLUMN_INBOX,
    val pinned: Boolean = false,
    val encrypted: Boolean = false,
    val archived: Boolean = false,
    val noteDate: Long? = null,
    val reminderAt: Long? = null,
) {
    companion object {
        fun from(note: Note, plainContent: String, tagIds: Set<Long>) = NoteDraft(
            id = note.id,
            title = note.title,
            content = plainContent,
            tagIds = tagIds,
            colorHex = note.colorHex,
            boardColumn = note.boardColumn,
            pinned = note.isPinned,
            encrypted = note.isEncrypted,
            archived = note.isArchived,
            noteDate = note.noteDate,
            reminderAt = note.reminderAt,
        )
    }
}
