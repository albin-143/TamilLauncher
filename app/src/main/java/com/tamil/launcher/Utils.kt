package com.tamil.launcher

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.ContactsContract

fun adminComponent(ctx: Context) = ComponentName(ctx, LockAdmin::class.java)

fun isAdminActive(ctx: Context): Boolean =
    ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(ctx))

fun requestAdmin(ctx: Context) {
    ctx.startActivity(
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.admin_reason))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun lockScreen(ctx: Context) {
    if (isAdminActive(ctx)) ctx.getSystemService(DevicePolicyManager::class.java).lockNow()
    else requestAdmin(ctx)
}

@SuppressLint("WrongConstant")
fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (_: Exception) {
    }
}

fun loadShortcuts(ctx: Context, pkg: String): List<ShortcutInfo> = try {
    val la = ctx.getSystemService(LauncherApps::class.java)
    if (!la.hasShortcutHostPermission()) emptyList()
    else la.getShortcuts(
        LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        ), Process.myUserHandle()
    )?.take(4) ?: emptyList()
} catch (_: Exception) {
    emptyList()
}

fun startShortcut(ctx: Context, s: ShortcutInfo) {
    try { ctx.getSystemService(LauncherApps::class.java).startShortcut(s, null, null) } catch (_: Exception) {}
}

fun hasContactsPermission(ctx: Context) =
    ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

fun searchContacts(ctx: Context, q: String): List<Pair<String, Uri>> {
    if (!hasContactsPermission(ctx) || q.isBlank()) return emptyList()
    val out = ArrayList<Pair<String, Uri>>()
    try {
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"),
            ContactsContract.Contacts.DISPLAY_NAME
        )?.use { c ->
            while (c.moveToNext() && out.size < 5) {
                out += c.getString(2) to ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
            }
        }
    } catch (_: Exception) {
    }
    return out
}

// ---------------- Kiosk mode (needs Device Owner, set once via adb)
fun isDeviceOwner(ctx: Context): Boolean =
    ctx.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(ctx.packageName)

fun updateLockTask(ctx: Context, pkgs: Collection<String>) {
    if (!isDeviceOwner(ctx)) return
    val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    val admin = adminComponent(ctx)
    try {
        // Only whitelisted packages may start inside lock-task mode, so allow every launchable app.
        dpm.setLockTaskPackages(admin, (pkgs + ctx.packageName).distinct().toTypedArray())
        if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        val f = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) }
        dpm.addPersistentPreferredActivity(admin, f, ComponentName(ctx, MainActivity::class.java))
    } catch (_: Exception) {
    }
}

fun enterKiosk(act: Activity, pkgs: Collection<String>) {
    updateLockTask(act, pkgs)
    try { act.startLockTask() } catch (_: Exception) {}
}

fun exitKiosk(act: Activity) {
    try { act.stopLockTask() } catch (_: Exception) {}
    try {
        act.getSystemService(DevicePolicyManager::class.java)
            .clearPackagePersistentPreferredActivities(adminComponent(act), act.packageName)
    } catch (_: Exception) {}
}

fun releaseDeviceOwner(ctx: Context) {
    try { ctx.getSystemService(DevicePolicyManager::class.java).clearDeviceOwnerApp(ctx.packageName) } catch (_: Exception) {}
}
