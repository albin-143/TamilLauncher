package com.tamil.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect

class MainActivity : AppCompatActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // First run: default the UI to Tamil.
        if (!vm.langInit) {
            vm.langInit = true
            if (AppCompatDelegate.getApplicationLocales().isEmpty)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ta"))
        }
        setContent {
            LaunchedEffect(vm.hideNav) { applyBars() }
            LauncherTheme(vm) { Root(vm) }
        }
    }

    /** Hides the Home / Back / Recents buttons (or gesture bar). Swipe from the screen edge shows them briefly. */
    private fun applyBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vm.hideNav) c.hide(WindowInsetsCompat.Type.navigationBars())
        else c.show(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBars()
    }

    override fun onResume() {
        super.onResume()
        if (vm.kiosk && isDeviceOwner(this)) enterKiosk(this, vm.apps.map { it.pkg })
    }

    override fun onStart() { super.onStart(); try { vm.widgetHost.startListening() } catch (_: Exception) {} }
    override fun onStop() { super.onStop(); try { vm.widgetHost.stopListening() } catch (_: Exception) {} }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) { // Home button pressed
            vm.screen = LauncherViewModel.Screen.HOME
            vm.homeSignal++
        }
    }
}

@Composable
fun Root(vm: LauncherViewModel) {
    BackHandler { if (vm.screen != LauncherViewModel.Screen.HOME) vm.screen = LauncherViewModel.Screen.HOME }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(vm)
        AnimatedVisibility(
            visible = vm.screen == LauncherViewModel.Screen.DRAWER,
            enter = if (vm.animations) slideInVertically { it / 2 } + fadeIn() else EnterTransition.None,
            exit = if (vm.animations) slideOutVertically { it / 2 } + fadeOut() else ExitTransition.None
        ) { Drawer(vm) }
        if (vm.screen == LauncherViewModel.Screen.SETTINGS) SettingsScreen(vm)
        vm.folderTarget?.let { FolderPickerDialog(vm, it) }
        if (vm.homeMenu) HomeMenuDialog(vm)
        AuthHost(vm)
    }
}
