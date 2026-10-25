package com.outsmartis.sofa.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A TV this phone has paired with. [id] is the mDNS service name, or the address for TVs added by hand. */
data class SavedTv(val id: String, val name: String, val host: String, val model: String = "")

class TvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun all(): List<SavedTv> {
        val arr = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedTv(o.getString("id"), o.getString("name"), o.getString("host"), o.optString("model"))
        }
    }

    fun save(tvs: List<SavedTv>) {
        val arr = JSONArray()
        tvs.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("model", it.model)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var touchpad: Boolean
        get() = prefs.getBoolean("touchpad", false)
        set(v) = prefs.edit().putBoolean("touchpad", v).apply()

    /** Open the phone keyboard as soon as the TV shows a text field. */
    var autoKeyboard: Boolean
        get() = prefs.getBoolean("auto_keyboard", true)
        set(v) = prefs.edit().putBoolean("auto_keyboard", v).apply()

    var volumeKeys: Boolean
        get() = prefs.getBoolean("volume_keys", true)
        set(v) = prefs.edit().putBoolean("volume_keys", v).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()
}
