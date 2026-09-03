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
    private var lastHash: String? = null
    private var shownCard: ParsedCard? = null
    private var shownIsUber = true
    private var lastShownMs = 0L
    private var stableOcr: ParsedCard? = null
    private var stableOcrMs = 0L
    private var lastOcrTryMs = 0L
    private var lastDumpMs = 0L
    private var ocrCooldownMs = OCR_IDLE_MS
    private var scanSig = ""
    private var lastOfferishMs = 0L
    private var lastOfferMs = 0L
    private var ocrInFlight = false
    private var lastScannerUber = true
    private var lastOfferish = false
    private var lastForegroundRide = false
    private var lastRideEvtMs = 0L
    private var scanThread: HandlerThread? = null
    private var scanHandler: Handler? = null
    private var scanPosted = false
    private var offerAbsentSince = 0L
    private var rideGoneSince = 0L
    private var lastOcrNudgeMs = 0L
    private var dismissedCard: ParsedCard? = null

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
        if (ocrInFlight) return
        lastOcrTryMs = now
        ocrInFlight = true
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
                            ocrInFlight = false
                            DriveWinLog.log("calc", "screenshot vazio ou falha ao converter")
                            return
                        }
                        val regionBmp = OcrFallback.cropBottomRegion(bmp)
                        if (regionBmp !== bmp) bmp.recycle()
                        OcrFallback.runOcrOnBitmap(regionBmp, { parser.parse(it) }) { card, items ->
                            ocrInFlight = false
                            if (items.isNotEmpty()) dumpTexts("ocr", items)
                            if (card != null) {
                                DriveWinLog.log("calc", "OCR retornou card - validando")
                                handleCard(card.copy(confidence = 0.6), isUber, System.currentTimeMillis())
                            } else if (lastOfferish) {
                                lastOcrTryMs = System.currentTimeMillis() - 350L
                            }
                            regionBmp.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        ocrInFlight = false
                        DriveWinLog.log("calc", "screenshot da acessibilidade falhou (codigo $errorCode)")
                    }
                })
            } catch (e: Exception) {
                ocrInFlight = false
                DriveWinLog.log("calc", "erro no screenshot: ${e.message}")
            }
            return
        }
        OcrFallback.tryCaptureAndParse(this, parser = { parser.parse(it) }) { ocrCard, ocrItems ->
            ocrInFlight = false
            DriveWinLog.log("calc", "OCR (media projection) retornou card - validando")
            if (ocrItems.isNotEmpty()) dumpTexts("ocr", ocrItems)
            handleCard(ocrCard, isUber, System.currentTimeMillis())
        }
        ocrInFlight = false
    }

    private fun startScanner() {
        if (scanThread != null) return
        val st = HandlerThread("DriveWinScan").also { it.start() }
        scanThread = st
        scanHandler = Handler(st.looper)
        scanPosted = false
        DriveWinLog.log("calc", "scanner de leitura continuo iniciado")
        scanHandler?.post { scanPosted = false; scheduleScan() }
    }

    private fun stopScanner() {
        scanThread?.let { st ->
            try {
                scanHandler?.removeCallbacksAndMessages(null)
            } catch (_: Exception) {
            }
            try {
                st.quitSafely()
            } catch (_: Exception) {
            }
        }
        scanThread = null
        scanHandler = null
        scanPosted = false
    }

    private fun scheduleScan() {
        if (!scanPosted && scanHandler != null) {
            scanPosted = true
            scanHandler?.postDelayed(::scanTick, SCAN_INTERVAL_MS)
        }
    }

    private fun noteOfferPresent() {
        offerAbsentSince = 0L
    }

    private fun registerCardCloseRelease() {
        OverlayManager.onCardClosed = {
            stableOcr = null
            stableOcrMs = 0L
            val cur = shownCard
            if (cur != null) {
                dismissedCard = cur
            }
            DriveWinLog.log(
                "calc",
                "card fechado pelo toque - esta oferta nao reaparece enquanto estiver na tela"
            )
        }
    }

    private fun noteOfferAbsent() {
        val now = System.currentTimeMillis()
        val stale = shownCard != null || state != State.IDLE ||
            scanSig.isNotEmpty() || lastHash != null
        if (!stale) {
            offerAbsentSince = 0L
            return
        }
        if (offerAbsentSince == 0L) {
            offerAbsentSince = now
            return
        }
        if (now - offerAbsentSince < OFFER_ABSENT_CONFIRM_MS) return
        offerAbsentSince = 0L
        if (state == State.DISPLAYING || shownCard != null) {
            OverlayManager.hide()
            DriveWinLog.log("calc", "oferta saiu da tela - card escondido")
        }
        resetOfferState()
        setState(State.IDLE)
        DriveWinLog.log("calc", "travas de oferta liberadas - proxima chamada sera lida")
    }

    private fun rideWindowPackageAndRoot(): Pair<String?, AccessibilityNodeInfo?> {
        try {
            for (w in windows) {
                val r = w.root ?: continue
                val p = r.packageName?.toString()?.lowercase() ?: continue
                if (p.contains("com.ubercab") || p.contains("br.com.taxiapp")) {
                    return p to r
                }
            }
        } catch (_: Exception) {
        }
        val r2 = rootInActiveWindow
        val p2 = r2?.packageName?.toString()?.lowercase()
        if (p2 != null && (p2.contains("com.ubercab") || p2.contains("br.com.taxiapp"))) {
            return p2 to r2
        }
        return null to null
    }

    private fun updateRideFocus(now: Long): Boolean {
        val (pkg, _) = rideWindowPackageAndRoot()
        if (pkg != null) {
            lastForegroundRide = true
            lastRideEvtMs = now
            lastScannerUber = pkg.contains("com.ubercab")
            return true
        }
        val active = rootInActiveWindow
        val ap = active?.packageName?.toString()?.lowercase() ?: ""
        if (ap == "com.example.calculadoraganhos") return false
        if (ap.contains("com.ubercab") || ap.contains("br.com.taxiapp")) {
            lastForegroundRide = true
            lastRideEvtMs = now
            lastScannerUber = ap.contains("com.ubercab")
            return true
        }
        return lastForegroundRide && now - lastRideEvtMs < RIDE_FALLBACK_MS
    }

    private fun scanTick() {
        scanPosted = false
        if (!Prefs(this).monitorOn) {
            stopScanner()
            return
        }
        scheduleScan()
        val now = System.currentTimeMillis()
        if (!updateRideFocus(now)) {
            ocrCooldownMs = OCR_IDLE_MS
            if (shownCard != null || state == State.DISPLAYING) {
                if (rideGoneSince == 0L) rideGoneSince = now
                if (now - rideGoneSince >= RIDE_GONE_CONFIRM_MS) {
                    rideGoneSince = 0L
                    OverlayManager.hide()
                    resetOfferState()
                    setState(State.IDLE)
                    DriveWinLog.log("calc", "janela de corrida sumiu - card escondido")
                }
            }
            return
        }
        rideGoneSince = 0L
        val parser = if (lastScannerUber) UberParser else NinetyNineParser
        if (treeDetectOffer(parser, lastScannerUber)) return
        val displayingTreeCard = state == State.DISPLAYING && scanSig.isNotEmpty()
        ocrCooldownMs = when {
            displayingTreeCard -> -1L
            now - lastOfferishMs < OFFER_ACTIVE_MS -> OCR_FAST_MS
            else -> OCR_IDLE_MS
        }
        if (ocrCooldownMs > 0L) tryOcr(parser, lastScannerUber)
    }

    private fun treeDetectOffer(parser: CardParserBase, isUber: Boolean): Boolean {
        val (_, root) = rideWindowPackageAndRoot()
        if (root == null) return false
        val all = collect(root)
        if (all.isEmpty()) {
            noteOfferAbsent()
            return false
        }
        val screenH = all.maxOf { it.bounds.bottom }
        val items = all.filter { inBottomRegion(it.bounds, screenH) }
        if (items.isEmpty()) {
            noteOfferAbsent()
            return false
        }
        val texts = items.map { it.text }
        val hasMoney = ParsingUtils.moneyValues(texts).isNotEmpty()
        val hasKmOrMin = ParsingUtils.kmValues(texts).isNotEmpty() ||
            ParsingUtils.minutesValues(texts).isNotEmpty()
        if (!hasMoney && !hasKmOrMin) {
            noteOfferAbsent()
            if (ParsingUtils.offerContext(texts)) {
                lastOfferish = true
                lastOfferishMs = System.currentTimeMillis()
            }
            return false
        }
        if (hasMoney && hasKmOrMin) {
            noteOfferPresent()
            lastOfferMs = System.currentTimeMillis()
        }
        val sig = buildString {
            (ParsingUtils.moneyValues(texts) + ParsingUtils.kmValues(texts) +
                ParsingUtils.minutesValues(texts))
                .map { Math.round(it * 10) }
                .sorted()
                .forEach { append(it).append(',') }
        }
        if (sig == scanSig) return false
        scanSig = sig
        lastOfferish = true
        lastOfferishMs = System.currentTimeMillis()
        val card = parser.parse(items)
        if (card != null) {
            DriveWinLog.log(
                "calc",
                "oferta lida pela arvore de acessibilidade (scan rapido) - " +
                    "fare=${card.data.fare} km=${card.data.totalDistanceKm} min=${card.data.totalTimeMin}"
            )
            handleCard(card, isUber, System.currentTimeMillis())
            return true
        }
        return false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DriveWinLog.log("calc", "servico de acessibilidade conectado")
        setState(State.IDLE)
        registerCardCloseRelease()
        if (Prefs(this).monitorOn) {
            DriveWinLog.log("calc", "monitor ligado - subindo FGS")
            try {
                RideForegroundService.start(this)
            } catch (e: Exception) {
                Log.w(TAG, "fgs start: ${e.message}")
                DriveWinLog.log("calc", "falha ao subir FGS: ${e.message}")
            }
            startScanner()
        } else {
            DriveWinLog.log("calc", "monitor DESLIGADO - sem leitura")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!Prefs(this).monitorOn) {
            if (state == State.DISPLAYING) OverlayManager.hide()
            stopScanner()
            return
        }
        startScanner()
        val now = System.currentTimeMillis()
        val pkg = rootInActiveWindow?.packageName?.toString()?.lowercase()
        val rideActive = pkg?.contains("com.ubercab") == true ||
            pkg?.contains("br.com.taxiapp") == true
        if (rideActive) {
            lastForegroundRide = true
            lastRideEvtMs = now
            lastScannerUber = pkg!!.contains("com.ubercab")
            val t = event?.eventType ?: 0
            val contentChanged = t == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                t == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            val displayingTreeCard = state == State.DISPLAYING && scanSig.isNotEmpty()
            if (contentChanged && !displayingTreeCard && now - lastOcrNudgeMs >= OCR_NUDGE_MS) {
                lastOcrNudgeMs = now
                lastOcrTryMs = 0L
                ocrCooldownMs = OCR_FAST_MS
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        DriveWinLog.log("calc", "servico destruido")
        OverlayManager.onCardClosed = null
        stopScanner()
        OverlayManager.hide()
        try {
            RideForegroundService.stop(this)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun handleCard(card: ParsedCard, isUber: Boolean, now: Long) {
        val nowMs = if (now > 0L) now else System.currentTimeMillis()
        val dis = dismissedCard
        if (dis != null && similar(dis, card)) {
            DriveWinLog.log(
                "calc",
                "oferta fechada pelo toque ainda na tela - sem releitura " +
                    "fare=${card.data.fare} km=${card.data.totalDistanceKm} min=${card.data.totalTimeMin}"
            )
            return
        }
        val disp = shownCard
        if (disp != null && similar(disp, card)) {
            val cardStillUp = OverlayManager.isVisible() || (nowMs - lastShownMs < holdMs())
            val oldOfferStillOnScreen = nowMs - lastOfferMs < FRESH_OFFER_GAP_MS
            if (cardStillUp && oldOfferStillOnScreen) {
                if (disp.passenger == null && card.passenger != null) {
                    refreshPassenger(card)
                } else {
                    DriveWinLog.log(
                        "calc",
                        "mesma oferta ainda na tela (variacao de leitura) - mantendo " +
                            "fare=${card.data.fare} km=${card.data.totalDistanceKm} min=${card.data.totalTimeMin}"
                    )
                }
                return
            }
        }
        if (dis != null) dismissedCard = null
        if (card.confidence >= 1.0) {
            displayIfNew(card, isUber)
            return
        }
        if (disp == null) {
            displayIfNew(card, isUber)
            return
        }
        val st = stableOcr
        if (st != null && similar(st, card) && nowMs - stableOcrMs >= OCR_CONFIRM_MIN_MS) {
            stableOcr = null
            DriveWinLog.log(
                "calc",
                "leitura OCR consistente em 2 telas - trocando card " +
                    "fare=${card.data.fare} km=${card.data.totalDistanceKm} min=${card.data.totalTimeMin}"
            )
            displayIfNew(card, isUber)
        } else {
            stableOcr = card
            stableOcrMs = nowMs
        }
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
        shownCard = card
        shownIsUber = isUber
        lastShownMs = System.currentTimeMillis()
        stableOcr = null

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
            beep = prefs.overlayAlert,
            hold = true
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

    private fun similar(a: ParsedCard, b: ParsedCard): Boolean {
        val x = a.data
        val y = b.data
        fun near(p: Double, q: Double, floor: Double): Boolean {
            return Math.abs(p - q) <= Math.max(floor, Math.max(p, q) * 0.25)
        }
        return near(x.fare, y.fare, 1.0) &&
            near(x.totalDistanceKm, y.totalDistanceKm, 0.5) &&
            near(x.totalTimeMin, y.totalTimeMin, 1.0)
    }

    private fun holdMs(): Long {
        return (Prefs(this).overlayShowSeconds * 1000L) + 2000L
    }

    private fun resetOfferState() {
        shownCard = null
        lastShownMs = 0L
        lastHash = null
        stableOcr = null
        stableOcrMs = 0L
        scanSig = ""
        dismissedCard = null
        offerAbsentSince = 0L
    }

    private fun refreshPassenger(card: ParsedCard) {
        val old = shownCard ?: return
        if (old.passenger != null || card.passenger == null) return
        shownCard = old.copy(passenger = card.passenger)
        val prefs = Prefs(this)
        val res = Calculator.calculate(old.data, prefs.minPerKm, prefs.minPerHour)
        val app = if (shownIsUber) "Uber" else "99"
        DriveWinLog.log("calc", "PASSAGEIRO detectado em leitura seguinte - atualizando card (${card.passenger})")
        OverlayManager.show(
            this,
            OverlayManager.OverlayContent(
                old.data,
                res,
                app,
                old.suspicious,
                old.confidence,
                passenger = card.passenger
            ),
            beep = false,
            hold = true
        )
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
        private const val OCR_CONFIRM_MIN_MS = 400L
        private const val SCAN_INTERVAL_MS = 300L
        private const val OFFER_ABSENT_CONFIRM_MS = 2500L
        private const val RIDE_FALLBACK_MS = 6000L
        private const val RIDE_GONE_CONFIRM_MS = 2000L
        private const val OCR_FAST_MS = 600L
        private const val OCR_IDLE_MS = 3000L
        private const val OCR_NUDGE_MS = 1500L
        private const val OFFER_ACTIVE_MS = 8000L
        private const val FRESH_OFFER_GAP_MS = 2500L
    }
}
