package com.douyintv.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("douyintv_native_prefs", Context.MODE_PRIVATE)

    var cookie: String
        get() = prefs.getString("cookie", "") ?: ""
        set(value) = prefs.edit().putString("cookie", value.trim()).apply()

    var ttwid: String
        get() = prefs.getString("ttwid", "") ?: ""
        set(value) = prefs.edit().putString("ttwid", value.trim()).apply()

    var proxyBaseUrl: String
        get() = prefs.getString("proxy_base_url", "") ?: ""
        set(value) = prefs.edit().putString("proxy_base_url", value.trim().trimEnd('/')).apply()

    var autoPlayNext: Boolean
        get() = prefs.getBoolean("auto_play_next", true)
        set(value) = prefs.edit().putBoolean("auto_play_next", value).apply()

    var loopSingle: Boolean
        get() = prefs.getBoolean("loop_single", false)
        set(value) = prefs.edit().putBoolean("loop_single", value).apply()
}
