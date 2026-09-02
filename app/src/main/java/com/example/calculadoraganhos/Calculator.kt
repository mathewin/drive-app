package com.example.calculadoraganhos

enum class Level(val label: String) {
    EXCELLENT("EXCELENTE"),
    GOOD("BOA CORRIDA"),
    MEDIUM("MEDIA"),
    BAD("RUIM")
}

data class CalcResult(
    val perKm: Double,
    val perHour: Double,
    val level: Level,
    val score: Int,
    val totalKm: Double,
    val totalMin: Double
)

object Calculator {

    private const val EXCELLENT_MARGIN = 1.5

    fun calculate(data: RideData, minPerKm: Double, minPerHour: Double): CalcResult {
        val totalKm = data.totalDistanceKm
        val totalMin = data.totalTimeMin
        val perKm = if (totalKm > 0) data.fare / totalKm else 0.0
        val perHour = if (totalMin > 0) data.fare / totalMin * 60 else 0.0

        val kmOk = minPerKm <= 0 || perKm >= minPerKm
        val hOk = minPerHour <= 0 || perHour >= minPerHour

        val level = when {
            kmOk && hOk -> {
                val mKm = if (minPerKm > 0) perKm / minPerKm else 1.0
                val mH = if (minPerHour > 0) perHour / minPerHour else 1.0
                if (mKm >= EXCELLENT_MARGIN && mH >= EXCELLENT_MARGIN) Level.EXCELLENT else Level.GOOD
            }
            kmOk || hOk -> Level.MEDIUM
            else -> Level.BAD
        }

        val kmScore = if (minPerKm > 0) (perKm / minPerKm * 100).toInt().coerceIn(0, 100) else 100
        val hScore = if (minPerHour > 0) (perHour / minPerHour * 100).toInt().coerceIn(0, 100) else 100
        val score = ((kmScore + hScore) / 2.0).toInt().coerceIn(0, 100)

        return CalcResult(perKm, perHour, level, score, totalKm, totalMin)
    }
}
