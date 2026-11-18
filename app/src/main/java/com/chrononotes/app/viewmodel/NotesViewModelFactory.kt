package com.chrononotes.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.chrononotes.app.data.NotesRepository
import com.chrononotes.app.data.SettingsRepository

class NotesViewModelFactory(
    private val repository: NotesRepository,
    private val settings: SettingsRepository,
    private val context: Context,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            return NotesViewModel(repository, settings, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
