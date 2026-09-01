package com.example.calculadoraganhos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DesempenhoFragment : Fragment() {

    private lateinit var adapter: CorridaAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_desempenho, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvHoje)
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
        val hoje = RideHistory(requireContext()).ridesToday()
        val total = hoje.sumOf { it.fare }
        val totalKm = hoje.sumOf { it.km }
        val totalH = hoje.sumOf { it.minutes } / 60.0
        val perH = if (totalH > 0) total / totalH else 0.0
        val perKm = if (totalKm > 0) total / totalKm else 0.0

        view?.findViewById<TextView>(R.id.tvGanhos)?.text = OverlayManager.formatMoney(total)
        view?.findViewById<TextView>(R.id.tvPerHour)?.text = OverlayManager.formatMoney(perH)
        view?.findViewById<TextView>(R.id.tvPerKm)?.text = OverlayManager.formatMoney(perKm)
        view?.findViewById<TextView>(R.id.tvCount)?.text = "${hoje.size}"
        view?.findViewById<TextView>(R.id.tvDist)?.text =
            String.format(Locale("pt", "BR"), "%.1f km", totalKm)
        adapter.update(hoje)
    }
}
