package com.example.calculadoraganhos

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("drivewin", Context.MODE_PRIVATE)

    var minPerKm: Double
        get() = sp.getFloat("min_km", 1.2f).toDouble()
        set(v) = sp.edit().putFloat("min_km", v.toFloat()).apply()

    var minPerHour: Double
        get() = sp.getFloat("min_h", 18f).toDouble()
        set(v) = sp.edit().putFloat("min_h", v.toFloat()).apply()
}
