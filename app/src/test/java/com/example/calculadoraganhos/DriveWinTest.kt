package com.example.calculadoraganhos

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveWinTest {

    private fun item(text: String) = TextItem(text, Rect(0, 0, 100, 20))

    @Test
    fun specExample() {
        val data = RideData(
            fare = 28.0,
            pickupKm = 2.0,
            tripKm = 8.0,
            totalMin = 30.0
        )
        assertEquals(10.0, data.totalDistanceKm, 0.001)
        val r = Calculator.calculate(data, 2.0, 40.0)
        assertEquals(2.8, r.perKm, 0.001)
        assertEquals(56.0, r.perHour, 0.001)
        assertEquals(Level.GOOD, r.level)
        assertTrue(r.score in 0..100)
    }

    @Test
    fun parsingMoney() {
        assertEquals(20.0, ParsingUtils.moneyValues(listOf("R$ 20,00")).first(), 0.001)
        assertEquals(1250.5, ParsingUtils.moneyValues(listOf("R$ 1.250,50")).first(), 0.001)
        assertEquals(20.0, ParsingUtils.moneyValues(listOf("R$20,00")).first(), 0.001)
        assertTrue(ParsingUtils.moneyValues(listOf("R$ 2,30/km")).isEmpty())
    }

    @Test
    fun parsingDistanceTime() {
        assertEquals(5.1, ParsingUtils.kmValue("5.1 km")!!, 0.001)
        assertEquals(0.8, ParsingUtils.kmValue("800 m")!!, 0.001)
        assertEquals(80.0, ParsingUtils.minutesValue("1h20")!!, 0.001)
        assertEquals(38.0, ParsingUtils.minutesValue("0h38m")!!, 0.001)
        assertEquals(13.0, ParsingUtils.minutesValue("13 min")!!, 0.001)
        assertEquals(18.0, ParsingUtils.minutesValue("18 minutos")!!, 0.001)
    }

    @Test
    fun uberParserCard() {
        val card = UberParser.parse(
            listOf(
                item("UberX"),
                item("R\$ 19,59 · 8.5 km · 18 min"),
                item("Aceitar por R\$ 19,59")
            )
        )
        assertNotNull(card)
        assertEquals(19.59, card!!.data.fare, 0.001)
        assertEquals(8.5, card.data.totalDistanceKm, 0.001)
        assertEquals(18.0, card.data.totalTimeMin, 0.001)
        assertTrue(Validator.isValid(card.data))
    }

    @Test
    fun ninetyNineRadarCard() {
        val card = NinetyNineParser.parse(
            listOf(
                item("Radar de Viagens"),
                item("R\$ 11,01 · 5.1 km · 13 min"),
                item("Aceitar")
            )
        )
        assertNotNull(card)
        assertEquals(11.01, card!!.data.fare, 0.001)
        assertEquals(5.1, card.data.totalDistanceKm, 0.001)
        assertEquals(13.0, card.data.totalTimeMin, 0.001)
    }

    @Test
    fun pickupPlusTripIsOperationalTotal() {
        val card = UberParser.parse(
            listOf(
                item("2 km ate o passageiro"),
                item("8 km de viagem"),
                item("30 min"),
                item("R\$ 28,00"),
                item("Aceitar")
            )
        )
        assertNotNull(card)
        assertEquals(10.0, card!!.data.totalDistanceKm, 0.001)
        assertEquals(2.0, card.data.pickupKm, 0.001)
        assertEquals(8.0, card.data.tripKm, 0.001)
    }

    @Test
    fun passengerRatingCombinedToken() {
        assertEquals(
            "5,00",
            ParsingUtils.passengerRating(listOf("5,00(32)", "Verificado"))
        )
        assertEquals(
            "4,98",
            ParsingUtils.passengerRating(listOf("4.98(1254)", "Verificado"))
        )
    }

    @Test
    fun passengerRatingSplitTokens() {
        assertEquals(
            "4,9",
            ParsingUtils.passengerRating(listOf("R\$ 15,09", "4,9", "(237)", "Verificado"))
        )
    }

    @Test
    fun passengerRatingRejectsPlainNumbers() {
        assertNull(ParsingUtils.passengerRating(listOf("R\$ 15,09", "4,9", "6.3 km")))
        assertNull(ParsingUtils.passengerRating(listOf("15,09", "3,50", "1,93/km")))
    }
}
