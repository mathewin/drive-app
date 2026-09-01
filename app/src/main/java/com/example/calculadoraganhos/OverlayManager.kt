package com.example.calculadoraganhos

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

object OverlayManager {

    private var view: View? = null

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

    fun show(context: Context, packageName: String?, d: RideData, r: CalcResult) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        hide(context)
        val card = buildCard(context, packageName, d, r)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = (context.resources.displayMetrics.density * 40).toInt()
        try {
            wm.addView(card, params)
            view = card
        } catch (_: Exception) {
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
        view = null
    }

    private fun buildCard(context: Context, packageName: String?, d: RideData, r: CalcResult): View {
        val dp = context.resources.displayMetrics.density
        val pkg = packageName?.lowercase()
        val is99 = pkg?.contains("br.com.taxiapp") == true
        val isUber = pkg?.contains("com.ubercab") == true

        val bg = when {
            is99 -> 0xFF000000.toInt()
            isUber -> 0xFFFFFFFF.toInt()
            else -> 0xFF17181C.toInt()
        }
        val fareColor = when {
            is99 -> 0xFFFFCC00.toInt()
            isUber -> 0xFF000000.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        val lineColor = when {
            is99 -> 0xFFCCCCCC.toInt()
            isUber -> 0xFF666666.toInt()
            else -> 0xFFCCCCCC.toInt()
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
                textSize = sp
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
            if (d.km > 0) append("R\$/km ${formatMoney(r.perKm)}")
            if (d.km > 0 && d.minutes > 0) append(" - ")
            if (d.minutes > 0) append("R\$/h ${formatMoney(r.perHour)}")
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
