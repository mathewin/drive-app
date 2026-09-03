package com.example.calculadoraganhos

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

object OverlayManager {

    data class OverlayContent(
        val data: RideData,
        val result: CalcResult,
        val app: String,
        val suspicious: Boolean,
        val confidence: Double,
        val passenger: String? = null
    )

    private val COLOR_BG = 0xFF0E0F12.toInt()
    private val COLOR_VERDE = 0xFF31F900.toInt()
    private val COLOR_ROSA = 0xFFC864AF.toInt()
    private val COLOR_BRANCO = 0xFFFFFFFF.toInt()
    private val COLOR_CINZA = 0xFFC8C8C8.toInt()
    private val COLOR_CINZA2 = 0xFF888888.toInt()
    private val COLOR_AMBAR = 0xFFF5A623.toInt()
    private val COLOR_VERMELHO = 0xFFFF4D4D.toInt()

    private var wm: WindowManager? = null
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var content: OverlayContent? = null
    private var hideRunnable: Runnable? = null
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var density = 1f
    private val main = Handler(Looper.getMainLooper())

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            main.post(block)
        }
    }

    fun show(context: Context, c: OverlayContent, beep: Boolean) {
        runOnMain {
            showOnMain(context.applicationContext, c, beep)
        }
    }

    private fun showOnMain(ctx: Context, c: OverlayContent, beep: Boolean) {
        try {
            density = ctx.resources.displayMetrics.density
            content = c
            val fresh = view == null
            if (fresh) {
                wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val root = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    setOnTouchListener(overlayTouch)
                }
                val p = buildParams(ctx)
                root.alpha = 0f
                root.scaleX = 0.85f
                root.scaleY = 0.85f
                wm?.addView(root, p)
                view = root
                params = p
                DriveWinLog.log("ovl", "card criado na tela (overlay ok)")
            }
            rebuild(ctx, c)
            AppState.updateOverlayVisible(true)
            if (beep) beepAndVibrate(ctx)
            animateIn(fresh)
            scheduleHide(ctx)
            DriveWinLog.log(
                "ovl",
                "card atualizado: ${c.app} ${c.data.fare}km=${c.data.totalDistanceKm} min=${c.data.totalTimeMin}"
            )
        } catch (t: Throwable) {
            DriveWinLog.log("ovl", "ERRO no overlay: ${t.message}")
            cleanup()
        }
    }

    private fun animateIn(fresh: Boolean) {
        val v = view ?: return
        try {
            v.animate().cancel()
            if (fresh) {
                v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260).start()
            } else {
                v.scaleX = 0.93f
                v.scaleY = 0.93f
                v.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
        } catch (_: Exception) {
        }
    }

    fun hide() {
        runOnMain {
            hideRunnable?.let { main.removeCallbacks(it) }
            hideRunnable = null
            cleanup()
        }
    }

    private val overlayTouch = View.OnTouchListener { v, ev ->
        val p = params ?: return@OnTouchListener false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX
                lastY = ev.rawY
                dragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - lastX
                val dy = ev.rawY - lastY
                if (!dragging && (abs(dx) > dp(6).toFloat() || abs(dy) > dp(6).toFloat())) dragging = true
                if (dragging) {
                    p.x += dx.roundToInt()
                    p.y += dy.roundToInt()
                    lastX = ev.rawX
                    lastY = ev.rawY
                    try {
                        wm?.updateViewLayout(v, p)
                    } catch (_: Exception) {
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val ctx = v.context.applicationContext
                    val prefs = Prefs(ctx)
                    prefs.overlayPositionX = p.x
                    prefs.overlayPositionY = p.y
                } else {
                    DriveWinLog.log("ovl", "toque no card - fechando")
                    hide()
                }
                true
            }
            else -> false
        }
    }

    private fun rebuild(ctx: Context, c: OverlayContent) {
        val root = view ?: return
        root.removeAllViews()
        val prefs = Prefs(ctx)
        val scale = prefs.overlayFontSize / 13f
        val alpha = prefs.overlayOpacity

        val levelColor = when (c.result.level) {
            Level.EXCELLENT, Level.GOOD -> COLOR_VERDE
            Level.MEDIUM -> COLOR_AMBAR
            Level.BAD -> COLOR_VERMELHO
        }
        val bg = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(COLOR_BG)
            setStroke(dp(2), levelColor)
        }
        root.background = bg
        root.alpha = alpha

        val title = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title.addView(
            text(ctx, "DRIVEWIN", 13 * scale, COLOR_ROSA, true)
        )
        val badge = text(ctx, c.result.level.label, 11 * scale, COLOR_BG, true).apply {
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setBackgroundColor(levelColor)
        }
        title.addView(badge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8)
        })
        root.addView(title)

        root.addView(
            text(ctx, "${ParsingUtils.formatMoney(c.result.perKm)}/km", 26 * scale, COLOR_BRANCO, true),
            paramsTop(dp(4))
        )
        root.addView(
            text(ctx, "${ParsingUtils.formatMoney(c.result.perHour)}/h", 17 * scale, COLOR_CINZA, false),
            paramsTop(dp(2))
        )
        val passenger = c.passenger?.takeIf { it.isNotBlank() }
        if (prefs.showScore || passenger != null) {
            root.addView(
                text(
                    ctx,
                    if (passenger != null) "PASSAGEIRO  $passenger" else "NOTA ${c.result.score}/100",
                    14 * scale,
                    if (passenger != null) COLOR_ROSA else levelColor,
                    true
                ),
                paramsTop(dp(2))
            )
        }

        if (c.suspicious) {
            root.addView(
                text(ctx, "Dados suspeitos", 11 * scale, COLOR_VERMELHO, true),
                paramsTop(dp(4))
            )
        }

        try {
            wm?.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
    }

    private fun text(ctx: Context, s: String, sizeSp: Float, color: Int, bold: Boolean): TextView {
        return TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            includeFontPadding = false
        }
    }

    private fun paramsTop(marginDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(marginDp)
        }
    }

    private fun cleanup() {
        val v = view
        view = null
        params = null
        content = null
        AppState.updateOverlayVisible(false)
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (_: Exception) {
            }
        }
    }

    private fun scheduleHide(ctx: Context) {
        val runnable = Runnable { hide() }
        hideRunnable = runnable
        main.postDelayed(runnable, Prefs(ctx).overlayShowSeconds * 1000L)
    }

    private fun buildParams(ctx: Context): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        val prefs = Prefs(ctx)
        p.x = if (prefs.overlayPositionX == Int.MIN_VALUE) {
            (ctx.resources.displayMetrics.widthPixels / 2 - dp(140)).toInt()
        } else {
            prefs.overlayPositionX
        }
        p.y = prefs.overlayPositionY
        return p
    }

    private fun dp(v: Int): Int {
        return (v * density).roundToInt()
    }

    private fun beepAndVibrate(context: Context) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            try {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
            } catch (_: Exception) {
            }
            main.postDelayed({
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                } catch (_: Exception) {
                }
                try {
                    tg.release()
                } catch (_: Exception) {
                }
            }, 300)
        } catch (_: Exception) {
        }
        try {
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vib?.hasVibrator() == true) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 120, 400), -1))
            }
        } catch (_: Exception) {
        }
    }
}
