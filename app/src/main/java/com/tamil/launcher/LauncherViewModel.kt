package com.tamil.launcher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val pm = app.packageManager
    private val all = mutableListOf<Pref<*>>()
    private fun <T : Any> pref(key: String, def: T) = Pref(sp, key, def).also { all += it }

    enum class Screen { HOME, DRAWER, SETTINGS }
    class PendingAuth(val onSuccess: () -> Unit)

    // ---- transient UI state
    var screen by mutableStateOf(Screen.HOME)
    var homeSignal by mutableIntStateOf(0)
    var pendingAuth by mutableStateOf<PendingAuth?>(null)
    var folderTarget by mutableStateOf<String?>(null)
    var homeMenu by mutableStateOf(false)

    // ---- persisted settings
    var cols by pref("cols", 4)
    var rows by pref("rows", 5)
    var iconDp by pref("iconDp", 52)
    var showLabels by pref("labels", true)
    var minimal by pref("minimal", false)
    var theme by pref("theme", 0)
    var accent by pref("accent", 0xFF6750A4.toInt())
    var fontIdx by pref("font", 0)
    var iconPack by pref("iconPack", "")
    var dim by pref("dim", 0.85f)
    var animations by pref("anim", true)
    var transition by pref("transition", 1)
    var sortMode by pref("sort", 0)
    var dblLock by pref("dblLock", true)
    var swipeNotif by pref("swipeNotif", true)
    var badgesOn by pref("badges", true)
    var hideNav by pref("hideNav", true)
    var kiosk by pref("kiosk", false)
    var gestureNav by pref("gestureNav", true)
    var pinHash by pref("pin", "")
    var hiddenRaw by pref("hidden", "")
    var lockedRaw by pref("locked", "")
    var focusRaw by pref("focusApps", "")
    var focusUntil by pref("focusUntil", 0L)
    var dockRaw by pref("dock", "")
    var pagesRaw by pref("pages", "")
    var usageRaw by pref("usage", "")
    var widgetsRaw by pref("widgets", "")
    var langInit by pref("langInit", false)
    private var initialized by pref("init", false)

    // ---- data
    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    val appMap by derivedStateOf { apps.associateBy { it.pkg } }
    val pages by derivedStateOf { parsePages(pagesRaw) }
    val dock by derivedStateOf { csv(dockRaw) }
    val hidden by derivedStateOf { csv(hiddenRaw).toSet() }
    val locked by derivedStateOf { csv(lockedRaw).toSet() }
    val focusApps by derivedStateOf { csv(focusRaw).toSet() }
    val usage by derivedStateOf { parseUsage(usageRaw) }
    val widgetIds by derivedStateOf { csv(widgetsRaw).mapNotNull { it.toIntOrNull() } }

    val widgetHost = AppWidgetHost(app, 1024)
    val awm: AppWidgetManager = AppWidgetManager.getInstance(app)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { loadApps() }
    }

    private val cap get() = cols * rows

    fun toast(id: Int) = Toast.makeText(app, id, Toast.LENGTH_SHORT).show()

    // ---------------- apps
    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(i, 0).mapNotNull { ri ->
                val p = ri.activityInfo.packageName
                if (p == app.packageName) return@mapNotNull null
                AppInfo(
                    p, ri.loadLabel(pm).toString(), ri.activityInfo.applicationInfo.category,
                    try { pm.getPackageInfo(p, 0).firstInstallTime } catch (e: Exception) { 0L }
                )
            }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) { apps = list; afterAppsLoaded() }
        }
    }

    private fun afterAppsLoaded() {
        if (apps.isEmpty()) return
        if (kiosk) updateLockTask(app, apps.map { it.pkg })
        if (!initialized) { setupDefaults(); initialized = true; return }
        val pk = apps.map { it.pkg }.toSet()
        val np = pages.map { pg ->
            pg.mapNotNull {
                when (it) {
                    is HomeItem.App -> if (it.pkg in pk) it else null
                    is HomeItem.Folder -> it.apps.filter { p -> p in pk }.let { a -> if (a.isEmpty()) null else it.copy(apps = a) }
                }
            }
        }.clean()
        if (np != pages) savePages(np)
        val nd = dock.filter { it in pk }
        if (nd != dock) dockRaw = nd.joinCsv()
    }

    private fun setupDefaults() {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL), Intent(Intent.ACTION_VIEW, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val d = intents.mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { p -> apps.any { it.pkg == p } }.distinct()
        dockRaw = d.joinCsv()
        savePages(apps.map { it.pkg }.filter { it !in d }.take(8).map<String, HomeItem> { HomeItem.App(it) }
            .let { if (it.isEmpty()) listOf(emptyList()) else listOf(it) })
    }

    // ---------------- launching
    fun open(ctx: Context, pkg: String) {
        if (focusActive() && pkg in focusApps) { toast(R.string.focus_blocked); return }
        val go: () -> Unit = {
            recordLaunch(pkg)
            pm.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
            screen = Screen.HOME
        }
        if (pkg in locked) pendingAuth = PendingAuth(go) else go()
    }

    private fun recordLaunch(pkg: String) {
        val m = usage.toMutableMap()
        val c = m[pkg]?.first ?: 0
        m[pkg] = (c + 1) to System.currentTimeMillis()
        usageRaw = JSONObject().apply { m.forEach { (k, v) -> put(k, JSONArray().put(v.first).put(v.second)) } }.toString()
    }

    // ---------------- home layout
    private fun savePages(p: List<List<HomeItem>>) { pagesRaw = p.toJson() }

    fun reflow() = savePages(pages.flatten().chunked(cap).ifEmpty { listOf(emptyList()) })

    private fun appendItem(item: HomeItem) {
        val p = pages.map { it.toMutableList() }.toMutableList()
        val idx = p.indexOfFirst { it.size < cap }
        if (idx >= 0) p[idx].add(item) else p.add(mutableListOf(item))
        savePages(p)
    }

    private fun isOnHome(pkg: String) = pages.any { pg ->
        pg.any { (it is HomeItem.App && it.pkg == pkg) || (it is HomeItem.Folder && pkg in it.apps) }
    }

    fun addToHome(pkg: String) { if (!isOnHome(pkg)) appendItem(HomeItem.App(pkg)) }

    fun removeFromHome(pkg: String) =
        savePages(pages.map { pg -> pg.filterNot { it is HomeItem.App && it.pkg == pkg } }.clean())

    fun createFolder(pkg: String, name: String) {
        val f = HomeItem.Folder(System.currentTimeMillis().toString(), name.ifBlank { app.getString(R.string.folder) }, listOf(pkg))
        var replaced = false
        val np = pages.map { pg ->
            pg.map<HomeItem, HomeItem> { if (!replaced && it is HomeItem.App && it.pkg == pkg) { replaced = true; f } else it }
        }
        if (replaced) savePages(np) else appendItem(f)
    }

    fun addToFolder(folderId: String, pkg: String) {
        savePages(pages.map { pg ->
            pg.mapNotNull<HomeItem, HomeItem> {
                when {
                    it is HomeItem.App && it.pkg == pkg -> null
                    it is HomeItem.Folder && it.id == folderId -> it.copy(apps = (it.apps + pkg).distinct())
                    else -> it
                }
            }
        }.clean())
    }

    fun takeOutOfFolder(folderId: String, pkg: String) {
        savePages(pages.map { pg ->
            pg.mapNotNull<HomeItem, HomeItem> {
                if (it is HomeItem.Folder && it.id == folderId) it.apps.filter { a -> a != pkg }.let { r -> if (r.isEmpty()) null else it.copy(apps = r) } else it
            }
        }.clean())
        addToHome(pkg)
    }

    fun addToDock(pkg: String) {
        if (pkg in dock) return
        if (dock.size >= 5) { toast(R.string.dock_full); return }
        dockRaw = (dock + pkg).joinCsv()
    }

    fun removeFromDock(pkg: String) { dockRaw = (dock - pkg).joinCsv() }

    // ---------------- hide / lock / pin
    fun toggleHidden(pkg: String) {
        hiddenRaw = (if (pkg in hidden) hidden - pkg else hidden + pkg).joinCsv()
        if (pkg in hidden) { removeFromHome(pkg); removeFromDock(pkg) }
    }

    fun toggleLocked(pkg: String) {
        if (pinHash.isEmpty()) { toast(R.string.pin_set_first); return }
        lockedRaw = (if (pkg in locked) locked - pkg else locked + pkg).joinCsv()
    }

    fun toggleFocusApp(pkg: String) { focusRaw = (if (pkg in focusApps) focusApps - pkg else focusApps + pkg).joinCsv() }
    fun setPin(p: String) { pinHash = sha(p) }
    fun checkPin(p: String) = pinHash.isEmpty() || sha(p) == pinHash
    fun requireAuth(onOk: () -> Unit) { if (pinHash.isEmpty()) onOk() else pendingAuth = PendingAuth(onOk) }

    // ---------------- focus
    fun focusActive() = System.currentTimeMillis() < focusUntil
    fun startFocus(min: Int) { focusUntil = System.currentTimeMillis() + min * 60_000L }
    fun stopFocus() { focusUntil = 0L }

    // ---------------- icons / widgets
    fun setIconPackAndApply(p: String) { iconPack = p; IconStore.setPack(app, p) }
    fun addWidget(id: Int) { widgetsRaw = (csv(widgetsRaw) + id.toString()).joinCsv() }
    fun removeWidget(id: Int) {
        try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
        widgetsRaw = (csv(widgetsRaw) - id.toString()).joinCsv()
    }

    // ---------------- backup / restore
    fun exportJson(): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (k == "widgets") continue
            val t = when (v) { is Int -> "i"; is Boolean -> "b"; is Long -> "l"; is Float -> "f"; is String -> "s"; else -> continue }
            o.put(k, JSONObject().put("t", t).put("v", v))
        }
        return o.toString()
    }

    fun importJson(text: String): Boolean = try {
        val o = JSONObject(text)
        val e = sp.edit().clear()
        o.keys().forEach { k ->
            val x = o.getJSONObject(k)
            when (x.getString("t")) {
                "i" -> e.putInt(k, x.getInt("v"))
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                else -> e.putString(k, x.getString("v"))
            }
        }
        e.putString("widgets", widgetsRaw).apply()
        all.forEach { it.reload() }
        IconStore.setPack(app, iconPack)
        afterAppsLoaded()
        true
    } catch (e: Exception) {
        false
    }

    init {
        IconStore.setPack(app, iconPack)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        loadApps()
    }

    override fun onCleared() { try { app.unregisterReceiver(receiver) } catch (_: Exception) {} }
}
