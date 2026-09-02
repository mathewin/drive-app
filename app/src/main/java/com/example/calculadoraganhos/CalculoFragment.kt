package com.example.calculadoraganhos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment

class CalculoFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_calculo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())

        val sbPerKm = view.findViewById<SeekBar>(R.id.sbPerKm)
        val sbPerH = view.findViewById<SeekBar>(R.id.sbPerH)
        val sbOpac = view.findViewById<SeekBar>(R.id.sbOpac)
        val sbFont = view.findViewById<SeekBar>(R.id.sbFont)
        val sbSecs = view.findViewById<SeekBar>(R.id.sbSecs)
        val tvPerKm = view.findViewById<TextView>(R.id.tvValPerKm)
        val tvPerH = view.findViewById<TextView>(R.id.tvValPerH)
        val tvOpac = view.findViewById<TextView>(R.id.tvValOpac)
        val tvFont = view.findViewById<TextView>(R.id.tvValFont)
        val tvSecs = view.findViewById<TextView>(R.id.tvValSecs)
        val spinner = view.findViewById<Spinner>(R.id.spPosicao)
        val cbPerKm = view.findViewById<CheckBox>(R.id.cbPerKm)
        val cbPerH = view.findViewById<CheckBox>(R.id.cbPerH)

        sbPerKm.max = 100
        sbPerH.max = 100
        sbOpac.max = 70
        sbFont.max = 14
        sbSecs.max = 12

        val posicoes = arrayOf("Topo", "Meio", "Baixo")
        spinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, posicoes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        sbPerKm.progress = (prefs.minPerKm / 5.0 * 100).toInt()
        sbPerH.progress = (prefs.minPerHour / 100.0 * 100).toInt()
        sbOpac.progress = (prefs.overlayOpacity * 100).toInt() - 30
        sbFont.progress = prefs.overlayFontSize.toInt() - 10
        sbSecs.progress = prefs.overlayShowSeconds - 3
        spinner.setSelection(if (prefs.overlayPosition == "baixo") 2 else if (prefs.overlayPosition == "meio") 1 else 0)
        cbPerKm.isChecked = prefs.showPerKm
        cbPerH.isChecked = prefs.showPerHour

        fun refresh() {
            tvPerKm.text = "${OverlayManager.formatMoney(prefs.minPerKm)}/km"
            tvPerH.text = "${OverlayManager.formatMoney(prefs.minPerHour)}/h"
            tvOpac.text = "${(prefs.overlayOpacity * 100).toInt()}%"
            tvFont.text = "${prefs.overlayFontSize.toInt()}"
            tvSecs.text = "${prefs.overlayShowSeconds} segundos"
        }
        refresh()

        sbPerKm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.minPerKm = progress / 100.0 * 5.0
                tvPerKm.text = "${OverlayManager.formatMoney(prefs.minPerKm)}/km"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        sbPerH.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.minPerHour = progress / 100.0 * 100.0
                tvPerH.text = "${OverlayManager.formatMoney(prefs.minPerHour)}/h"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        sbOpac.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.overlayOpacity = (progress + 30) / 100f
                tvOpac.text = "${(prefs.overlayOpacity * 100).toInt()}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        sbFont.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.overlayFontSize = (progress + 10).toFloat()
                tvFont.text = "${prefs.overlayFontSize.toInt()}"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        sbSecs.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.overlayShowSeconds = progress + 3
                tvSecs.text = "${prefs.overlayShowSeconds} segundos"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                prefs.overlayPosition = when (position) {
                    2 -> "baixo"
                    1 -> "meio"
                    else -> "topo"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        cbPerKm.setOnCheckedChangeListener { _, b -> prefs.showPerKm = b }
        cbPerH.setOnCheckedChangeListener { _, b -> prefs.showPerHour = b }

        view.findViewById<Button>(R.id.btnTestarCardCalc).setOnClickListener {
            val d = RideData(42.37, 15.0, 25.0)
            val r = Calculator.calculate(d, prefs.minPerKm, prefs.minPerHour)
            OverlayManager.show(requireContext(), null, null, d, r)
        }
    }
}
