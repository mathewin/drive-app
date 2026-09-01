package com.example.calculadoraganhos

object RideCardParser {

    private val RE_CURRENCY = Regex("R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)")
    private val RE_FALLBACK = Regex("(?<![0-9])([0-9]{1,3},[0-9]{2})(?![0-9])")
    private val RE_KM = Regex("([0-9]{1,3}(?:[.,][0-9]+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_MIN = Regex("([0-9]{1,3})\\s*(?:min|minutos|minuto)", RegexOption.IGNORE_CASE)

    private val OFFER_KEYWORDS = listOf(
        "aceitar",
        "aceite",
        "aceito",
        "recusar",
        "recuse",
        "descartar",
        "rejeitar",
        "nova corrida",
        "nova solicita",
        "novo pedido",
        "nova chamada",
        "chegou uma corrida",
        "solicitacao de corrida",
        "pedido novo",
        "oferta de corrida"
    )

    fun hasOfferContext(texts: List<String>): Boolean {
        return texts.any { t ->
            val lower = t.lowercase()
            OFFER_KEYWORDS.any { lower.contains(it) }
        }
    }

    fun parse(texts: List<String>): RideData? {
        val all = texts.map { it.trim() }.filter { it.isNotBlank() }
        if (all.isEmpty()) return null

        val fare = parseFare(all) ?: return null
        val km = all.flatMap { RE_KM.findAll(it).map { m -> toNumber(m.groupValues[1]) } }.sum()
        val min = all.flatMap { RE_MIN.findAll(it).map { m -> toNumber(m.groupValues[1]) } }.sum()

        if (km <= 0 && min <= 0) return null
        return RideData(fare, km, min)
    }

    private fun parseFare(texts: List<String>): Double? {
        var best: Double? = null
        for (t in texts) {
            val m = RE_CURRENCY.find(t)
            if (m != null) {
                val v = toNumber(m.groupValues[1])
                if (v > 0 && (best == null || v > best)) best = v
            }
        }
        if (best != null) return best
        for (t in texts) {
            val m = RE_FALLBACK.find(t)
            if (m != null) {
                val v = toNumber(m.groupValues[1])
                if (v in 5.0..500.0) return v
            }
        }
        return null
    }

    private fun toNumber(s: String): Double {
        val hasComma = s.contains(',')
        val hasDot = s.contains('.')
        val cleaned = when {
            hasComma && hasDot -> s.replace(".", "").replace(',', '.')
            hasComma -> s.replace(',', '.')
            else -> s
        }
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
