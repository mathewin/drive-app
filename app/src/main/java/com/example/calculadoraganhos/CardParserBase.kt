package com.example.calculadoraganhos

abstract class CardParserBase {

    abstract val fareWords: List<String>
    abstract val pickupKmWords: List<String>
    abstract val tripKmWords: List<String>
    abstract val pickupMinWords: List<String>
    abstract val tripMinWords: List<String>

    fun parse(items: List<TextItem>): ParsedCard? {
        val offer = ParsingUtils.offerItems(items)
        if (offer.isEmpty()) return null
        val texts = offer.map { it.text }.filter { it.isNotBlank() }
        val fare = parseFare(offer) ?: return null
        val legs = ParsingUtils.extractLegs(offer)
        val (pickupKm, tripKm, totalKm) = parseDistances(offer, legs)
        val (pickupMin, tripMin, totalMin) = parseTimes(offer, legs)
        val data = RideData(fare, pickupKm, tripKm, totalKm, pickupMin, tripMin, totalMin)
        if (data.totalDistanceKm <= 0 && data.totalTimeMin <= 0) return null
        val addr = AddressFinder.extract(offer)
        val check = Validator.confirmPerKm(data, ParsingUtils.statedPerKm(items.map { it.text }))
        return ParsedCard(
            data,
            confidence = if (check.confirmed) 1.0 else if (check.mismatch) 0.75 else 1.0,
            suspicious = Validator.suspicious(data) || check.mismatch,
            confirmed = check.confirmed,
            passenger = ParsingUtils.passengerRating(texts),
            pickup = addr.pickup,
            dropoff = addr.dropoff
        )
    }

    private fun parseFare(items: List<TextItem>): Double? {
        val texts = items.map { it.text }
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
        val nearLegs = fareNearLegs(items)
        if (nearLegs != null) return nearLegs
        return ParsingUtils.moneyValues(texts).maxOrNull()
    }

    private fun fareNearLegs(items: List<TextItem>): Double? {
        val legItems = items.filter { ParsingUtils.offerLeg(it.text) != null }
        if (legItems.isEmpty()) return null
        val y0 = legItems.minOf { it.bounds.top }
        val y1 = legItems.maxOf { it.bounds.bottom }
        val pad = ((y1 - y0).coerceAtLeast(80) * 3).coerceAtLeast(120)
        val near = items.filter {
            it.bounds.centerY() in (y0 - pad)..(y1 + pad / 2)
        }
        return ParsingUtils.moneyValues(near.map { it.text }).maxOrNull()
    }

    private fun parseDistances(
        items: List<TextItem>,
        legs: List<Pair<Double, Double>>
    ): Triple<Double, Double, Double> {
        if (legs.size >= 2) {
            return Triple(legs.first().second, legs.last().second, 0.0)
        }
        if (legs.size == 1) {
            return Triple(0.0, 0.0, legs[0].second)
        }
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

    private fun parseTimes(
        items: List<TextItem>,
        legs: List<Pair<Double, Double>>
    ): Triple<Double, Double, Double> {
        if (legs.size >= 2) {
            return Triple(legs.first().first, legs.last().first, 0.0)
        }
        if (legs.size == 1) {
            return Triple(0.0, 0.0, legs[0].first)
        }
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
