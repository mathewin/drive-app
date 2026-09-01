package com.example.calculadoraganhos

object RideCardParser {

    private val RE_CURRENCY = Regex("R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)")
    private val RE_ACEITAR = Regex("aceitar\\s*por\\s*R\\$\\s*([0-9][0-9.,]*)", RegexOption.IGNORE_CASE)
    private val RE_PER_KM = Regex("R\\$\\s*[0-9][0-9.,]*\\s*(?:/|por)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_FALLBACK = Regex("(?<![0-9])([0-9]{1,3},[0-9]{2})(?![0-9])")
    private val RE_KM = Regex("([0-9]{1,3}(?:[.,][0-9]+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val RE_MIN = Regex("([0-9]{1,3})\\s*(?:min|minutos|minuto)", RegexOption.IGNORE_CASE)
    private val RE_HM = Regex("([0-9]+)h\\s*([0-9]+)m", RegexOption.IGNORE_CASE)

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

        val km = all.flatMap { RE_KM.findAll(it).map { m -> toNumber(m.groupValues[1]) } }
            .maxOrNull() ?: 0.0

        val min = all.flatMap {
            RE_MIN.findAll(it).map { m -> toNumber(m.groupValues[1]) } +
                RE_HM.findAll(it).map { m -> toNumber(m.groupValues[1]) * 60 + toNumber(m.groupValues[2]) }
        }.maxOrNull() ?: 0.0

        if (km <= 0 && min <= 0) return null
        return RideData(fare, km, min)
    }

    private fun parseFare(texts: List<String>): Double? {
        for (t in texts) {
            val m = RE_ACEITAR.find(t)
            if (m != null) {
                val v = toNumber(m.groupValues[1])
                if (v > 0) return v
            }
        }

        var best: Double? = null
        for (t in texts) {
            val clean = RE_PER_KM.replace(t, " ")
            for (m in RE_CURRENCY.findAll(clean)) {
                val v = toNumber(m.groupValues[1])
                if (v > 0 && (best == null || v > best)) best = v
            }
        }
        if (best != null) return best

        for (t in texts) {
            val clean = RE_PER_KM.replace(t, " ")
            for (m in RE_FALLBACK.findAll(clean)) {
                val v = toNumber(m.groupValues[1])
                if (v in 5.0..500.0 && (best == null || v > best)) best = v
            }
        }
        return best
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
