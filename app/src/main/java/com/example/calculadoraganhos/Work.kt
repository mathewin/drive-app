package com.example.calculadoraganhos

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Cronometro de trabalho do motorista e estatisticas locais.
 *
 * Guarda, por dia, quantos segundos foram trabalhados e qual o valor que o
 * motorista informou (ganho do dia). A partir disso calcula dia, semana e mes,
 * usados tanto no card flutuante quanto na tela de estatisticas do app.
 */
object Work {

    private const val PREF = "drivewin"
    private const val K_RUNNING = "work_running"
    private const val K_SESSION = "work_session_ms"
    private const val K_LAST_ELAPSED = "work_last_elapsed"
    private const val K_SECONDS = "work_seconds_json"
    private const val K_EARNINGS = "work_earnings_json"
    private const val KEEP_DAYS = 120

    private val lock = Any()
    private var running = false
    private var sessionMs = 0L
    private var lastElapsed = 0L
    private var secondsMap: MutableMap<String, Long> = HashMap()
    private var earningsMap: MutableMap<String, Double> = HashMap()
    private var loaded = false
    private var ticksSinceSave = 0

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun sdf() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun key(ms: Long = System.currentTimeMillis()): String = sdf().format(Date(ms))

    private fun ensure(ctx: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val p = sp(ctx)
            running = p.getBoolean(K_RUNNING, false)
            sessionMs = p.getLong(K_SESSION, 0L)
            lastElapsed = if (running) SystemClock.elapsedRealtime() else p.getLong(K_LAST_ELAPSED, 0L)
            secondsMap = parseLongMap(p.getString(K_SECONDS, null))
            earningsMap = parseDoubleMap(p.getString(K_EARNINGS, null))
            loaded = true
        }
    }

    private fun parseLongMap(raw: String?): MutableMap<String, Long> {
        val out = HashMap<String, Long>()
        try {
            val o = JSONObject(raw ?: "{}")
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = o.optLong(k, 0L)
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun parseDoubleMap(raw: String?): MutableMap<String, Double> {
        val out = HashMap<String, Double>()
        try {
            val o = JSONObject(raw ?: "{}")
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = o.optDouble(k, 0.0)
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun persist(ctx: Context) {
        synchronized(lock) {
            prune()
            val sec = JSONObject()
            secondsMap.forEach { (k, v) -> sec.put(k, v) }
            val ear = JSONObject()
            earningsMap.forEach { (k, v) -> ear.put(k, v) }
            sp(ctx).edit()
                .putBoolean(K_RUNNING, running)
                .putLong(K_SESSION, sessionMs)
                .putLong(K_LAST_ELAPSED, lastElapsed)
                .putString(K_SECONDS, sec.toString())
                .putString(K_EARNINGS, ear.toString())
                .apply()
        }
    }

    private fun prune() {
        val limit = System.currentTimeMillis() - KEEP_DAYS.toLong() * 24L * 3600L * 1000L
        val cut = key(limit)
        secondsMap.keys.removeAll { it < cut }
        earningsMap.keys.removeAll { it < cut }
    }

    /** Avanca o cronometro, creditando o tempo decorrido no dia atual. */
    fun tick(ctx: Context) {
        ensure(ctx)
        synchronized(lock) {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            var delta = now - lastElapsed
            if (delta < 0L) delta = 0L
            lastElapsed = now
            if (delta == 0L) return
            sessionMs += delta
            val k = key()
            secondsMap[k] = (secondsMap[k] ?: 0L) + delta
            ticksSinceSave++
            if (ticksSinceSave >= 15) {
                ticksSinceSave = 0
                persist(ctx)
            }
        }
    }

    /** Forca a gravacao do estado atual (usado ao sair/pausar). */
    fun save(ctx: Context) {
        ensure(ctx)
        persist(ctx)
    }

    fun start(ctx: Context) {
        ensure(ctx)
        synchronized(lock) {
            if (running) return
            running = true
            lastElapsed = SystemClock.elapsedRealtime()
        }
        persist(ctx)
    }

    fun pause(ctx: Context) {
        ensure(ctx)
        tick(ctx)
        synchronized(lock) {
            running = false
        }
        persist(ctx)
    }

    fun resetTimer(ctx: Context) {
        ensure(ctx)
        tick(ctx)
        synchronized(lock) {
            sessionMs = 0L
            running = false
            lastElapsed = SystemClock.elapsedRealtime()
        }
        persist(ctx)
    }

    fun isRunning(ctx: Context): Boolean {
        ensure(ctx)
        return running
    }

    /** Tempo visivel do cronometro (turno atual). */
    fun clockMs(ctx: Context): Long {
        ensure(ctx)
        synchronized(lock) {
            var v = sessionMs
            if (running) {
                val d = SystemClock.elapsedRealtime() - lastElapsed
                if (d > 0L) v += d
            }
            return v
        }
    }

    fun addEarning(ctx: Context, value: Double): Double {
        ensure(ctx)
        val total: Double
        synchronized(lock) {
            val k = key()
            total = (earningsMap[k] ?: 0.0) + value
            earningsMap[k] = total
        }
        persist(ctx)
        return total
    }

    fun earningsOn(ctx: Context, k: String): Double {
        ensure(ctx)
        synchronized(lock) { return earningsMap[k] ?: 0.0 }
    }

    fun secondsOn(ctx: Context, k: String): Long {
        ensure(ctx)
        synchronized(lock) { return secondsMap[k] ?: 0L }
    }

    fun earningsToday(ctx: Context): Double = earningsOn(ctx, key())

    fun secondsToday(ctx: Context): Long {
        tick(ctx)
        return secondsOn(ctx, key())
    }

    fun earningsWeek(ctx: Context): Double = sumEarnings(ctx, weekKeys())

    fun earningsMonth(ctx: Context): Double {
        val prefix = key().substring(0, 7)
        return sumEarnings(ctx, monthKeys(prefix))
    }

    fun secondsWeek(ctx: Context): Long {
        tick(ctx)
        return sumSeconds(ctx, weekKeys())
    }

    fun secondsMonth(ctx: Context): Long {
        tick(ctx)
        val prefix = key().substring(0, 7)
        return sumSeconds(ctx, monthKeys(prefix))
    }

    private fun weekKeys(): List<String> {
        val c = Calendar.getInstance()
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // segunda = 0
        val start = Calendar.getInstance()
        start.add(Calendar.DAY_OF_MONTH, -dow)
        val out = ArrayList<String>(7)
        for (i in 0 until 7) {
            val d = Calendar.getInstance()
            d.timeInMillis = start.timeInMillis
            d.add(Calendar.DAY_OF_MONTH, i)
            out.add(sdf().format(d.time))
        }
        return out
    }

    private fun monthKeys(prefix: String): List<String> {
        val c = Calendar.getInstance()
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val out = ArrayList<String>(days)
        for (i in 1..days) {
            out.add(prefix + "-" + String.format(Locale.US, "%02d", i))
        }
        return out
    }

    private fun sumEarnings(ctx: Context, keys: List<String>): Double {
        ensure(ctx)
        var total = 0.0
        synchronized(lock) {
            keys.forEach { total += earningsMap[it] ?: 0.0 }
        }
        return total
    }

    private fun sumSeconds(ctx: Context, keys: List<String>): Long {
        var total = 0L
        synchronized(lock) {
            keys.forEach { total += secondsMap[it] ?: 0L }
        }
        return total
    }

    fun formatClock(ms: Long): String {
        var total = ms / 1000L
        if (total < 0L) total = 0L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun formatHuman(seconds: Long): String {
        var total = seconds
        if (total < 0L) total = 0L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        return if (h > 0L) "${h}h ${m}min" else "${m}min"
    }
}
