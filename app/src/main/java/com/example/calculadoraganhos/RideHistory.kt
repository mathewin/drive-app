package com.example.calculadoraganhos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class RideEntry(
    val ts: Long,
    val app: String,
    val fare: Double,
    val km: Double,
    val minutes: Double
) {
    val perHour: Double get() = if (minutes > 0) fare / (minutes / 60.0) else 0.0
    val perKm: Double get() = if (km > 0) fare / km else 0.0
}

class RideHistory(context: Context) {

    private val sp = context.getSharedPreferences("drivewin_history", Context.MODE_PRIVATE)

    fun addRide(app: String, fare: Double, km: Double, minutes: Double) {
        val list = rides().toMutableList()
        val now = System.currentTimeMillis()
        val last = list.firstOrNull()
        if (last != null &&
            now - last.ts < 120_000 &&
            kotlin.math.abs(last.fare - fare) < 0.01 &&
            kotlin.math.abs(last.km - km) < 0.01 &&
            kotlin.math.abs(last.minutes - minutes) < 0.01
        ) {
            return
        }
        list.add(0, RideEntry(now, app, fare, km, minutes))
        val trimmed = if (list.size > 300) list.subList(0, 300) else list
        save(trimmed)
    }

    fun rides(): List<RideEntry> {
        val s = sp.getString("rides", null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RideEntry(
                    o.getLong("ts"),
                    o.getString("app"),
                    o.getDouble("fare"),
                    o.getDouble("km"),
                    o.getDouble("min")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun ridesToday(): List<RideEntry> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        return rides().filter { it.ts >= start }
    }

    fun clear() {
        sp.edit().remove("rides").apply()
    }

    private fun save(list: List<RideEntry>) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject()
                    .put("ts", r.ts)
                    .put("app", r.app)
                    .put("fare", r.fare)
                    .put("km", r.km)
                    .put("min", r.minutes)
            )
        }
        sp.edit().putString("rides", arr.toString()).apply()
    }
}
