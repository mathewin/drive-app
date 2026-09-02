package com.example.calculadoraganhos

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

object OverlayManager {

    data class OverlayContent(
        val data: RideData,
        val result: CalcResult,
        val app: String,
        val suspicious: Boolean,
        val confidence: Double
    )

    var content by mutableStateOf<OverlayContent?>(null)
        private set

    var expanded by mutableStateOf(false)
        private set

    private var wm: WindowManager? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var contextRef: WeakReference<Context>? = null
    private var hideRunnable: Runnable? = null
    private var beepedHash: String? = null

    fun show(context: Context, c: OverlayContent, beep: Boolean) {
        content = c
        try {
            val ctx = context.applicationContext
            if (composeView == null) {
                contextRef = WeakReference(ctx)
                wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val view = ComposeView(ctx).apply {
                    setContent { OverlayCard() }
                }
                val p = buildParams(ctx)
                wm?.addView(view, p)
                composeView = view
                params = p
                DriveWinLog.log("ovl", "card criado na tela (overlay ok)")
            }
            AppState.updateOverlayVisible(true)
            if (beep) beepAndVibrate(ctx)
            scheduleHide(ctx)
            DriveWinLog.log(
                "ovl",
                "card atualizado: ${c.app} ${c.data.fare}km=${c.data.totalDistanceKm} min=${c.data.totalTimeMin}"
            )
        } catch (t: Throwable) {
            DriveWinLog.log("ovl", "ERRO no overlay: ${t.message}")
            content = null
            composeView = null
            params = null
        }
    }

    fun hide() {
        hideRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        hideRunnable = null
        content = null
        expanded = false
        AppState.updateOverlayVisible(false)
        val v = composeView ?: return
        composeView = null
        params = null
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
    }

    fun toggleExpanded() {
        expanded = !expanded
    }

    fun moveBy(dx: Int, dy: Int) {
        val p = params ?: return
        p.x += dx
        p.y += dy
        try {
            wm?.updateViewLayout(composeView, p)
        } catch (_: Exception) {
        }
        contextRef?.get()?.let { ctx ->
            Prefs(ctx).let { prefs ->
                prefs.overlayPositionX = p.x
                prefs.overlayPositionY = p.y
            }
        }
    }

    private fun scheduleHide(ctx: Context) {
        val runnable = Runnable { hide() }
        hideRunnable = runnable
        Handler(Looper.getMainLooper()).postDelayed(
            runnable,
            Prefs(ctx).overlayShowSeconds * 1000L
        )
    }

    private fun buildParams(ctx: Context): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        val prefs = Prefs(ctx)
        p.x = if (prefs.overlayPositionX == Int.MIN_VALUE) {
            val dp = ctx.resources.displayMetrics.density
            (ctx.resources.displayMetrics.widthPixels / 2 - 140 * dp).toInt()
        } else {
            prefs.overlayPositionX
        }
        p.y = prefs.overlayPositionY
        return p
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

    @Composable
    fun OverlayCard() {
        val c = content ?: return
        val isExpanded = expanded
        val context = LocalContext.current
        val density = LocalDensity.current
        val prefs = remember { Prefs(context) }
        val fontScale = remember { prefs.overlayFontSize / 13f }

        var drag by remember { mutableStateOf(IntOffset.Zero) }

        val levelColor = when (c.result.level) {
            Level.EXCELLENT, Level.GOOD -> Color(0xFF31F900)
            Level.MEDIUM -> Color(0xFFF5A623)
            Level.BAD -> Color(0xFFFF4D4D)
        }

        Box(
            modifier = Modifier
                .alpha(prefs.overlayOpacity)
                .offset { drag }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            drag = IntOffset(
                                drag.x + amount.x.roundToInt(),
                                drag.y + amount.y.roundToInt()
                            )
                        },
                        onDragEnd = {
                            OverlayManager.moveBy(drag.x, drag.y)
                            drag = IntOffset.Zero
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { OverlayManager.toggleExpanded() }
                .background(Color(0xFF0E0F12), RoundedCornerShape(14.dp))
                .border(2.dp, levelColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier.width(((if (isExpanded) 250 else 220) * fontScale).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "DRIVEWIN",
                        color = Color(0xFFC864AF),
                        fontSize = (11 * fontScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        c.result.level.label,
                        color = Color(0xFF0E0F12),
                        fontSize = (10 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(levelColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "${ParsingUtils.formatMoney(c.result.perKm)}/km",
                    color = Color.White,
                    fontSize = (20 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${ParsingUtils.formatMoney(c.result.perHour)}/h",
                    color = Color(0xFFC8C8C8),
                    fontSize = (14 * fontScale).sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "NOTA ${c.result.score}/100",
                    color = levelColor,
                    fontSize = (12 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )

                if (isExpanded) {
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Valor", ParsingUtils.formatMoney(c.data.fare), fontScale, Color.White)
                    InfoRow("Distancia", ParsingUtils.formatKm(c.result.totalKm), fontScale, Color(0xFFC8C8C8))
                    InfoRow("Tempo", ParsingUtils.formatMin(c.result.totalMin), fontScale, Color(0xFFC8C8C8))
                    if (c.suspicious) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Dados suspeitos",
                            color = Color(0xFFFF4D4D),
                            fontSize = (10 * fontScale).sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String, fontScale: Float, color: Color) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF888888), fontSize = (11 * fontScale).sp)
            Text(value, color = color, fontSize = (12 * fontScale).sp, fontWeight = FontWeight.Medium)
        }
    }
}
