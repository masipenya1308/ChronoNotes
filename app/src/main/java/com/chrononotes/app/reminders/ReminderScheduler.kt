package com.chrononotes.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chrononotes.app.data.Note

/** Планирует и отменяет напоминания через системный AlarmManager. */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(noteId: Long, atMillis: Long, title: String, encrypted: Boolean) {
        cancel(noteId)
        if (atMillis <= System.currentTimeMillis()) return
        val intent = pendingIntent(noteId, title, encrypted)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }.onFailure {
            // Если точные будильники запрещены — fallback на неточные.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    fun cancel(noteId: Long) {
        alarmManager.cancel(pendingIntent(noteId, "", false))
    }

    fun rescheduleAll(notes: List<Note>) {
        val now = System.currentTimeMillis()
        notes.forEach { note ->
            val at = note.reminderAt
            if (at != null && at > now) {
                schedule(note.id, at, note.title, note.isEncrypted)
            }
        }
    }

    private fun pendingIntent(noteId: Long, title: String, encrypted: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderConstants.ACTION_REMINDER
            putExtra(ReminderConstants.EXTRA_NOTE_ID, noteId)
            putExtra(
                ReminderConstants.EXTRA_TITLE,
                if (encrypted) "Напоминание из сейфа" else title.ifBlank { "Заметка" },
            )
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
