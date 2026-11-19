package com.chrononotes.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrononotes.app.crypto.CryptoManager
import com.chrononotes.app.data.NotesRepository
import com.chrononotes.app.data.SettingsRepository
import com.chrononotes.app.ui.navigation.AppNavigation
import com.chrononotes.app.ui.theme.ChronoTheme
import com.chrononotes.app.viewmodel.NotesViewModel
import com.chrononotes.app.viewmodel.NotesViewModelFactory

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Разрешение не обязательно для работы приложения */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as ChronoNotesApp
        val repository = NotesRepository(app.database.dao(), CryptoManager(app), app.database)
        val settingsRepository = SettingsRepository(this)
        val viewModel = ViewModelProvider(
            this,
            NotesViewModelFactory(repository, settingsRepository, applicationContext),
        )[NotesViewModel::class.java]

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val fromVault = intent.getBooleanExtra(EXTRA_VAULT, false)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            ChronoTheme(settings) {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(viewModel, noteId, fromVault)
                }
            }
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "noteId"
        const val EXTRA_VAULT = "vault"
    }
}
