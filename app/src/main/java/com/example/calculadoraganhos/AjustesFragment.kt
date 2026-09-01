package com.example.calculadoraganhos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class AjustesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_ajustes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())
        val etKm = view.findViewById<EditText>(R.id.etMetaKmAj)
        val etH = view.findViewById<EditText>(R.id.etMetaHAj)
        etKm.setText(prefs.minPerKm.toString())
        etH.setText(prefs.minPerHour.toString())

        view.findViewById<Button>(R.id.btnSalvarAj).setOnClickListener {
            prefs.minPerKm = parseDouble(etKm, 1.2)
            prefs.minPerHour = parseDouble(etH, 18.0)
            Toast.makeText(requireContext(), "Metas salvas", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnLimparHist).setOnClickListener {
            RideHistory(requireContext()).clear()
            Toast.makeText(requireContext(), "Historico apagado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseDouble(ed: EditText, def: Double): Double =
        ed.text.toString().replace(',', '.').toDoubleOrNull() ?: def
}
