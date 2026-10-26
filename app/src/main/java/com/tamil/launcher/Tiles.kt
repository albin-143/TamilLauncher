package com.tamil.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Place { HOME, DOCK, DRAWER, FOLDER }

@Composable
fun IconImage(vm: LauncherViewModel, pkg: String, sizeDp: Int) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, pkg, px, vm.iconPack) {
        value = withContext(Dispatchers.Default) { IconStore.get(ctx, pkg, px) }
    }
    Box(Modifier.size(sizeDp.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(vm: LauncherViewModel, pkg: String, place: Place, folderId: String? = null) {
    val ctx = LocalContext.current
    val info = vm.appMap[pkg] ?: return
    var menu by remember { mutableStateOf(false) }
    val onWall = place == Place.HOME || place == Place.DOCK
    val fg = if (onWall) Color.White else MaterialTheme.colorScheme.onBackground
    val sh = if (onWall) TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)) else TextStyle.Default
    val shortcuts by produceState(emptyList<android.content.pm.ShortcutInfo>(), menu) {
        value = if (menu) withContext(Dispatchers.IO) { loadShortcuts(ctx, pkg) } else emptyList()
    }

    @Composable
    fun mi(id: Int, f: () -> Unit) =
        DropdownMenuItem(text = { Text(stringResource(id)) }, onClick = { menu = false; f() })

    Box(
        Modifier.combinedClickable(onClick = { vm.open(ctx, pkg) }, onLongClick = { menu = true }).padding(4.dp),
        Alignment.Center
    ) {
        if (vm.minimal) {
            Text(
                info.label, color = fg, style = sh, maxLines = 1,
                fontSize = if (place == Place.DOCK) 14.sp else 22.sp,
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
            )
        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                IconImage(vm, pkg, vm.iconDp)
                val n = if (vm.badgesOn) BadgeStore.counts[pkg] ?: 0 else 0
                if (n > 0) Box(Modifier.align(Alignment.TopEnd).size(16.dp).background(Color.Red, CircleShape), Alignment.Center) {
                    Text(if (n > 9) "9+" else "$n", color = Color.White, fontSize = 9.sp)
                }
            }
            if (vm.showLabels && place != Place.DOCK)
                Text(info.label, color = fg, style = sh, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        DropdownMenu(menu, { menu = false }) {
            shortcuts.forEach { s ->
                DropdownMenuItem(text = { Text(s.shortLabel?.toString() ?: "") }, onClick = { menu = false; startShortcut(ctx, s) })
            }
            if (place == Place.DRAWER) { mi(R.string.add_home) { vm.addToHome(pkg) }; mi(R.string.add_dock) { vm.addToDock(pkg) }; mi(R.string.add_folder) { vm.folderTarget = pkg } }
            if (place == Place.HOME) { mi(R.string.remove_home) { vm.removeFromHome(pkg) }; mi(R.string.add_dock) { vm.addToDock(pkg) }; mi(R.string.add_folder) { vm.folderTarget = pkg } }
            if (place == Place.DOCK) mi(R.string.remove_dock) { vm.removeFromDock(pkg) }
            if (place == Place.FOLDER && folderId != null) mi(R.string.take_out) { vm.takeOutOfFolder(folderId, pkg) }
            mi(R.string.app_info) { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) }
            mi(R.string.uninstall) { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }
            mi(R.string.hide) { vm.toggleHidden(pkg) }
            mi(if (pkg in vm.locked) R.string.unlock_app else R.string.lock_app) { vm.toggleLocked(pkg) }
        }
    }
}

@Composable
fun FolderTile(vm: LauncherViewModel, f: HomeItem.Folder) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(vm.homeSignal) { open = false }
    Column(Modifier.clickable { open = true }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val s = vm.iconDp
        Box(Modifier.size(s.dp).background(Color.White.copy(0.28f), RoundedCornerShape(14.dp)).padding(4.dp)) {
            Column(verticalArrangement = Arrangement.SpaceEvenly) {
                f.apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        row.forEach { IconImage(vm, it, (s - 14) / 2) }
                    }
                }
            }
        }
        if (vm.showLabels && !vm.minimal)
            Text(f.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)))
        if (vm.minimal) Text(f.name, color = Color.White, fontSize = 22.sp)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(f.name) },
        text = {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 360.dp)) {
                items(f.apps, key = { it }) { AppTile(vm, it, Place.FOLDER, f.id) }
            }
        },
        confirmButton = { TextButton({ open = false }) { Text(stringResource(R.string.ok)) } }
    )
}

@Composable
fun FolderPickerDialog(vm: LauncherViewModel, pkg: String) {
    var name by remember { mutableStateOf("") }
    val folders = vm.pages.flatten().filterIsInstance<HomeItem.Folder>()
    AlertDialog(
        onDismissRequest = { vm.folderTarget = null },
        title = { Text(stringResource(R.string.add_folder)) },
        text = {
            Column {
                folders.forEach { f -> TextButton({ vm.addToFolder(f.id, pkg); vm.folderTarget = null }) { Text(f.name) } }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ vm.createFolder(pkg, name); vm.folderTarget = null }) { Text(stringResource(R.string.new_folder)) } },
        dismissButton = { TextButton({ vm.folderTarget = null }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun PinDialog(title: String, error: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                t, { t = it.filter(Char::isDigit) }, singleLine = true, isError = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                supportingText = { if (error) Text(stringResource(R.string.wrong_pin)) }
            )
        },
        confirmButton = { TextButton({ onDone(t) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Fingerprint first, PIN as fallback. Shown whenever vm.pendingAuth is set. */
@Composable
fun AuthHost(vm: LauncherViewModel) {
    val req = vm.pendingAuth ?: return
    val act = LocalContext.current as FragmentActivity
    var showPin by remember(req) { mutableStateOf(false) }
    var wrong by remember(req) { mutableStateOf(false) }

    LaunchedEffect(req) {
        val can = BiometricManager.from(act).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) { showPin = true; return@LaunchedEffect }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(act.getString(R.string.auth_title))
            .setNegativeButtonText(act.getString(R.string.use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.pendingAuth = null; req.onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { showPin = true }
        }).authenticate(info)
    }
    if (showPin) PinDialog(
        stringResource(R.string.enter_pin), wrong,
        onDone = { if (vm.checkPin(it)) { vm.pendingAuth = null; req.onSuccess() } else wrong = true },
        onDismiss = { vm.pendingAuth = null }
    )
}
