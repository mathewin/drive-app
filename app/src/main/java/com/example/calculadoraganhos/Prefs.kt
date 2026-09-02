package com.example.calculadoraganhos

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("drivewin", Context.MODE_PRIVATE)

    var minPerKm: Double
        get() = sp.getFloat("min_km", 1.5f).toDouble()
        set(v) = sp.edit().putFloat("min_km", v.toFloat()).apply()

    var minPerHour: Double
        get() = sp.getFloat("min_h", 40f).toDouble()
        set(v) = sp.edit().putFloat("min_h", v.toFloat()).apply()

    var overlayPosition: String
        get() = sp.getString("ov_pos", "topo") ?: "topo"
        set(v) = sp.edit().putString("ov_pos", v).apply()

    var overlayOpacity: Float
        get() = sp.getFloat("ov_opac", 1f)
        set(v) = sp.edit().putFloat("ov_opac", v).apply()

    var overlayFontSize: Float
        get() = sp.getFloat("ov_font", 14f)
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
}
