package com.chrononotes.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chrononotes.app.data.NotesDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Восстанавливает напоминания после перезагрузки устройства. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val notes = NotesDatabase.get(context).dao().notesWithReminders()
                withContext(Dispatchers.Main) {
                    ReminderScheduler(context).rescheduleAll(notes)
                }
            }
            pending.finish()
        }
    }
}
