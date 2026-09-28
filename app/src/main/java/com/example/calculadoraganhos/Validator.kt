package com.example.calculadoraganhos

object Validator {

    data class PerKmCheck(val confirmed: Boolean, val mismatch: Boolean)

    fun isValid(data: RideData): Boolean =
        data.fare > 0 && data.totalDistanceKm > 0 && data.totalTimeMin > 0

    fun confirmPerKm(data: RideData, stated: Double?): PerKmCheck {
        if (stated == null || stated <= 0) return PerKmCheck(confirmed = false, mismatch = false)
        val km = data.totalDistanceKm
        if (km <= 0 || data.fare <= 0) return PerKmCheck(confirmed = false, mismatch = false)
        val calc = data.fare / km
        val gap = kotlin.math.abs(calc - stated) / kotlin.math.max(calc, stated)
        return if (gap <= 0.12) {
            PerKmCheck(confirmed = true, mismatch = false)
        } else {
            PerKmCheck(confirmed = false, mismatch = true)
        }
    }

    fun suspicious(data: RideData): Boolean {
        if (data.fare <= 0) return true
        val km = data.totalDistanceKm
        val min = data.totalTimeMin
        if (km <= 0 || min <= 0) return true
        val perKm = data.fare / km
        if (perKm < 0.4 || perKm > 60) return true
        val perHour = data.fare / min * 60
        if (perHour < 3 || perHour > 400) return true
        val kmh = km / min * 60
        if (kmh < 3 || kmh > 150) return true
        return false
    }
}
