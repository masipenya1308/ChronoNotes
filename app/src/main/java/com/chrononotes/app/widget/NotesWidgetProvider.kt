package com.chrononotes.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chrononotes.app.MainActivity
import com.chrononotes.app.R
import com.chrononotes.app.data.NotesDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Виджет с закреплёнными заметками (до четырёх штук). */
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            CoroutineScope(Dispatchers.IO).launch {
                val pinned = runCatching {
                    NotesDatabase.get(context).dao().allNotes()
                        .filter { it.isPinned && !it.isTrashed }
                        .sortedByDescending { it.updatedAt }
                        .take(4)
                }.getOrDefault(emptyList())
                withContext(Dispatchers.Main) {
                    render(context, appWidgetManager, widgetId, pinned)
                }
            }
        }
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        notes: List<com.chrononotes.app.data.Note>,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_notes)
        views.removeAllViews(R.id.widget_container)

        if (notes.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.widget_item)
            empty.setTextViewText(R.id.widget_item_text, "Нет закреплённых заметок")
            views.addView(R.id.widget_container, empty)
        }

        notes.forEach { note ->
            val item = RemoteViews(context.packageName, R.layout.widget_item)
            val text = if (note.isEncrypted) {
                "🔒 ${note.title.ifBlank { "Без названия" }}"
            } else {
                buildString {
                    append(note.title.ifBlank { "Без названия" })
                    val body = note.content.lines()
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .joinToString(" ")
                    if (body.isNotBlank()) append("\n").append(body.take(80))
                }
            }
            item.setTextViewText(R.id.widget_item_text, text)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.chrononotes.app.OPEN_NOTE"
                putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
                putExtra(MainActivity.EXTRA_VAULT, note.isEncrypted)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                note.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            item.setOnClickPendingIntent(R.id.widget_item_root, pendingIntent)
            views.addView(R.id.widget_container, item)
        }

        manager.updateAppWidget(widgetId, views)
    }
}
