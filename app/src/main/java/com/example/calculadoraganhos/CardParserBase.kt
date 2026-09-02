package com.example.calculadoraganhos

abstract class CardParserBase {

    abstract val fareWords: List<String>
    abstract val pickupKmWords: List<String>
    abstract val tripKmWords: List<String>
    abstract val pickupMinWords: List<String>
    abstract val tripMinWords: List<String>

    fun parse(items: List<TextItem>): ParsedCard? {
        val texts = items.map { it.text }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return null
        val fare = parseFare(texts) ?: return null
        val (pickupKm, tripKm, totalKm) = parseDistances(items)
        val (pickupMin, tripMin, totalMin) = parseTimes(items)
        val data = RideData(fare, pickupKm, tripKm, totalKm, pickupMin, tripMin, totalMin)
        if (data.totalDistanceKm <= 0 && data.totalTimeMin <= 0) return null
        return ParsedCard(data, suspicious = Validator.suspicious(data))
    }

    private fun parseFare(texts: List<String>): Double? {
        for (t in texts) {
            val lower = t.lowercase()
            for (kw in fareWords) {
                val idx = lower.indexOf(kw)
                if (idx >= 0) {
                    val sub = t.substring(idx)
                    val v = ParsingUtils.moneyValues(listOf(sub)).maxOrNull()
                    if (v != null && v > 0) return v
                }
            }
        }
        return ParsingUtils.moneyValues(texts).maxOrNull()
    }

    private fun parseDistances(items: List<TextItem>): Triple<Double, Double, Double> {
        var pickup = 0.0
        var trip = 0.0
        var total = 0.0
        val unassigned = ArrayList<Double>()
        for (item in items) {
            val v = ParsingUtils.kmValue(item.text) ?: continue
            val l = item.text.lowercase()
            when {
                l.contains("total") && l.contains("distancia") -> total = v
                l.contains("total da viagem") -> total = v
                pickupKmWords.any { l.contains(it) } -> pickup = v
                tripKmWords.any { l.contains(it) } -> trip = v
                else -> unassigned.add(v)
            }
        }
        if (pickup > 0 && trip > 0) return Triple(pickup, trip, total)
        if (total > 0) return Triple(pickup, trip, total)
        val distinct = unassigned.distinct().sorted()
        return when (distinct.size) {
            0 -> Triple(pickup, trip, 0.0)
            1 -> Triple(pickup, trip, distinct[0])
            else -> Triple(distinct.first(), distinct.last(), 0.0)
        }
    }

    private fun parseTimes(items: List<TextItem>): Triple<Double, Double, Double> {
        var pickup = 0.0
        var trip = 0.0
        var total = 0.0
        val unassigned = ArrayList<Double>()
        for (item in items) {
            val v = ParsingUtils.minutesValue(item.text) ?: continue
            val l = item.text.lowercase()
            when {
                l.contains("total") && l.contains("tempo") -> total = v
                l.contains("tempo total") -> total = v
                pickupMinWords.any { l.contains(it) } -> pickup = v
                tripMinWords.any { l.contains(it) } -> trip = v
                else -> unassigned.add(v)
            }
        }
        if (pickup > 0 && trip > 0) return Triple(pickup, trip, total)
        if (total > 0) return Triple(pickup, trip, total)
        val distinct = unassigned.distinct().sorted()
        return when (distinct.size) {
            0 -> Triple(pickup, trip, 0.0)
            1 -> Triple(pickup, trip, distinct[0])
            else -> Triple(distinct.first(), distinct.last(), 0.0)
        }
    }
}
