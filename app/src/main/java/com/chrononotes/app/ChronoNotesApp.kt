package com.chrononotes.app

import android.app.Application
import com.chrononotes.app.data.NotesDatabase

class ChronoNotesApp : Application() {
    val database: NotesDatabase by lazy { NotesDatabase.get(this) }
}
