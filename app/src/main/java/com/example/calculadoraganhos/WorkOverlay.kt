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
 * Card flutuante compacto: so cronometro e valor em verde.
 * Toque no cronometro abre INICIAR/PAUSAR e ZERAR.
 * Toque no valor abre o campo grande de digitacao e OK.
 */
object WorkOverlay {

    private val COLOR_BG = 0xEE0E0F12.toInt()
    private val COLOR_VERDE = 0xFF31F900.toInt()
    private val COLOR_ROSA = 0xFFC864AF.toInt()
    private val COLOR_BRANCO = 0xFFFFFFFF.toInt()
    private val COLOR_CINZA = 0xFFC8C8C8.toInt()
    private val COLOR_FUNDO2 = 0xFF22242B.toInt()

    private var wm: WindowManager? = null
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var timerText: TextView? = null
    private var valueText: TextView? = null
    private var mainButton: TextView? = null
    private var valueField: EditText? = null
    private var controlsRow: LinearLayout? = null
    private var valueEditRow: LinearLayout? = null
    private var valueButtonsRow: LinearLayout? = null

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
                    if (!editing) updateValue(ctx)
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
            updateValue(ctx)
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
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnTouchListener(overlayTouch)
            }

            timerText = text(ctx, "00:00:00", 26f, COLOR_VERDE, true).apply {
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
                isClickable = true
                setOnTouchListener(tapOrDrag { toggleControls() })
            }
            root.addView(timerText)

            val buttons = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                visibility = View.GONE
            }
            controlsRow = buttons
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
                }
            }
            buttons.addView(reset, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            })
            root.addView(buttons, topParams(dp(8)))

            valueText = text(ctx, ParsingUtils.formatMoney(0.0), 22f, COLOR_VERDE, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(2))
                setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
                isClickable = true
                setOnTouchListener(tapOrDrag { openValueEditor(ctx) })
            }
            root.addView(valueText, topParams(dp(4)))

            val editRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
            }
            valueEditRow = editRow
            valueField = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = EditorInfo.IME_ACTION_DONE
                hint = "0,00"
                setHintTextColor(0xFF555555.toInt())
                setTextColor(COLOR_VERDE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                setSelectAllOnFocus(true)
                background = rounded(dp(10), COLOR_FUNDO2, dp(1), COLOR_VERDE)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                minHeight = dp(48)
                isFocusableInTouchMode = true
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        submit(ctx, add = true)
                        true
                    } else {
                        false
                    }
                }
            }
            editRow.addView(valueField, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            root.addView(editRow, topParams(dp(6)))

            val actionRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                visibility = View.GONE
            }
            valueButtonsRow = actionRow
            val plus = button(ctx, "+", COLOR_VERDE, COLOR_BG).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { submit(ctx, add = true) }
            }
            actionRow.addView(plus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val minus = button(ctx, "\u2212", COLOR_ROSA, COLOR_BG).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { submit(ctx, add = false) }
            }
            actionRow.addView(minus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            })
            root.addView(actionRow, topParams(dp(6)))

            applyStyle(ctx)
            val p = buildParams(ctx)
            w.addView(root, p)
            view = root
            params = p
            updateValue(ctx)
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
        valueText = null
        mainButton = null
        valueField = null
        controlsRow = null
        valueEditRow = null
        valueButtonsRow = null
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (_: Exception) {
            }
        }
        DriveWinLog.log("work", "painel de trabalho oculto")
    }

    private fun toggleControls() {
        val row = controlsRow ?: return
        row.visibility = if (row.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun openValueEditor(ctx: Context) {
        if (editing) return
        valueText?.visibility = View.GONE
        valueEditRow?.visibility = View.VISIBLE
        valueButtonsRow?.visibility = View.VISIBLE
        valueField?.setText("")
        enterEdit(ctx)
    }

    private fun closeValueEditor(ctx: Context) {
        valueEditRow?.visibility = View.GONE
        valueButtonsRow?.visibility = View.GONE
        valueText?.visibility = View.VISIBLE
        exitEdit(ctx)
        updateValue(ctx)
    }

    private fun submit(ctx: Context, add: Boolean) {
        val raw = valueField?.text?.toString()?.trim()?.replace(',', '.') ?: ""
        if (raw.isEmpty()) {
            closeValueEditor(ctx)
            return
        }
        val v = raw.toDoubleOrNull()
        if (v == null || v < 0.0) {
            closeValueEditor(ctx)
            return
        }
        val total = if (add) Work.addEarning(ctx, v) else Work.subtractEarning(ctx, v)
        valueField?.setText("")
        closeValueEditor(ctx)
        GanhoSync.push(ctx, Work.key(), total, null)
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

    private fun updateValue(ctx: Context) {
        valueText?.text = ParsingUtils.formatMoney(Work.earningsToday(ctx))
    }

    private fun applyStyle(ctx: Context) {
        val v = view ?: return
        val prefs = Prefs(ctx)
        if (prefs.workBgOn) {
            v.background = rounded(dp(14), COLOR_BG, 0, 0)
            v.setPadding(dp(14), dp(10), dp(14), dp(10))
        } else {
            v.background = null
            v.setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        v.alpha = prefs.workOpacity
        val scale = prefs.workFontSize / 13f
        timerText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * scale)
        valueText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * scale)
        valueField?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * scale)
    }

    private fun tapOrDrag(onTap: () -> Unit): View.OnTouchListener {
        return View.OnTouchListener { _, ev ->
            val p = params ?: return@OnTouchListener false
            val root = view ?: return@OnTouchListener false
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
                            wm?.updateViewLayout(root, p)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val ctx = root.context.applicationContext
                        val prefs = Prefs(ctx)
                        prefs.workPosX = p.x
                        prefs.workPosY = p.y
                    } else {
                        onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
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
            topMargin = marginDp
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
