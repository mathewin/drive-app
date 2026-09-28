package com.example.calculadoraganhos

import android.graphics.Rect
import java.util.Locale

const val BOTTOM_REGION_FRACTION = 0.45

data class TextItem(val text: String, val bounds: Rect, val viewId: String? = null)

fun inBottomRegion(bounds: Rect, screenHeight: Int): Boolean {
    if (screenHeight <= 0) return true
    return bounds.centerY() >= (screenHeight * BOTTOM_REGION_FRACTION).toInt()
}

data class RideData(
    val fare: Double,
    val pickupKm: Double = 0.0,
    val tripKm: Double = 0.0,
    val totalKm: Double = 0.0,
    val pickupMin: Double = 0.0,
    val tripMin: Double = 0.0,
    val totalMin: Double = 0.0
) {
    val totalDistanceKm: Double
        get() = when {
            pickupKm > 0 && tripKm > 0 -> pickupKm + tripKm
            totalKm > 0 -> totalKm
            else -> pickupKm + tripKm
        }

    val totalTimeMin: Double
        get() = when {
            pickupMin > 0 && tripMin > 0 -> pickupMin + tripMin
            totalMin > 0 -> totalMin
            else -> pickupMin + tripMin
        }
}

data class ParsedCard(
    val data: RideData,
    val confidence: Double = 1.0,
    val suspicious: Boolean = false,
    val confirmed: Boolean = false,
    val passenger: String? = null,
    val pickup: String? = null,
    val dropoff: String? = null
)

object ParsingUtils {

    private val RE_MONEY = Regex(
        "R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val RE_PER_KM = Regex("R\\$\\s*[0-9][0-9.,]*\\s*(?:/|por)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_PER_KM_VALUE = Regex(
        "R\\$\\s*([0-9][0-9.,]*)\\s*(?:/|por)\\s*km",
        RegexOption.IGNORE_CASE
    )
    private val RE_PER_H = Regex("R\\$\\s*[0-9][0-9.,]*\\s*(?:/|por)\\s*h(?:ora)?", RegexOption.IGNORE_CASE)
    private val RE_KM = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_METER = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*m(?!in|i)", RegexOption.IGNORE_CASE)
    private val RE_HM = Regex("([0-9]{1,2})h\\s*(?:([0-9]{1,2})(?:m(?:in)?)?)?", RegexOption.IGNORE_CASE)
    private val RE_MIN = Regex("([0-9]{1,3})\\s*(?:min(?:uto)?s?)", RegexOption.IGNORE_CASE)
    private val RE_HOUR = Regex("([0-9]{1,2})\\s*h(?:oras?)?(?!\\w)", RegexOption.IGNORE_CASE)
    private val RE_RATING_TRIPS = Regex("""(\d[.,]\d{1,2})\s*\(\s*\d[\d.,]*\s*\)""")
    private val RE_RATING = Regex("""^\s*(\d[.,]\d{1,2})\s*$""")
    private val RE_PASSENGER_COMBINED = Regex(
        """(\d[.,]\d{1,2})\s*(?:[-–·•|]\s*\d[\d.,]*\s*)?(?:\d[\d.,]*\s*)?(?:corridas?|viagens?|avaliacoes?|avaliações?|trips?|verificad|confirmad)""",
        RegexOption.IGNORE_CASE
    )
    private val RE_PASSENGER_SIGNAL = Regex(
        """(corridas?|viagens?|avaliacoes?|avaliações?|trips?|verificad|confirmad|\(\s*\d[\d.,]*)""",
        RegexOption.IGNORE_CASE
    )

    private val MONEY = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun formatMoney(v: Double): String = MONEY.format(v)

    fun formatKm(v: Double): String = String.format(Locale("pt", "BR"), "%.1f km", v)

    fun formatMin(v: Double): String {
        val m = v.toInt()
        val h = m / 60
        val rest = m % 60
        return if (h > 0) "${h}h${rest}min" else "${m}min"
    }

    private fun ratingValue(s: String): Double? {
        val v = toDouble(s.trim()) ?: return null
        return if (v in 3.0..5.2) v else null
    }

    private fun normRating(s: String): String = s.trim().replace('.', ',')

    fun passengerRating(texts: List<String>): String? {
        var plain: String? = null
        var signal = false
        for (t in texts) {
            RE_PASSENGER_COMBINED.find(t)?.let { m ->
                val v = ratingValue(m.groupValues[1])
                if (v != null) return normRating(m.groupValues[1])
            }
            RE_RATING_TRIPS.find(t)?.let { m ->
                val v = ratingValue(m.groupValues[1])
                if (v != null) return normRating(m.groupValues[1])
            }
            val m = RE_RATING.find(t)
            if (m != null) {
                val v = ratingValue(m.groupValues[1])
                if (v != null && plain == null) plain = normRating(m.groupValues[1])
            }
            if (RE_PASSENGER_SIGNAL.containsMatchIn(t)) signal = true
        }
        if (plain == null) return null
        return if (signal) plain else null
    }

    private val RE_CLOCK = Regex("""^\s*\d{1,2}:\d{2}(?::\d{2})?\s*$""")
    private val RE_LEG = Regex(
        """(\d{1,3})\s*(?:min(?:uto)?s?)\s*\(\s*([0-9]+(?:[.,][0-9]+)?)\s*km\s*\)""",
        RegexOption.IGNORE_CASE
    )

    fun isNoiseLine(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val l = t.lowercase(Locale.ROOT)
        if (RE_CLOCK.matches(t)) return true
        if (RE_PER_KM.containsMatchIn(l) || l.contains("/km") || l.contains("por km")) return true
        if (l.contains("aprox")) return true
        if (l.contains("tarifa") && (l.contains("inclus") || l.contains("dinamic") || l.contains("expresso"))) return true
        if (l.contains("viagem longa") || (l.contains("mais de") && l.contains("min"))) return true
        if (l.contains("no ar") || l.startsWith("hoje ") || l.contains("hoje ")) return true
        if (l == "trabalho" || l == "pausar" || l == "zerar" || l == "iniciar" || l == "ok" || l == "valor") return true
        if (l == "perfil premium" || l == "verificado") return true
        return false
    }

    fun statedPerKm(texts: List<String>): Double? {
        for (t in texts) {
            val m = RE_PER_KM_VALUE.find(t) ?: continue
            val v = toDouble(m.groupValues[1])
            if (v > 0) return v
        }
        return null
    }

    fun offerItems(items: List<TextItem>): List<TextItem> {
        val useful = items.filter { !isNoiseLine(it.text) }
        if (useful.isEmpty()) return useful
        val screenH = useful.maxOf { it.bounds.bottom }.coerceAtLeast(1)
        val bottom = useful.filter { it.bounds.centerY() >= (screenH * 0.28).toInt() }
        return if (bottom.any { moneyValues(listOf(it.text)).isNotEmpty() }) bottom else useful
    }

    fun offerLeg(text: String): Pair<Double, Double>? {
        val m = RE_LEG.find(text) ?: return null
        val min = toDouble(m.groupValues[1])
        val km = toDouble(m.groupValues[2])
        if (min <= 0 || km <= 0) return null
        return min to km
    }

    fun extractLegs(items: List<TextItem>): List<Pair<Double, Double>> {
        val sorted = items.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0
        while (i < sorted.size) {
            val t = sorted[i].text
            val combined = offerLeg(t)
            if (combined != null) {
                out.add(combined)
                i++
                continue
            }
            val min = minutesValue(t)
            val kmHere = kmValue(t)
            if (min != null && min > 0 && kmHere != null && kmHere > 0) {
                out.add(min to kmHere)
                i++
                continue
            }
            if (min != null && min > 0 && i + 1 < sorted.size) {
                val next = sorted[i + 1].text
                val kmNext = kmValue(next)
                if (kmNext != null && kmNext > 0 && minutesValue(next) == null) {
                    out.add(min to kmNext)
                    i += 2
                    continue
                }
            }
            i++
        }
        return out
    }

    fun moneyValues(texts: List<String>): List<Double> {
        val out = ArrayList<Double>()
        for (t in texts) {
            if (isNoiseLine(t)) continue
            val clean1 = RE_PER_KM.replace(t, " ")
            val clean2 = RE_PER_H.replace(clean1, " ")
            for (m in RE_MONEY.findAll(clean2)) {
                val v = toDouble(m.groupValues[1])
                if (v > 0) out.add(v)
            }
        }
        return out
    }

    fun kmValue(text: String): Double? {
        if (isNoiseLine(text)) return null
        val clean1 = RE_PER_KM.replace(text, " ")
        val clean2 = RE_PER_H.replace(clean1, " ")
        RE_KM.find(clean2)?.let { return toDouble(it.groupValues[1]) }
        RE_METER.find(clean2)?.let { return toDouble(it.groupValues[1]) / 1000.0 }
        return null
    }

    fun kmValues(texts: List<String>): List<Double> = texts.mapNotNull { kmValue(it) }

    fun minutesValue(text: String): Double? {
        if (isNoiseLine(text)) return null
        RE_HM.find(text)?.let {
            val h = toDouble(it.groupValues[1])
            val m = if (it.groupValues[2].isNotEmpty()) toDouble(it.groupValues[2]) else 0.0
            return h * 60 + m
        }
        RE_MIN.find(text)?.let { return toDouble(it.groupValues[1]) }
        RE_HOUR.find(text)?.let { return toDouble(it.groupValues[1]) * 60 }
        return null
    }

    fun minutesValues(texts: List<String>): List<Double> = texts.mapNotNull { minutesValue(it) }

    fun toDouble(s: String): Double {
        val hasComma = s.contains(',')
        val hasDot = s.contains('.')
        val cleaned = when {
            hasComma && hasDot -> s.replace(".", "").replace(',', '.')
            hasComma -> s.replace(',', '.')
            else -> s
        }
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun offerContext(texts: List<String>): Boolean {
        return texts.any { t ->
            val l = t.lowercase()
            OFFER_WORDS.any { l.contains(it) }
        }
    }

    fun hasMoney(texts: List<String>): Boolean = texts.any { it.contains('$') }

    val OFFER_WORDS = listOf(
        "aceitar", "aceite", "aceito", "accept", "accepted", "accept ride",
        "recusar", "recuse", "decline", "descartar", "rejeitar",
        "nova corrida", "nova solicita", "novo pedido", "nova chamada",
        "new request", "new trip", "ride request", "nova oferta", "new offer",
        "chegou uma corrida", "solicitacao de corrida", "pedido novo",
        "oferta de corrida", "selecionar", "escolher", "reservar", "book",
        "radar de viagens", "aceitar corrida", "pegar corrida",
        "toque para aceitar", "tocar para aceitar", "disponivel para voce"
    )
}
