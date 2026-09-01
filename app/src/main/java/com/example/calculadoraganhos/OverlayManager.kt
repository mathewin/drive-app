package com.example.calculadoraganhos

import android.content.Context
import android.graphics.Color
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

    fun show(context: Context, d: RideData, r: CalcResult) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        hide(context)
        val card = buildCard(context, d, r)
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

    private fun buildCard(context: Context, d: RideData, r: CalcResult): View {
        val dp = context.resources.displayMetrics.density
        val color = verdictColor(r.verdict)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(color)
            }
        }

        fun text(s: String, sp: Float, bold: Boolean, alpha: Float = 1f): TextView =
            TextView(context).apply {
                this.text = s
                textSize = sp
                setTextColor(Color.WHITE)
                this.alpha = alpha
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
            }

        container.addView(text("DriveWin - ${verdictLabel(r.verdict)}", 11f, true, 0.95f))
        container.addView(text(formatMoney(d.fare), 24f, true))
        val stats = buildString {
            if (d.km > 0) append("R\$/km ${formatMoney(r.perKm)}")
            if (d.km > 0 && d.minutes > 0) append(" - ")
            if (d.minutes > 0) append("R\$/h ${formatMoney(r.perHour)}")
        }
        container.addView(text(stats, 14f, false, 0.97f))
        return container
    }
}
