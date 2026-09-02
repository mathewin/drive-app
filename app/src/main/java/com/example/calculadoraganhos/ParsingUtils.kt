package com.example.calculadoraganhos

import android.graphics.Rect
import java.util.Locale

data class TextItem(val text: String, val bounds: Rect, val viewId: String? = null)

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
    val confirmed: Boolean = false
)

object ParsingUtils {

    private val RE_MONEY = Regex(
        "R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val RE_PER_KM = Regex("R\\$\\s*[0-9][0-9.,]*\\s*(?:/|por)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_PER_H = Regex("R\\$\\s*[0-9][0-9.,]*\\s*(?:/|por)\\s*h(?:ora)?", RegexOption.IGNORE_CASE)
    private val RE_KM = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_METER = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*m(?!in|i)", RegexOption.IGNORE_CASE)
    private val RE_HM = Regex("([0-9]{1,2})h\\s*(?:([0-9]{1,2})(?:m(?:in)?)?)?", RegexOption.IGNORE_CASE)
    private val RE_MIN = Regex("([0-9]{1,3})\\s*(?:min(?:uto)?s?)", RegexOption.IGNORE_CASE)
    private val RE_HOUR = Regex("([0-9]{1,2})\\s*h(?:oras?)?(?!\\w)", RegexOption.IGNORE_CASE)

    private val MONEY = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun formatMoney(v: Double): String = MONEY.format(v)

    fun formatKm(v: Double): String = String.format(Locale("pt", "BR"), "%.1f km", v)

    fun formatMin(v: Double): String {
        val m = v.toInt()
        val h = m / 60
        val rest = m % 60
        return if (h > 0) "${h}h${rest}min" else "${m}min"
    }

    fun moneyValues(texts: List<String>): List<Double> {
        val out = ArrayList<Double>()
        for (t in texts) {
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
        val clean1 = RE_PER_KM.replace(text, " ")
        val clean2 = RE_PER_H.replace(clean1, " ")
        RE_KM.find(clean2)?.let { return toDouble(it.groupValues[1]) }
        RE_METER.find(clean2)?.let { return toDouble(it.groupValues[1]) / 1000.0 }
        return null
    }

    fun kmValues(texts: List<String>): List<Double> = texts.mapNotNull { kmValue(it) }

    fun minutesValue(text: String): Double? {
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

    val OFFER_WORDS = listOf(
        "aceitar", "aceite", "aceito", "recusar", "recuse", "descartar", "rejeitar",
        "nova corrida", "nova solicita", "novo pedido", "nova chamada",
        "chegou uma corrida", "solicitacao de corrida", "pedido novo",
        "oferta de corrida", "selecionar", "escolher", "reservar",
        "radar de viagens", "aceitar corrida", "pegar corrida"
    )
}
