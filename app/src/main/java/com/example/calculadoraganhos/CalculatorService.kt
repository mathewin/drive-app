package com.example.calculadoraganhos

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CalculatorService : AccessibilityService() {

    private var lastHash: String? = null
    private var lastParseMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()?.lowercase() ?: return
        if (RIDE_PACKAGES.none { pkg.contains(it) }) {
            OverlayManager.hide(this)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastParseMs < 1500) return
        lastParseMs = now

        val allTexts = ArrayList<String>()
        collect(root, allTexts)
        if (allTexts.isEmpty() || !RideCardParser.hasOfferContext(allTexts)) {
            OverlayManager.hide(this)
            return
        }

        val hash = allTexts.joinToString("|")
        if (hash == lastHash) return
        lastHash = hash

        val cardTexts = offerCardTexts(root)
        val data = cardTexts?.let { RideCardParser.parse(it) } ?: return

        val prefs = Prefs(this)
        val res = Calculator.calculate(data, prefs.minPerKm, prefs.minPerHour)
        OverlayManager.show(this, pkg, cardTexts, data, res)
        RideHistory(this).addRide(appLabel(pkg), data.fare, data.km, data.minutes)
    }

    override fun onInterrupt() {
    }

    private fun appLabel(pkg: String): String = when {
        pkg.contains("com.ubercab") -> "Uber"
        pkg.contains("br.com.taxiapp") -> "99"
        pkg.contains("com.indrive") -> "inDriver"
        pkg.contains("io.bolt") -> "Bolt"
        else -> pkg
    }

    private fun offerCardTexts(root: AccessibilityNodeInfo): List<String>? {
        val offer = findOfferNode(root) ?: return null
        var node: AccessibilityNodeInfo? = offer
        var up = 0
        while (up < 6) {
            val n = node ?: break
            val texts = ArrayList<String>()
            collect(n, texts)
            if (RideCardParser.parse(texts) != null) return texts
            node = n.parent
            up++
        }
        return null
    }

    private fun findOfferNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val t = node.text?.toString()?.lowercase()
        if (t != null && OFFER_ACTION_WORDS.any { t.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val r = findOfferNode(c)
            if (r != null) return r
        }
        return null
    }

    private fun collect(node: AccessibilityNodeInfo, out: ArrayList<String>) {
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) out.add(t)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, out)
        }
    }

    companion object {
        val RIDE_PACKAGES = listOf(
            "com.ubercab",
            "br.com.taxiapp",
            "com.indrive",
            "io.bolt"
        )

        val OFFER_ACTION_WORDS = listOf("aceitar", "recusar", "descartar", "rejeitar", "aceite", "selecionar", "escolher", "reservar")
    }
}
