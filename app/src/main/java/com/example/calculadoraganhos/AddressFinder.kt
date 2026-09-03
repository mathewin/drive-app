package com.example.calculadoraganhos

import java.util.Locale

data class RideAddresses(val pickup: String? = null, val dropoff: String? = null)

object AddressFinder {

    private val STREET = listOf(
        "avenida", "av. ", "alameda", "rodovia", "estrada", "travessa",
        "praça", "praca", "vila", "jardim", "bairro", "parque", "setor",
        "loteamento", "quadra", "conjunto", "centro", "residencial",
        "condominio", "recanto", "rua", "largo", "colina", "morro"
    )

    private val STOP = listOf(
        "aceitar", "aceite", "aceito", "recusar", "recuse", "rejeitar", "descartar", "decline",
        "nova corrida", "novo pedido", "nova oferta", "novo destino", "nova solicitacao",
        "solicitacao", "solicitação", "radar", "toque", "selecionar", "escolher", "reservar",
        "chegou uma corrida", "disponivel para voce", "detalhes da viagem", "detalhes",
        "ganhos", "promocoes", "perfil", "procurando", "aguardando", "conectando",
        "avaliacao", "avaliacoes", "avaliação", "avaliações", "corridas", "viagens", "trips",
        "cancelar", "voltar", "fechar", "confirmar", "uberx", "ubergo", "uber x", "uber go",
        "uberconfort", "uber confort", "uberblack", "confort", "uber bag", "uber flash",
        "mais conforto", "viagem", "tempo estimado", "distancia estimada", "nota", "modo",
        "rota", "localizar", "minha localizacao", "destino", "dest.", "embarque", "desembarque",
        "coleta", "chegada", "chegando", "a caminho", "passageiro", "motorista", "pagamento",
        "dinheiro", "cartao", "pix", "finalizar", "iniciar", "agora", "em breve", "ate",
        "preferencias", "fluxo", "recente", "salvo", "favorito", "trabalho", "casa"
    )

    fun extract(items: List<TextItem>): RideAddresses {
        if (items.isEmpty()) return RideAddresses()
        val sorted = items.sortedBy { it.bounds.top }
        val picks = ArrayList<TextItem>(2)
        for (it in sorted) {
            if (picks.size >= 2) break
            val t = it.text.trim()
            if (candidate(t)) picks.add(it)
        }
        if (picks.isEmpty()) return RideAddresses()
        val first = picks[0].text.trim()
        val second = picks.getOrNull(1)?.text?.trim()
        return RideAddresses(pickup = first, dropoff = second)
    }

    private fun candidate(t: String): Boolean {
        val s = t.trim()
        if (s.length < 6 || s.length > 110) return false
        if (!s.any { it.isLetter() }) return false
        val l = s.lowercase(Locale.ROOT)
        if (l.contains('$')) return false
        if (l.contains('/')) return false
        if (l.contains("r$")) return false
        if (ParsingUtils.kmValue(s) != null) return false
        if (ParsingUtils.minutesValue(s) != null) return false
        if (ParsingUtils.moneyValues(listOf(s)).isNotEmpty()) return false
        if (ParsingUtils.passengerRating(listOf(s)) != null) return false
        for (w in STOP) if (l.contains(w)) return false
        return s.contains(',') || STREET.any { l.contains(it) }
    }
}
