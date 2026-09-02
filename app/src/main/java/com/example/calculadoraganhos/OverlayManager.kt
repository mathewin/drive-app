package com.example.calculadoraganhos

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

object OverlayManager {

    private enum class Theme { NINETY_NINE, UBER, DEFAULT }

    private var view: View? = null
    private var hideRunnable: Runnable? = null

    private val money: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun formatMoney(v: Double): String = money.format(v)

    fun verdictLabel(v: Int): String = when (v) {
        CalcResult.GREEN -> "COMPENSA"
        CalcResult.AMBER -> "NO LIMITE"
        else -> "NAO COMPENSA"
    }

    fun verdictColor(v: Int): Int = when (v) {
        CalcResult.GREEN -> 0xFF16A34A.toInt()
        CalcResult.AMBER -> 0xFFF59E0B.toInt()
        else -> 0xFFDC2626.toInt()
    }

    fun show(context: Context, packageName: String?, texts: List<String>?, d: RideData, r: CalcResult) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        hide(context)
        val prefs = Prefs(context)
        val card = buildCard(context, themeFor(packageName, texts), prefs, d, r)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        val margin = (context.resources.displayMetrics.density * 40).toInt()
        params.y = margin
        params.gravity = when (prefs.overlayPosition) {
            "baixo" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            "meio" -> Gravity.CENTER
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        try {
            card.alpha = prefs.overlayOpacity
            wm.addView(card, params)
            view = card
            if (prefs.overlayAlert) beepAndVibrate(context)
            Log.d("DriveWin", "overlay shown fare=${d.fare} km=${d.km} min=${d.minutes} verdict=${r.verdict}")
            scheduleHide(context, card, prefs.overlayShowSeconds * 1000L)
        } catch (e: Exception) {
            Log.w("DriveWin", "overlay fail: ${e.message}")
        }
    }

    private fun beepAndVibrate(context: Context) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            tg.release()
        } catch (_: Exception) {
        }
        try {
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vib?.hasVibrator() == true) {
                vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        hideRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        hideRunnable = null
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
        view = null
    }

    private fun scheduleHide(context: Context, card: View, delayMs: Long) {
        val h = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            if (view === card) hide(context)
        }
        hideRunnable = runnable
        h.postDelayed(runnable, delayMs)
    }

    private fun themeFor(packageName: String?, texts: List<String>?): Theme {
        val pkg = packageName?.lowercase()
        if (pkg?.contains("br.com.taxiapp") == true) return Theme.NINETY_NINE
        if (pkg?.contains("com.ubercab") == true) return Theme.UBER

        val joined = (texts?.joinToString(" ") ?: "").lowercase()
        if (joined.contains("radar de viagens") || joined.contains("taxiapp")) return Theme.NINETY_NINE
        if (joined.contains("uberx") || joined.contains("uber black") || joined.contains("uber comfort") || joined.contains("uber")) return Theme.UBER
        return Theme.DEFAULT
    }

    private fun buildCard(context: Context, theme: Theme, prefs: Prefs, d: RideData, r: CalcResult): View {
        val dp = context.resources.displayMetrics.density
        val font = prefs.overlayFontSize / 14f

        val bg = when (theme) {
            Theme.NINETY_NINE -> 0xFF000000.toInt()
            Theme.UBER -> 0xFFFFFFFF.toInt()
            Theme.DEFAULT -> 0xFF17181C.toInt()
        }
        val fareColor = when (theme) {
            Theme.NINETY_NINE -> 0xFFFFCC00.toInt()
            Theme.UBER -> 0xFF000000.toInt()
            Theme.DEFAULT -> 0xFFFFFFFF.toInt()
        }
        val lineColor = when (theme) {
            Theme.NINETY_NINE -> 0xFFCCCCCC.toInt()
            Theme.UBER -> 0xFF666666.toInt()
            Theme.DEFAULT -> 0xFFCCCCCC.toInt()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(bg)
                setStroke((2 * dp).toInt(), verdictColor(r.verdict))
            }
        }

        fun text(s: String, sp: Float, bold: Boolean, color: Int, alpha: Float = 1f): TextView =
            TextView(context).apply {
                this.text = s
                textSize = sp * font
                setTextColor(color)
                this.alpha = alpha
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
            }

        val tripInfo = buildString {
            if (d.minutes > 0) append(formatTime(d.minutes))
            if (d.minutes > 0 && d.km > 0) append(" - ")
            if (d.km > 0) append(formatKm(d.km))
        }

        container.addView(text("DriveWin - ${verdictLabel(r.verdict)}", 11f, true, verdictColor(r.verdict)))
        container.addView(text(formatMoney(d.fare), 24f, true, fareColor))
        if (tripInfo.isNotEmpty()) container.addView(text(tripInfo, 12f, false, lineColor, 0.95f))
        val stats = buildString {
            if (d.km > 0 && prefs.showPerKm) append("R\$/km ${formatMoney(r.perKm)}")
            if (d.km > 0 && prefs.showPerKm && d.minutes > 0 && prefs.showPerHour) append(" - ")
            if (d.minutes > 0 && prefs.showPerHour) append("R\$/h ${formatMoney(r.perHour)}")
        }
        container.addView(text(stats, 14f, false, lineColor, 0.97f))
        return container
    }

    fun formatKm(v: Double): String = String.format(Locale("pt", "BR"), "%.1f km", v)

    fun formatTime(minutes: Double): String {
        val m = minutes.toInt()
        val h = m / 60
        val rest = m % 60
        return if (h > 0) "${h}h${rest}min" else "${m}min"
    }
}
