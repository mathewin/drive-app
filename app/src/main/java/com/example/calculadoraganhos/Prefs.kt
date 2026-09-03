package com.example.calculadoraganhos

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("drivewin", Context.MODE_PRIVATE)

    var minPerKm: Double
        get() = sp.getFloat("min_km", 2.0f).toDouble()
        set(v) = sp.edit().putFloat("min_km", v.toFloat()).apply()

    var minPerHour: Double
        get() = sp.getFloat("min_h", 40f).toDouble()
        set(v) = sp.edit().putFloat("min_h", v.toFloat()).apply()

    var overlayPositionX: Int
        get() = sp.getInt("ov_x", Int.MIN_VALUE)
        set(v) = sp.edit().putInt("ov_x", v).apply()

    var overlayPositionY: Int
        get() = sp.getInt("ov_y", 40)
        set(v) = sp.edit().putInt("ov_y", v).apply()

    var overlayOpacity: Float
        get() = sp.getFloat("ov_opac", 1f)
        set(v) = sp.edit().putFloat("ov_opac", v).apply()

    var overlayFontSize: Float
        get() = sp.getFloat("ov_font", 13f)
        set(v) = sp.edit().putFloat("ov_font", v).apply()

    var overlayShowSeconds: Int
        get() = sp.getInt("ov_secs", 8)
        set(v) = sp.edit().putInt("ov_secs", v).apply()

    var showPerKm: Boolean
        get() = sp.getBoolean("ov_perkm", true)
        set(v) = sp.edit().putBoolean("ov_perkm", v).apply()

    var showPerHour: Boolean
        get() = sp.getBoolean("ov_perh", true)
        set(v) = sp.edit().putBoolean("ov_perh", v).apply()

    var showScore: Boolean
        get() = sp.getBoolean("ov_score", true)
        set(v) = sp.edit().putBoolean("ov_score", v).apply()

    var overlayAlert: Boolean
        get() = sp.getBoolean("ov_alert", true)
        set(v) = sp.edit().putBoolean("ov_alert", v).apply()

    var monitorOn: Boolean
        get() = sp.getBoolean("monitor_on", false)
        set(v) = sp.edit().putBoolean("monitor_on", v).apply()

    var darkMode: Boolean
        get() = sp.getBoolean("dark_mode", true)
        set(v) = sp.edit().putBoolean("dark_mode", v).apply()

    var ocrEnabled: Boolean
        get() = sp.getBoolean("ocr_enabled", true)
        set(v) = sp.edit().putBoolean("ocr_enabled", v).apply()

    var printAuto: Boolean
        get() = sp.getBoolean("print_auto", false)
        set(v) = sp.edit().putBoolean("print_auto", v).apply()

    var cardNotify: Boolean
        get() = sp.getBoolean("card_notify", true)
        set(v) = sp.edit().putBoolean("card_notify", v).apply()

    var lastOffer: String?
        get() = sp.getString("last_offer", null)
        set(v) = sp.edit().putString("last_offer", v).apply()

    fun pushHistory(entry: String) {
        val list = history().toMutableList()
        list.add(0, entry)
        while (list.size > 30) list.removeAt(list.size - 1)
        sp.edit().putString("history", list.joinToString("\n")).apply()
    }

    fun history(): List<String> =
        (sp.getString("history", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun clearHistory() {
        sp.edit().remove("history").apply()
    }
}
