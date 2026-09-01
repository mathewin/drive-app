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
        if (RIDE_PACKAGES.none { pkg.contains(it) }) return

        val now = System.currentTimeMillis()
        if (now - lastParseMs < 1500) return
        lastParseMs = now

        val texts = ArrayList<String>()
        collect(root, texts)
        if (texts.isEmpty()) return

        val hash = texts.joinToString("|")
        if (hash == lastHash) return
        lastHash = hash

        val data = RideCardParser.parse(texts) ?: return
        val prefs = Prefs(this)
        val res = Calculator.calculate(data, prefs.minPerKm, prefs.minPerHour)
        OverlayManager.show(this, data, res)
    }

    override fun onInterrupt() {
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
    }
}
