package com.example.calculadoraganhos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeituraFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_leitura, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnA11yLeitura).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        view.findViewById<Button>(R.id.btnOverlayLeitura).setOnClickListener {
            if (Settings.canDrawOverlays(requireContext())) {
                Toast.makeText(requireContext(), "Sobreposicao ja permitida", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                )
            }
        }
        view.findViewById<Button>(R.id.btnIniciarLeitura).setOnClickListener {
            val a11y = servicoAtivo()
            val ov = Settings.canDrawOverlays(requireContext())
            when {
                !a11y && !ov -> Toast.makeText(requireContext(), "Ative acessibilidade e sobreposicao acima", Toast.LENGTH_LONG).show()
                !a11y -> Toast.makeText(requireContext(), "Ative a acessibilidade acima", Toast.LENGTH_LONG).show()
                !ov -> Toast.makeText(requireContext(), "Permita a sobreposicao acima", Toast.LENGTH_LONG).show()
                else -> Toast.makeText(requireContext(), "Leitura ativa - abra a Uber/99", Toast.LENGTH_LONG).show()
            }
        }
        atualizar()
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        val ctx = requireContext()
        val a11y = servicoAtivo()
        val ov = Settings.canDrawOverlays(ctx)

        view?.findViewById<TextView>(R.id.tvStatus)?.text =
            if (a11y && ov) "AGUARDANDO CORRIDA" else "CONFIGURE AS PERMISSOES"
        view?.findViewById<TextView>(R.id.tvA11yLeitura)?.text = if (a11y) "Ativa" else "Desativada"
        view?.findViewById<TextView>(R.id.tvOverlayLeitura)?.text = if (ov) "Permitida" else "Nao permitida"
        view?.findViewById<Button>(R.id.btnA11yLeitura)?.text = if (a11y) "OK" else "Conceder"
        view?.findViewById<Button>(R.id.btnOverlayLeitura)?.text = if (ov) "OK" else "Conceder"

        val last = RideHistory(ctx).rides().firstOrNull()
        val lastApp = view?.findViewById<TextView>(R.id.tvLastApp)
        val lastFare = view?.findViewById<TextView>(R.id.tvLastFare)
        val lastStats = view?.findViewById<TextView>(R.id.tvLastStats)
        if (last == null) {
            lastApp?.text = "Nenhuma corrida detectada ainda"
            lastFare?.text = ""
            lastStats?.text = "Abra a Uber/99 e aguarde uma oferta"
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last.ts))
            lastApp?.text = "${last.app} - $time"
            lastFare?.text = OverlayManager.formatMoney(last.fare)
            lastStats?.text = buildString {
                append(OverlayManager.formatMoney(last.perHour)).append("/h")
                if (last.km > 0) append(" - ").append(OverlayManager.formatMoney(last.perKm)).append("/km")
                if (last.km > 0) append(" - ").append(OverlayManager.formatKm(last.km))
            }
        }
    }

    private fun servicoAtivo(): Boolean {
        val expected = "${requireContext().packageName}/${CalculatorService::class.java.name}"
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
