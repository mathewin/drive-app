package com.example.calculadoraganhos

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Card flutuante de trabalho: cronometro (iniciar / pausar / zerar) e campo
 * onde o motorista informa o valor que fez. Fica sobre a tela do app de corrida,
 * pode ser arrastado pra qualquer canto e lembra a posicao.
 */
object WorkOverlay {

    private val COLOR_BG = 0xEE0E0F12.toInt()
    private val COLOR_VERDE = 0xFF31F900.toInt()
    private val COLOR_ROSA = 0xFFC864AF.toInt()
    private val COLOR_BRANCO = 0xFFFFFFFF.toInt()
    private val COLOR_CINZA = 0xFFC8C8C8.toInt()
    private val COLOR_CINZA2 = 0xFF888888.toInt()
    private val COLOR_FUNDO2 = 0xFF22242B.toInt()

    private var wm: WindowManager? = null
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var timerText: TextView? = null
    private var summaryText: TextView? = null
    private var statusText: TextView? = null
    private var mainButton: TextView? = null
    private var valueField: EditText? = null

    private var ctxRef: Context? = null
    private var density = 1f
    private var editing = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    private val main = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val ctx = ctxRef
            if (ctx != null && view != null) {
                try {
                    if (Work.isRunning(ctx)) Work.tick(ctx)
                    timerText?.text = Work.formatClock(Work.clockMs(ctx))
                    mainButton?.text = if (Work.isRunning(ctx)) "PAUSAR" else "INICIAR"
                    updateSummary(ctx)
                } catch (_: Exception) {
                }
            }
            main.postDelayed(this, 1000)
        }
    }

    fun isVisible(): Boolean = view != null

    fun show(context: Context) {
        runOnMain { showOnMain(context.applicationContext) }
    }

    fun hide() {
        runOnMain { hideOnMain() }
    }

    fun toggle(context: Context) {
        runOnMain {
            if (view != null) {
                hideOnMain()
            } else {
                showOnMain(context.applicationContext)
            }
        }
    }

    fun refresh(context: Context) {
        runOnMain {
            val ctx = context.applicationContext
            val v = view ?: return@runOnMain
            applyStyle(ctx)
            updateSummary(ctx)
            mainButton?.text = if (Work.isRunning(ctx)) "PAUSAR" else "INICIAR"
            try {
                wm?.updateViewLayout(v, params)
            } catch (_: Exception) {
            }
        }
    }

    private fun showOnMain(ctx: Context) {
        ctxRef = ctx
        density = ctx.resources.displayMetrics.density
        if (view != null) {
            refresh(ctx)
            return
        }
        try {
            val w = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            wm = w
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnTouchListener(overlayTouch)
            }

            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(text(ctx, "TRABALHO", 10f, COLOR_ROSA, true))
            header.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            val close = text(ctx, "\u00D7", 14f, COLOR_CINZA2, true).apply {
                setPadding(dp(8), 0, dp(2), dp(2))
                isClickable = true
                setOnClickListener {
                    Prefs(ctx).workPanelOn = false
                    hideOnMain()
                }
            }
            header.addView(close)
            root.addView(header)

            timerText = text(ctx, "00:00:00", 24f, COLOR_VERDE, true).apply {
                typeface = Typeface.MONOSPACE
            }
            root.addView(timerText, topParams(dp(2)))

            val buttons = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            mainButton = button(ctx, "INICIAR", COLOR_VERDE, COLOR_BG).apply {
                setOnClickListener {
                    if (Work.isRunning(ctx)) {
                        Work.pause(ctx)
                    } else {
                        Work.start(ctx)
                    }
                    text = if (Work.isRunning(ctx)) "PAUSAR" else "INICIAR"
                }
            }
            buttons.addView(mainButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val reset = button(ctx, "ZERAR", COLOR_FUNDO2, COLOR_CINZA).apply {
                setOnClickListener {
                    Work.resetTimer(ctx)
                    timerText?.text = Work.formatClock(0L)
                    mainButton?.text = "INICIAR"
                    updateSummary(ctx)
                }
            }
            buttons.addView(reset, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(6)
            })
            root.addView(buttons, topParams(dp(6)))

            val valueRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            valueRow.addView(text(ctx, "R\$", 14f, COLOR_VERDE, true).apply {
                setPadding(dp(2), 0, dp(6), 0)
            })
            valueField = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = EditorInfo.IME_ACTION_DONE
                hint = "valor"
                setHintTextColor(COLOR_CINZA2)
                setTextColor(COLOR_BRANCO)
                textSize = 15f
                isSingleLine = true
                setSelectAllOnFocus(true)
                background = rounded(dp(6), COLOR_FUNDO2, 0, 0)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                isFocusableInTouchMode = true
                setOnClickListener { enterEdit(ctx) }
                setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) exitEdit(ctx) }
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        submit(ctx)
                        true
                    } else {
                        false
                    }
                }
            }
            valueRow.addView(valueField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val ok = button(ctx, "OK", COLOR_ROSA, COLOR_BG).apply {
                setOnClickListener { submit(ctx) }
            }
            valueRow.addView(ok, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(6)
            })
            root.addView(valueRow, topParams(dp(6)))

            summaryText = text(ctx, "", 10f, COLOR_CINZA, false)
            root.addView(summaryText, topParams(dp(6)))

            statusText = text(ctx, "", 10f, COLOR_ROSA, false)
            root.addView(statusText, topParams(dp(2)))

            applyStyle(ctx)
            val p = buildParams(ctx)
            w.addView(root, p)
            view = root
            params = p
            updateSummary(ctx)
            timerText?.text = Work.formatClock(Work.clockMs(ctx))
            mainButton?.text = if (Work.isRunning(ctx)) "PAUSAR" else "INICIAR"
            main.removeCallbacks(ticker)
            main.post(ticker)
            DriveWinLog.log("work", "painel de trabalho exibido")
        } catch (t: Throwable) {
            DriveWinLog.log("work", "ERRO ao mostrar painel: ${t.message}")
            hideOnMain()
        }
    }

    private fun hideOnMain() {
        editing = false
        main.removeCallbacks(ticker)
        val v = view
        view = null
        params = null
        timerText = null
        summaryText = null
        statusText = null
        mainButton = null
        valueField = null
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (_: Exception) {
            }
        }
        DriveWinLog.log("work", "painel de trabalho oculto")
    }

    private fun submit(ctx: Context) {
        val raw = valueField?.text?.toString()?.trim()?.replace(',', '.') ?: ""
        val v = raw.toDoubleOrNull()
        if (v == null || v <= 0.0) {
            setStatus("digite um valor valido")
            return
        }
        val total = Work.addEarning(ctx, v)
        valueField?.setText("")
        exitEdit(ctx)
        updateSummary(ctx)
        setStatus("enviando " + ParsingUtils.formatMoney(v) + "...")
        GanhoSync.push(ctx, Work.key(), total) { ok, msg ->
            setStatus(if (ok) "no ar: " + ParsingUtils.formatMoney(total) else msg)
        }
    }

    private fun enterEdit(ctx: Context) {
        if (editing) return
        val p = params ?: return
        editing = true
        try {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            wm?.updateViewLayout(view, p)
            valueField?.isFocusableInTouchMode = true
            valueField?.requestFocus()
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(valueField, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
        }
    }

    private fun exitEdit(ctx: Context) {
        if (!editing) return
        editing = false
        try {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(valueField?.windowToken, 0)
            valueField?.clearFocus()
            val p = params ?: return
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            wm?.updateViewLayout(view, p)
        } catch (_: Exception) {
        }
    }

    private fun updateSummary(ctx: Context) {
        val t = Work.secondsToday(ctx)
        val e = Work.earningsToday(ctx)
        summaryText?.text = "Hoje " + Work.formatHuman(t) + " \u00B7 " + ParsingUtils.formatMoney(e)
    }

    private fun setStatus(msg: String) {
        statusText?.text = msg
    }

    private fun applyStyle(ctx: Context) {
        val v = view ?: return
        val prefs = Prefs(ctx)
        v.background = rounded(dp(14), COLOR_BG, dp(2), COLOR_ROSA)
        v.alpha = prefs.workOpacity
        val scale = prefs.workFontSize / 13f
        timerText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * scale)
        summaryText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * scale)
        statusText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * scale)
    }

    private val overlayTouch = View.OnTouchListener { v, ev ->
        val p = params ?: return@OnTouchListener false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX
                lastY = ev.rawY
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - lastX
                val dy = ev.rawY - lastY
                if (!dragging && (abs(ev.rawX - downX) > dp(10).toFloat() || abs(ev.rawY - downY) > dp(10).toFloat())) {
                    dragging = true
                }
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
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val ctx = v.context.applicationContext
                    val prefs = Prefs(ctx)
                    prefs.workPosX = p.x
                    prefs.workPosY = p.y
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
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
        p.x = if (prefs.workPosX == Int.MIN_VALUE) dp(12) else prefs.workPosX
        p.y = if (prefs.workPosY == Int.MIN_VALUE) dp(160) else prefs.workPosY
        return p
    }

    private fun button(ctx: Context, label: String, bg: Int, fg: Int): TextView {
        return text(ctx, label, 12f, fg, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(dp(8), bg, 0, 0)
            isClickable = true
        }
    }

    private fun rounded(radius: Int, color: Int, stroke: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
            if (stroke > 0) setStroke(stroke, strokeColor)
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

    private fun topParams(marginDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(marginDp)
        }
    }

    private fun dp(v: Int): Int = (v * density).roundToInt()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            main.post(block)
        }
    }
}
