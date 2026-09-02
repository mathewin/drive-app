package com.example.calculadoraganhos

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CalculatorService : AccessibilityService() {

    enum class State { IDLE, DETECTING, READING, VALIDATING, CALCULATING, DISPLAYING }

    private var state = State.IDLE
    private var lastEventMs = 0L
    private var lastHash: String? = null
    private var pending: ParsedCard? = null
    private var pendingMs = 0L
    private var ocrAttempted = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "service connected")
        setState(State.IDLE)
        try {
            RideForegroundService.start(this)
        } catch (e: Exception) {
            Log.w(TAG, "fgs start: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()?.lowercase() ?: return
        val isUber = pkg.contains("com.ubercab")
        val isNinetyNine = pkg.contains("br.com.taxiapp")
        if (!isUber && !isNinetyNine) {
            if (state == State.DISPLAYING) OverlayManager.hide()
            setState(State.IDLE)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastEventMs < DEBOUNCE_MS) return
        lastEventMs = now

        setState(State.DETECTING)
        val items = collect(root)
        val texts = items.map { it.text }
        if (items.isEmpty() || !ParsingUtils.offerContext(texts)) {
            setState(State.IDLE)
            return
        }

        setState(State.READING)
        val parser = if (isUber) UberParser else NinetyNineParser
        val card = parser.parse(items)
        if (card == null) {
            if (!ocrAttempted) {
                ocrAttempted = true
                Log.d(TAG, "direct read falhou, tentando OCR")
                OcrFallback.tryCaptureAndParse(
                    this,
                    parser = { parser.parse(it) }
                ) { ocrCard, _ ->
                    handleCard(ocrCard, isUber, System.currentTimeMillis())
                }
            }
            return
        }
        handleCard(card, isUber, now)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
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
        if (!Validator.isValid(card.data)) return
        val data = card.data
        val hash = "${data.fare}|${data.totalDistanceKm}|${data.totalTimeMin}"
        if (hash == lastHash) return
        lastHash = hash
        ocrAttempted = false

        setState(State.VALIDATING)
        val prefs = Prefs(this)
        val res = Calculator.calculate(data, prefs.minPerKm, prefs.minPerHour)
        val app = if (isUber) "Uber" else "99"

        setState(State.CALCULATING)
        setState(State.DISPLAYING)
        OverlayManager.show(
            this,
            OverlayManager.OverlayContent(data, res, app, card.suspicious, card.confidence),
            beep = prefs.overlayAlert
        )
        AppState.updateOverlayVisible(true)

        prefs.lastOffer = buildString {
            append(app).append(" · ")
            append(ParsingUtils.formatMoney(data.fare)).append(" · ")
            append(ParsingUtils.formatKm(data.totalDistanceKm)).append(" · ")
            append(ParsingUtils.formatMin(data.totalTimeMin))
        }
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
        private const val DEBOUNCE_MS = 150L
        private const val CONFIRM_WINDOW_MS = 800L
    }
}
