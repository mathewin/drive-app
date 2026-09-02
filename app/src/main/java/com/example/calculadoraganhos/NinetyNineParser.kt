package com.example.calculadoraganhos

object NinetyNineParser : CardParserBase() {

    override val fareWords = listOf(
        "aceitar por", "aceite por", "aceitar", "aceite",
        "pegar", "reservar", "selecionar", "escolher"
    )

    override val pickupKmWords = listOf(
        "ate passageiro", "até passageiro", "ate o passageiro", "até o passageiro",
        "origem", "buscar", "busca", "pickup", "pegar", "a caminho", "ate voce", "ate você"
    )

    override val tripKmWords = listOf(
        "viagem", "destino", "chegada", "final", "ao destino", "ate o destino", "até o destino"
    )

    override val pickupMinWords = listOf(
        "ate passageiro", "até passageiro", "ate o passageiro", "até o passageiro",
        "origem", "buscar", "pickup", "ate voce", "ate você", "a caminho", "chegada em"
    )

    override val tripMinWords = listOf(
        "viagem", "destino", "final", "ate o destino", "até o destino"
    )
}
