package com.douyintv.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("douyintv_prefs", Context.MODE_PRIVATE)

    var autoPlayNext: Boolean
        get() = prefs.getBoolean("auto_play_next", true)
        set(value) = prefs.edit().putBoolean("auto_play_next", value).apply()

    var compatMobileUa: Boolean
        get() = prefs.getBoolean("compat_mobile_ua", false)
        set(value) = prefs.edit().putBoolean("compat_mobile_ua", value).apply()

    // 0 = Auto, 1 = Landscape, 2 = Portrait
    var orientationMode: Int
        get() = prefs.getInt("orientation_mode", 0)
        set(value) = prefs.edit().putInt("orientation_mode", value).apply()

    var sidebarVisible: Boolean
        get() = prefs.getBoolean("sidebar_visible", true)
        set(value) = prefs.edit().putBoolean("sidebar_visible", value).apply()
}
