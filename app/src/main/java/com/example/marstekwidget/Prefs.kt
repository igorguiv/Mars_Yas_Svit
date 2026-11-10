package com.example.marstekwidget

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun s(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var email: String get() = s("email"); set(v) = put("email", v)
    var password: String get() = s("password"); set(v) = put("password", v)
    var token: String get() = s("token"); set(v) = put("token", v)
    var lastRaw: String get() = s("lastRaw"); set(v) = put("lastRaw", v)
    var lastError: String get() = s("lastError"); set(v) = put("lastError", v)
    var group: String get() = s("group", "21.1"); set(v) = put("group", v)
    var history: String get() = s("history"); set(v) = put("history", v)
    var outageRaw: String get() = s("outageRaw"); set(v) = put("outageRaw", v)
    var outageErr: String get() = s("outageErr"); set(v) = put("outageErr", v)
    var address: String get() = s("address"); set(v) = put("address", v)
    var channel: String get() = s("channel"); set(v) = put("channel", v)
    var tgTitle: String get() = s("tgTitle"); set(v) = put("tgTitle", v)
    var tgEvents: String get() = s("tgEvents"); set(v) = put("tgEvents", v)
    var tgErr: String get() = s("tgErr"); set(v) = put("tgErr", v)
    var regionLabel: String get() = s("regionLabel", "Київ"); set(v) = put("regionLabel", v)
    var regionId: Int
        get() = sp.getInt("regionId", 25)
        set(v) = sp.edit().putInt("regionId", v).apply()
    var dsoId: Int
        get() = sp.getInt("dsoId", 902)
        set(v) = sp.edit().putInt("dsoId", v).apply()

    var capacity: Double
        get() = sp.getFloat("capacity", 2.0f).toDouble()
        set(v) = sp.edit().putFloat("capacity", v.toFloat()).apply()
    var avgW: Double
        get() = sp.getFloat("avgW", 220f).toDouble()
        set(v) = sp.edit().putFloat("avgW", v.toFloat()).apply()
    var minSoc: Double
        get() = sp.getFloat("minSoc", 10f).toDouble()
        set(v) = sp.edit().putFloat("minSoc", v.toFloat()).apply()
    /** 0 = авто, 1 = світла, 2 = темна */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()
    /** тема самого застосунку: 0 = авто, 1 = світла, 2 = темна */
    var appTheme: Int
        get() = sp.getInt("appTheme", 0)
        set(v) = sp.edit().putInt("appTheme", v).apply()
    /** час натискання «оновити» (0 = не оновлюється) */
    var refreshStart: Long
        get() = sp.getLong("refreshStart", 0L)
        set(v) = sp.edit().putLong("refreshStart", v).apply()
    var lastOk: Long
        get() = sp.getLong("lastOk", 0L)
        set(v) = sp.edit().putLong("lastOk", v).apply()
}
