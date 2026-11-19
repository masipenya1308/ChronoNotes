package com.chrononotes.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chrononotes.app.ui.screens.BoardScreen
import com.chrononotes.app.ui.screens.CalendarScreen
import com.chrononotes.app.ui.screens.HistoryScreen
import com.chrononotes.app.ui.screens.NotesListScreen
import com.chrononotes.app.ui.screens.NoteEditScreen
import com.chrononotes.app.ui.screens.SettingsScreen
import com.chrononotes.app.ui.screens.TrashScreen
import com.chrononotes.app.ui.screens.VaultScreen
import com.chrononotes.app.viewmodel.NotesViewModel

private data class Tab(val route: String, val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("list", "Заметки", Icons.Default.Home),
    Tab("calendar", "Календарь", Icons.Default.Event),
    Tab("board", "Доска", Icons.Default.Dashboard),
    Tab("vault", "Сейф", Icons.Default.Lock),
    Tab("settings", "Настройки", Icons.Default.Settings),
)

@Composable
fun AppNavigation(
    vm: NotesViewModel,
    initialNoteId: Long = -1L,
    initialFromVault: Boolean = false,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route.orEmpty()
    val showBottomBar = TABS.any { it.route == currentRoute }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.error) {
        ui.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    AutoLockEffect(vm)

    LaunchedEffect(initialNoteId) {
        if (initialNoteId > 0) {
            navController.navigate("edit/$initialNoteId?vault=${if (initialFromVault) 1 else 0}")
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo("list") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier.padding(padding),
        ) {
            composable("list") { NotesListScreen(vm, navController) }
            composable("calendar") { CalendarScreen(vm, navController) }
            composable("board") { BoardScreen(vm, navController) }
            composable("vault") { VaultScreen(vm, navController) }
            composable("settings") { SettingsScreen(vm) }
            composable("trash") { TrashScreen(vm, navController) }
            composable("history/{id}") { entry ->
                HistoryScreen(vm, navController, entry.arguments?.getString("id")?.toLongOrNull() ?: -1L)
            }
            composable(
                route = "edit/{id}?vault={vault}&date={date}",
                arguments = listOf(
                    navArgument("vault") { defaultValue = "0" },
                    navArgument("date") { defaultValue = "-1" },
                ),
            ) { entry ->
                NoteEditScreen(
                    vm = vm,
                    navController = navController,
                    noteId = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L,
                    fromVault = entry.arguments?.getString("vault") == "1",
                    initialDate = entry.arguments?.getString("date")?.toLongOrNull()
                        ?.takeIf { it >= 0 },
                )
            }
        }
    }
}

@Composable
private fun AutoLockEffect(vm: NotesViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                Lifecycle.Event.ON_START -> vm.onForeground(vm.appSettings.value.autoLockMinutes)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

fun NavController.openNote(id: Long, fromVault: Boolean = false, date: Long? = null) {
    val dateArg = date?.toString() ?: "-1"
    navigate("edit/$id?vault=${if (fromVault) 1 else 0}&date=$dateArg")
}
