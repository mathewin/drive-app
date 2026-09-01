package com.example.calculadoraganhos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoricoFragment : Fragment() {

    private lateinit var adapter: CorridaAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_historico, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvHistorico)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = CorridaAdapter()
        rv.adapter = adapter
        atualizar()
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        val todas = RideHistory(requireContext()).rides()
        val total = todas.sumOf { it.fare }
        view?.findViewById<TextView>(R.id.tvTotalCorridas)?.text = "${todas.size} corridas"
        view?.findViewById<TextView>(R.id.tvTotalGanhos)?.text = OverlayManager.formatMoney(total)
        adapter.update(todas)
    }
}
