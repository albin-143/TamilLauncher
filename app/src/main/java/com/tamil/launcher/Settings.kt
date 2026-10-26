package com.tamil.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.util.Date

private val ACCENTS = listOf(0xFF6750A4, 0xFF1E88E5, 0xFF43A047, 0xFFE53935, 0xFFFB8C00, 0xFFD81B60, 0xFF00ACC1)

@Composable private fun s(id: Int) = stringResource(id)

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))

@Composable
private fun SwitchRow(label: String, v: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(v, onChange)
    }

@Composable
private fun SliderRow(label: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) =
    Column { Text(label); Slider(v, onChange, valueRange = range, steps = steps) }

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) =
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected == i, { onSelect(i) }, { Text(o) }) }
        }
    }

@Composable
private fun AppPickerDialog(vm: LauncherViewModel, title: String, selected: Set<String>, onToggle: (String) -> Unit, onClose: () -> Unit) =
    AlertDialog(
        onDismissRequest = onClose, title = { Text(title) },
        confirmButton = { TextButton(onClose) { Text(s(R.string.ok)) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(vm.apps, key = { it.pkg }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { onToggle(a.pkg) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(a.pkg in selected, { onToggle(a.pkg) }); Text(a.label)
                    }
                }
            }
        }
    )

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    var dialog by remember { mutableStateOf("") } // hidden | locked | focus | pin | pack | widget
    var pendingWidget by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    // ---- backup / restore
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.toast(
            if (runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportJson().toByteArray()) } }.isSuccess)
                R.string.backup_done else R.string.backup_fail
        )
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.toast(
            if (runCatching { vm.importJson(ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()) }.getOrDefault(false))
                R.string.backup_done else R.string.backup_fail
        )
    }

    // ---- widgets: bind -> (configure) -> add
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingWidget; pendingWidget = null
        if (p != null) { if (r.resultCode == Activity.RESULT_OK) vm.addWidget(p.first) else vm.widgetHost.deleteAppWidgetId(p.first) }
    }
    fun afterBind(id: Int, p: AppWidgetProviderInfo) {
        if (p.configure != null) {
            pendingWidget = id to p
            configure.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(p.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } else vm.addWidget(id)
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingWidget
        if (p != null) { if (r.resultCode == Activity.RESULT_OK) afterBind(p.first, p.second) else { vm.widgetHost.deleteAppWidgetId(p.first); pendingWidget = null } }
    }

    Surface(Modifier.fillMaxSize().systemBarsPadding().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.screen = LauncherViewModel.Screen.HOME }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(s(R.string.settings), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {

                Section(s(R.string.s_look))
                Choice(s(R.string.s_theme), listOf(s(R.string.theme_system), s(R.string.theme_light), s(R.string.theme_dark), s(R.string.theme_amoled)), vm.theme) { vm.theme = it }
                Text(s(R.string.s_accent), Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { c ->
                        Box(Modifier.size(34.dp).background(Color(c), CircleShape).clickable { vm.accent = c.toInt() }
                            .then(if (vm.accent == c.toInt()) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier))
                    }
                }
                Choice(s(R.string.s_font), listOf(s(R.string.font_default), s(R.string.font_serif), s(R.string.font_mono), s(R.string.font_cursive)), vm.fontIdx) { vm.fontIdx = it }
                SliderRow("${s(R.string.s_icon_size)}: ${vm.iconDp}dp", vm.iconDp.toFloat(), 40f..72f, 7) { vm.iconDp = it.toInt() }
                SwitchRow(s(R.string.s_labels), vm.showLabels) { vm.showLabels = it }
                SwitchRow(s(R.string.s_minimal), vm.minimal) { vm.minimal = it }
                SwitchRow(s(R.string.s_hide_nav), vm.hideNav) { vm.hideNav = it }
                SwitchRow(s(R.string.s_anim), vm.animations) { vm.animations = it }
                Choice(s(R.string.s_transition), listOf(s(R.string.tr_slide), s(R.string.tr_zoom), s(R.string.tr_rotate)), vm.transition) { vm.transition = it }
                SliderRow(s(R.string.s_dim), vm.dim, 0.3f..1f) { vm.dim = it }
                OutlinedButton({ dialog = "pack" }) { Text(s(R.string.s_icon_pack)) }
                OutlinedButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), s0(ctx, R.string.wallpaper))) }) { Text(s(R.string.wallpaper)) }

                Section(s(R.string.s_grid))
                SliderRow(stringResource(R.string.s_cols, vm.cols), vm.cols.toFloat(), 3f..6f, 2) { vm.cols = it.toInt(); vm.reflow() }
                SliderRow(stringResource(R.string.s_rows, vm.rows), vm.rows.toFloat(), 4f..8f, 3) { vm.rows = it.toInt(); vm.reflow() }

                Section(s(R.string.s_drawer))
                Choice(s(R.string.s_sort), listOf(s(R.string.sort_az), s(R.string.sort_usage), s(R.string.sort_cat)), vm.sortMode) { vm.sortMode = it }

                Section(s(R.string.s_privacy))
                OutlinedButton({ dialog = "pin" }) { Text(s(R.string.set_pin)) }
                OutlinedButton({ vm.requireAuth { dialog = "hidden" } }) { Text(s(R.string.s_hidden)) }
                OutlinedButton({ if (vm.pinHash.isEmpty()) vm.toast(R.string.pin_set_first) else dialog = "locked" }) { Text(s(R.string.s_locked)) }

                Section(s(R.string.s_focus))
                if (vm.focusActive()) {
                    Text(stringResource(R.string.focus_on_until, DateFormat.getTimeFormat(ctx).format(Date(vm.focusUntil))))
                    OutlinedButton({ vm.stopFocus() }) { Text(s(R.string.focus_stop)) }
                } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { m -> Button({ vm.startFocus(m) }) { Text(stringResource(R.string.focus_min, m)) } }
                }
                OutlinedButton({ dialog = "focus" }) { Text(s(R.string.s_focus_apps)) }

                Section(s(R.string.s_gestures))
                SwitchRow(s(R.string.s_dbl), vm.dblLock) { vm.dblLock = it }
                if (!isAdminActive(ctx)) OutlinedButton({ requestAdmin(ctx) }) { Text(s(R.string.s_admin)) }
                SwitchRow(s(R.string.s_swipe_down), vm.swipeNotif) { vm.swipeNotif = it }
                SwitchRow(s(R.string.s_gesturenav), vm.gestureNav) { vm.gestureNav = it }
                Text(s(R.string.gesturenav_help), style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(s(R.string.gesturenav_enable)) }

                Section(s(R.string.s_kiosk))
                SwitchRow(s(R.string.s_kiosk), vm.kiosk) { on ->
                    if (on) {
                        when {
                            vm.pinHash.isEmpty() -> vm.toast(R.string.pin_set_first)
                            !isDeviceOwner(ctx) -> dialog = "owner"
                            else -> { vm.kiosk = true; enterKiosk(act, vm.apps.map { it.pkg }) }
                        }
                    } else vm.requireAuth { vm.kiosk = false; exitKiosk(act) }
                }
                if (isDeviceOwner(ctx)) OutlinedButton({
                    vm.requireAuth { vm.kiosk = false; exitKiosk(act); releaseDeviceOwner(ctx) }
                }) { Text(s(R.string.kiosk_release)) }

                Section(s(R.string.s_widgets))
                OutlinedButton({ dialog = "widget" }) { Text(s(R.string.add_widget)) }
                vm.widgetIds.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.awm.getAppWidgetInfo(id)?.loadLabel(ctx.packageManager) ?: "#$id", Modifier.weight(1f))
                        TextButton({ vm.removeWidget(id) }) { Text(s(R.string.remove)) }
                    }
                }

                Section(s(R.string.s_badges))
                SwitchRow(s(R.string.s_badges), vm.badgesOn) { vm.badgesOn = it }
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(s(R.string.grant_access)) }

                Section(s(R.string.s_language))
                val sel = when (AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2)) { "ta" -> 1; "en" -> 2; else -> 0 }
                Choice(s(R.string.s_language), listOf(s(R.string.lang_system), s(R.string.lang_ta), s(R.string.lang_en)), sel) {
                    AppCompatDelegate.setApplicationLocales(when (it) {
                        1 -> LocaleListCompat.forLanguageTags("ta"); 2 -> LocaleListCompat.forLanguageTags("en")
                        else -> LocaleListCompat.getEmptyLocaleList()
                    })
                }

                Section(s(R.string.s_backup))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ exp.launch("tamil-launcher-backup.json") }) { Text(s(R.string.backup)) }
                    OutlinedButton({ imp.launch(arrayOf("application/json", "*/*")) }) { Text(s(R.string.restore)) }
                }
                Spacer(Modifier.height(12.dp))
                Button({ ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text(s(R.string.s_default)) }
            }
        }
    }

    // ---- dialogs
    when (dialog) {
        "hidden" -> AppPickerDialog(vm, s(R.string.s_hidden), vm.hidden, { vm.toggleHidden(it) }) { dialog = "" }
        "locked" -> AppPickerDialog(vm, s(R.string.s_locked), vm.locked, { vm.toggleLocked(it) }) { dialog = "" }
        "focus" -> AppPickerDialog(vm, s(R.string.s_focus_apps), vm.focusApps, { vm.toggleFocusApp(it) }) { dialog = "" }
        "pin" -> PinDialog(s(R.string.set_pin), false, { if (it.length >= 4) { vm.setPin(it); dialog = "" } }) { dialog = "" }
        "owner" -> AlertDialog(
            onDismissRequest = { dialog = "" }, title = { Text(s(R.string.s_kiosk)) },
            confirmButton = { TextButton({ dialog = "" }) { Text(s(R.string.ok)) } },
            text = {
                Column {
                    Text(s(R.string.kiosk_need_owner))
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text("adb shell dpm set-device-owner com.tamil.launcher/.LockAdmin", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        )
        "pack" -> {
            val packs = remember { IconStore.listPacks(ctx) }
            AlertDialog(
                onDismissRequest = { dialog = "" }, title = { Text(s(R.string.s_icon_pack)) },
                confirmButton = { TextButton({ dialog = "" }) { Text(s(R.string.ok)) } },
                text = {
                    Column {
                        TextButton({ vm.setIconPackAndApply(""); dialog = "" }) { Text(s(R.string.icon_pack_default)) }
                        packs.forEach { (pkg, label) -> TextButton({ vm.setIconPackAndApply(pkg); dialog = "" }) { Text(label) } }
                    }
                }
            )
        }
        "widget" -> {
            val providers = remember { vm.awm.installedProviders.sortedBy { it.loadLabel(ctx.packageManager).lowercase() } }
            AlertDialog(
                onDismissRequest = { dialog = "" }, title = { Text(s(R.string.select_widget)) },
                confirmButton = { TextButton({ dialog = "" }) { Text(s(R.string.cancel)) } },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(providers) { p ->
                            Text(p.loadLabel(ctx.packageManager), Modifier.fillMaxWidth().clickable {
                                dialog = ""
                                val id = vm.widgetHost.allocateAppWidgetId()
                                if (vm.awm.bindAppWidgetIdIfAllowed(id, p.provider)) afterBind(id, p)
                                else {
                                    pendingWidget = id to p
                                    bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider))
                                }
                            }.padding(12.dp))
                        }
                    }
                }
            )
        }
    }
}

private fun s0(ctx: android.content.Context, id: Int) = ctx.getString(id)
