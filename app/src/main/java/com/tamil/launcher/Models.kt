package com.tamil.launcher

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.reflect.KProperty

data class AppInfo(val pkg: String, val label: String, val category: Int, val installed: Long)

sealed interface HomeItem {
    data class App(val pkg: String) : HomeItem
    data class Folder(val id: String, val name: String, val apps: List<String>) : HomeItem
}

fun csv(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split(",")
fun Iterable<String>.joinCsv(): String = joinToString(",")
fun sha(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

fun List<List<HomeItem>>.toJson(): String = JSONArray().also { root ->
    forEach { page ->
        root.put(JSONArray().also { pa ->
            page.forEach { item ->
                pa.put(
                    when (item) {
                        is HomeItem.App -> JSONObject().put("t", "a").put("p", item.pkg)
                        is HomeItem.Folder -> JSONObject().put("t", "f").put("i", item.id)
                            .put("n", item.name).put("a", JSONArray(item.apps))
                    }
                )
            }
        })
    }
}.toString()

private fun parseItem(o: JSONObject): HomeItem =
    if (o.getString("t") == "a") HomeItem.App(o.getString("p"))
    else {
        val a = o.getJSONArray("a")
        HomeItem.Folder(o.getString("i"), o.getString("n"), List(a.length()) { a.getString(it) })
    }

fun parsePages(raw: String): List<List<HomeItem>> = try {
    val r = JSONArray(raw)
    List(r.length()) { i ->
        val pa = r.getJSONArray(i)
        List(pa.length()) { j -> parseItem(pa.getJSONObject(j)) }
    }.ifEmpty { listOf(emptyList()) }
} catch (e: Exception) {
    listOf(emptyList())
}

fun List<List<HomeItem>>.clean(): List<List<HomeItem>> =
    filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) }

fun parseUsage(raw: String): Map<String, Pair<Int, Long>> = try {
    val o = JSONObject(raw.ifEmpty { "{}" })
    o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { it.getInt(0) to it.getLong(1) } }
} catch (e: Exception) {
    emptyMap()
}

/** SharedPreferences-backed Compose state, usable as a property delegate. */
class Pref<T : Any>(private val sp: SharedPreferences, private val key: String, default: T) {
    private val s = mutableStateOf(read(default))

    @Suppress("UNCHECKED_CAST")
    private fun read(d: T): T = when (d) {
        is Int -> sp.getInt(key, d)
        is Boolean -> sp.getBoolean(key, d)
        is Long -> sp.getLong(key, d)
        is Float -> sp.getFloat(key, d)
        is String -> sp.getString(key, d) ?: d
        else -> d
    } as T

    operator fun getValue(r: Any?, p: KProperty<*>): T = s.value
    operator fun setValue(r: Any?, p: KProperty<*>, v: T) {
        s.value = v
        sp.edit().also { e ->
            when (v) {
                is Int -> e.putInt(key, v)
                is Boolean -> e.putBoolean(key, v)
                is Long -> e.putLong(key, v)
                is Float -> e.putFloat(key, v)
                is String -> e.putString(key, v)
                else -> e
            }
        }.apply()
    }

    fun reload() { s.value = read(s.value) }
}
