package com.tamil.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Loads app icons (optionally from an ADW/Nova-style icon pack) and caches them. */
object IconStore {
    private val cache = LruCache<String, ImageBitmap>(400)
    private var packPkg = ""
    private var packRes: Resources? = null
    private var packMap: Map<String, String> = emptyMap()

    fun listPacks(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
        return actions.flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
    }

    fun setPack(ctx: Context, pack: String) {
        cache.evictAll()
        packPkg = pack
        packRes = null
        packMap = emptyMap()
        if (pack.isEmpty()) return
        try {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            packRes = res
            packMap = parseFilter(res, pack)
        } catch (_: Exception) {
        }
    }

    private fun parseFilter(res: Resources, pack: String): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val id = res.getIdentifier("appfilter", "xml", pack)
            val p: XmlPullParser = if (id != 0) res.getXml(id) else
                XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), null) }
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && p.name == "item") {
                    val comp = p.getAttributeValue(null, "component")
                    val dr = p.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        Regex("ComponentInfo\\{([^/]+)/").find(comp)?.groupValues?.get(1)?.let { map.putIfAbsent(it, dr) }
                    }
                }
                e = p.next()
            }
        } catch (_: Exception) {
        }
        return map
    }

    fun get(ctx: Context, pkg: String, px: Int): ImageBitmap? {
        val key = "$pkg@$px@$packPkg"
        cache.get(key)?.let { return it }
        val fromPack = try {
            val name = packMap[pkg]
            val res = packRes
            if (name != null && res != null) {
                val id = res.getIdentifier(name, "drawable", packPkg)
                if (id != 0) res.getDrawable(id, null) else null
            } else null
        } catch (_: Exception) {
            null
        }
        val d = fromPack ?: try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (_: Exception) {
            return null
        }
        val bmp = d.toBitmap(px, px).asImageBitmap()
        cache.put(key, bmp)
        return bmp
    }
}
