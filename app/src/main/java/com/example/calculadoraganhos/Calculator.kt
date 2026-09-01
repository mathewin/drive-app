package com.example.calculadoraganhos

data class RideData(val fare: Double, val km: Double, val minutes: Double)

data class CalcResult(val perKm: Double, val perHour: Double, val verdict: Int) {
    companion object {
        const val GREEN = 0
        const val AMBER = 1
        const val RED = 2
    }
}

object Calculator {
    fun calculate(d: RideData, minPerKm: Double, minPerHour: Double): CalcResult {
        val perKm = if (d.km > 0) d.fare / d.km else 0.0
        val perHour = if (d.minutes > 0) d.fare / (d.minutes / 60.0) else 0.0
        val kmOk = minPerKm <= 0 || perKm >= minPerKm
        val hOk = minPerHour <= 0 || perHour >= minPerHour
        val verdict = when {
            kmOk && hOk -> CalcResult.GREEN
            !kmOk && !hOk -> CalcResult.RED
            else -> CalcResult.AMBER
        }
        return CalcResult(perKm, perHour, verdict)
    }
}
