package com.chrononotes.app.util

import com.chrononotes.app.data.Note
import org.json.JSONArray
import org.json.JSONObject

object JsonIO {

    fun exportNotes(notes: List<Note>): String {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject()
                    .put("title", note.title)
                    .put("content", note.content)
                    .put("isEncrypted", note.isEncrypted)
                    .put("isPinned", note.isPinned)
                    .put("boardColumn", note.boardColumn)
                    .put("colorHex", note.colorHex ?: JSONObject.NULL)
                    .put("noteDate", note.noteDate ?: JSONObject.NULL)
                    .put("reminderAt", note.reminderAt ?: JSONObject.NULL)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt),
            )
        }
        return JSONObject()
            .put("app", "ChronoNotes")
            .put("formatVersion", 2)
            .put("notes", array)
            .toString(2)
    }

    fun importNotes(json: String): List<Note> {
        val root = JSONObject(json)
        val array = root.optJSONArray("notes") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val obj = array.getJSONObject(index)
                Note(
                    title = obj.optString("title"),
                    content = obj.optString("content"),
                    isEncrypted = obj.optBoolean("isEncrypted"),
                    isPinned = obj.optBoolean("isPinned"),
                    boardColumn = obj.optString("boardColumn", "inbox"),
                    colorHex = obj.optString("colorHex").takeIf { it.isNotBlank() && it != "null" },
                    noteDate = obj.optLong("noteDate", -1L).takeIf { it >= 0 },
                    reminderAt = obj.optLong("reminderAt", -1L).takeIf { it >= 0 },
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }

    fun exportMarkdown(notes: List<Note>, plain: (Note) -> String): String = buildString {
        append("# Экспорт заметок ChronoNotes\n\n")
        notes.forEach { note ->
            append("## ").append(note.title.ifBlank { "Без названия" }).append("\n\n")
            append(plain(note)).append("\n\n")
            append("---\n\n")
        }
    }
}
