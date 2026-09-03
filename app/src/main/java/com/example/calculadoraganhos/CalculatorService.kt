package com.example.calculadoraganhos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor

class CalculatorService : AccessibilityService() {

    enum class State { IDLE, DETECTING, READING, VALIDATING, CALCULATING, DISPLAYING }

    private var state = State.IDLE
    private var lastEventMs = 0L
    private var lastHash: String? = null
    private var pending: ParsedCard? = null
    private var pendingMs = 0L
    private var lastOcrTryMs = 0L
    private var lastCtxTag = ""
    private var lastCtxMs = 0L
    private var lastDumpMs = 0L
    private var ocrCooldownMs = OCR_SLOW_MS
    private var lastTextSig = ""

    private fun logCtx(pkg: String, isUber: Boolean, msg: String) {
        val now = System.currentTimeMillis()
        val tag = if (isUber) "uber" else "99"
        if (tag != lastCtxTag || now - lastCtxMs > 2500) {
            lastCtxTag = tag
            lastCtxMs = now
            DriveWinLog.log("calc", msg)
        }
    }

    private fun dumpTexts(source: String, items: List<TextItem>) {
        val now = System.currentTimeMillis()
        if (now - lastDumpMs < 4000) return
        lastDumpMs = now
        DriveWinLog.log("calc", "--- textos vistos ($source):")
        items.take(14).forEach {
            val vid = it.viewId?.substringAfterLast('/') ?: ""
            val txt = it.text.replace("\n", " ").take(70)
            DriveWinLog.log("calc", "  [$txt]${if (vid.isNotEmpty()) " <$vid>" else ""}")
        }
    }

    private var shotExecutor: Executor? = null

    private fun tryOcr(parser: CardParserBase, isUber: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastOcrTryMs < ocrCooldownMs) return
        lastOcrTryMs = now
        DriveWinLog.log("calc", "OCR: lendo a tela por imagem (acessibilidade)")
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val ex = shotExecutor ?: HandlerThread("DriveWinShot").also { it.start() }
                    .let { st -> Executor { r -> Handler(st.looper).post(r) } }
                    .also { shotExecutor = it }
                takeScreenshot(Display.DEFAULT_DISPLAY, ex, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hb = screenshot.hardwareBuffer
                        val bmp = try {
                            val wrapped = if (Build.VERSION.SDK_INT >= 31) {
                                Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                            } else {
                                Bitmap.wrapHardwareBuffer(hb, null)
                            }
                            wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            null
                        } finally {
                            hb.close()
                        }
                        if (bmp == null) {
                            DriveWinLog.log("calc", "screenshot vazio ou falha ao converter")
                            return
                        }
                        OcrFallback.runOcrOnBitmap(bmp, { parser.parse(it) }) { card, items ->
                            if (items.isNotEmpty()) dumpTexts("ocr", items)
                            if (card != null) {
                                DriveWinLog.log("calc", "OCR retornou card - validando")
                                handleCard(card.copy(confidence = 0.6), isUber, System.currentTimeMillis())
                            } else if (ocrCooldownMs == OCR_FAST_MS) {
                                lastOcrTryMs = System.currentTimeMillis() - 400L
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        DriveWinLog.log("calc", "screenshot da acessibilidade falhou (codigo $errorCode)")
                    }
                })
            } catch (e: Exception) {
                DriveWinLog.log("calc", "erro no screenshot: ${e.message}")
            }
            return
        }
        OcrFallback.tryCaptureAndParse(this, parser = { parser.parse(it) }) { ocrCard, ocrItems ->
            DriveWinLog.log("calc", "OCR (media projection) retornou card - validando")
            if (ocrItems.isNotEmpty()) dumpTexts("ocr", ocrItems)
            handleCard(ocrCard, isUber, System.currentTimeMillis())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DriveWinLog.log("calc", "servico de acessibilidade conectado")
        setState(State.IDLE)
        if (Prefs(this).monitorOn) {
            DriveWinLog.log("calc", "monitor ligado - subindo FGS")
            try {
                RideForegroundService.start(this)
            } catch (e: Exception) {
                Log.w(TAG, "fgs start: ${e.message}")
                DriveWinLog.log("calc", "falha ao subir FGS: ${e.message}")
            }
        } else {
            DriveWinLog.log("calc", "monitor DESLIGADO - sem leitura")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!Prefs(this).monitorOn) {
            if (state == State.DISPLAYING) OverlayManager.hide()
            return
        }
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()?.lowercase() ?: return
        val isUber = pkg.contains("com.ubercab")
        val isNinetyNine = pkg.contains("br.com.taxiapp")
        if (!isUber && !isNinetyNine) {
            if (state == State.DISPLAYING) {
                OverlayManager.hide()
                DriveWinLog.log("calc", "saiu do app de corrida - card escondido")
            }
            setState(State.IDLE)
            return
        }

        logCtx(pkg, isUber, "vendo ${if (isUber) "UBER" else "99"} - aguardando oferta")
        val now = System.currentTimeMillis()
        if (now - lastEventMs < DEBOUNCE_MS) return
        lastEventMs = now

        setState(State.DETECTING)
        val items = collect(root)
        val texts = items.map { it.text }
        val parser = if (isUber) UberParser else NinetyNineParser
        val hasWords = ParsingUtils.offerContext(texts)
        val hasMoney = ParsingUtils.hasMoney(texts)
        val hasKmOrMin = ParsingUtils.kmValues(texts).isNotEmpty() ||
            ParsingUtils.minutesValues(texts).isNotEmpty()
        val canDirect = hasWords || (hasMoney && hasKmOrMin)

        if (canDirect) {
            setState(State.READING)
            val card = parser.parse(items)
            if (card != null) {
                handleCard(card, isUber, now)
                return
            }
            DriveWinLog.log("calc", "parse direto falhou (textos: ${texts.size})")
            dumpTexts("a11y", items)
        } else {
            logCtx(pkg, isUber, "app aberto SEM oferta visivel (estado $state)")
            if (items.isNotEmpty() && (hasMoney || hasKmOrMin)) dumpTexts("a11y", items)
        }

        ocrCooldownMs = if (hasMoney || hasKmOrMin || hasWords) OCR_FAST_MS else OCR_SLOW_MS
        val sig = buildString {
            if (hasMoney || hasKmOrMin || hasWords) {
                (ParsingUtils.moneyValues(texts) + ParsingUtils.kmValues(texts) +
                    ParsingUtils.minutesValues(texts))
                    .map { Math.round(it * 10) }
                    .sorted()
                    .forEach { append(it).append(',') }
            }
        }
        if (sig.isNotEmpty() && sig != lastTextSig) {
            DriveWinLog.log("calc", "nova composicao numerica na tela - OCR imediato")
            lastOcrTryMs = 0L
        }
        lastTextSig = sig
        tryOcr(parser, isUber)
        setState(State.IDLE)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        DriveWinLog.log("calc", "servico destruido")
        OverlayManager.hide()
        try {
            RideForegroundService.stop(this)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun handleCard(card: ParsedCard, isUber: Boolean, now: Long) {
        val prev = pending
        if (prev != null && now - pendingMs < CONFIRM_WINDOW_MS) {
            if (sameCard(prev, card)) {
                pending = card.copy(confirmed = true)
            } else {
                pending = card
            }
        } else {
            pending = card
        }
        pendingMs = now
        displayIfNew(card, isUber)
    }

    private fun displayIfNew(card: ParsedCard, isUber: Boolean) {
        if (!Validator.isValid(card.data)) {
            DriveWinLog.log(
                "calc",
                "dados rejeitados pelo validador: fare=${card.data.fare} " +
                    "km=${card.data.totalDistanceKm} min=${card.data.totalTimeMin} " +
                    "pickupKm=${card.data.pickupKm} tripKm=${card.data.tripKm} suspeito=${card.suspicious}"
            )
            return
        }
        val data = card.data
        val hash = "${data.fare}|${data.totalDistanceKm}|${data.totalTimeMin}"
        if (hash == lastHash) {
            DriveWinLog.log("calc", "oferta repetida ignorada")
            return
        }
        lastHash = hash

        setState(State.VALIDATING)
        val prefs = Prefs(this)
        val res = Calculator.calculate(data, prefs.minPerKm, prefs.minPerHour)
        val app = if (isUber) "Uber" else "99"

        setState(State.CALCULATING)
        setState(State.DISPLAYING)
        DriveWinLog.log(
            "calc",
            "MOSTRANDO CARD: $app R\$${data.fare} ${data.totalDistanceKm}km ${data.totalTimeMin}min"
        )
        OverlayManager.show(
            this,
            OverlayManager.OverlayContent(
                data,
                res,
                app,
                card.suspicious,
                card.confidence,
                passenger = card.passenger
            ),
            beep = prefs.overlayAlert
        )
        AppState.updateOverlayVisible(true)

        prefs.lastOffer = buildString {
            append(app).append(" · ")
            append(ParsingUtils.formatMoney(data.fare)).append(" · ")
            append(ParsingUtils.formatKm(data.totalDistanceKm)).append(" · ")
            append(ParsingUtils.formatMin(data.totalTimeMin))
        }
        prefs.pushHistory(
            listOf(
                System.currentTimeMillis().toString(),
                app,
                "%.2f".format(data.fare),
                "%.2f".format(data.totalDistanceKm),
                "%.0f".format(data.totalTimeMin),
                "%.2f".format(res.perKm),
                "%.2f".format(res.perHour),
                res.score.toString(),
                card.passenger ?: ""
            ).joinToString("|")
        )
        Log.d(
            TAG,
            "offer $app fare=${data.fare} km=${data.totalDistanceKm} min=${data.totalTimeMin} " +
                "rkm=${"%.2f".format(res.perKm)} rh=${"%.2f".format(res.perHour)} nota=${res.score} ${res.level}"
        )
    }

    private fun sameCard(a: ParsedCard, b: ParsedCard): Boolean {
        return Math.abs(a.data.fare - b.data.fare) < 0.01 &&
            Math.abs(a.data.totalDistanceKm - b.data.totalDistanceKm) < 0.05 &&
            Math.abs(a.data.totalTimeMin - b.data.totalTimeMin) < 0.5
    }

    private fun setState(s: State) {
        if (state != s) {
            state = s
            AppState.updateServiceState(s.name)
        }
    }

    private fun collect(node: AccessibilityNodeInfo): List<TextItem> {
        val out = ArrayList<TextItem>()
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) {
            val r = Rect()
            node.getBoundsInScreen(r)
            out.add(TextItem(t, r, node.viewIdResourceName))
        }
        val cd = node.contentDescription?.toString()
        if (!cd.isNullOrBlank() && cd != t) {
            val r = Rect()
            node.getBoundsInScreen(r)
            out.add(TextItem(cd, r, node.viewIdResourceName))
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            out.addAll(collect(c))
        }
        return out
    }

    companion object {
        private const val TAG = "DriveWin"
        private const val DEBOUNCE_MS = 100L
        private const val CONFIRM_WINDOW_MS = 800L
        private const val OCR_FAST_MS = 1000L
        private const val OCR_SLOW_MS = 5000L
    }
}
