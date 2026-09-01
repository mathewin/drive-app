package com.example.calculadoraganhos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = Prefs(this)

        findViewById<EditText>(R.id.etMetaKm).setText(prefs.minPerKm.toString())
        findViewById<EditText>(R.id.etMetaH).setText(prefs.minPerHour.toString())

        findViewById<Button>(R.id.btnCalcular).setOnClickListener { calcularManual() }
        findViewById<Button>(R.id.btnSalvarMetas).setOnClickListener {
            prefs.minPerKm = parseDouble(findViewById(R.id.etMetaKm), 1.2)
            prefs.minPerHour = parseDouble(findViewById(R.id.etMetaH), 18.0)
            Toast.makeText(this, "Metas salvas", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { pedirSobreposicao() }
        findViewById<Button>(R.id.btnTesteCard).setOnClickListener { testarCard() }
        findViewById<Button>(R.id.btnAcessibilidade).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun calcularManual() {
        val fare = parseDouble(findViewById(R.id.etValor), 0.0)
        val km = parseDouble(findViewById(R.id.etKm), 0.0)
        val min = parseDouble(findViewById(R.id.etMin), 0.0)
        if (fare <= 0 || (km <= 0 && min <= 0)) {
            Toast.makeText(this, "Preencha o valor e km ou tempo", Toast.LENGTH_SHORT).show()
            return
        }
        val r = Calculator.calculate(RideData(fare, km, min), prefs.minPerKm, prefs.minPerHour)
        val tv = findViewById<TextView>(R.id.tvResultado)
        val sb = StringBuilder()
        if (r.perKm > 0) sb.append("R\$/km: ").append(OverlayManager.formatMoney(r.perKm)).append("\n")
        if (r.perHour > 0) sb.append("R\$/h: ").append(OverlayManager.formatMoney(r.perHour)).append("\n")
        sb.append(OverlayManager.verdictLabel(r.verdict))
        tv.text = sb.toString()
        tv.setTextColor(OverlayManager.verdictColor(r.verdict))
    }

    private fun pedirSobreposicao() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Sobreposicao ja permitida", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun testarCard() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primeiro permita a sobreposicao", Toast.LENGTH_LONG).show()
            pedirSobreposicao()
            return
        }
        val d = RideData(42.37, 15.0, 25.0)
        OverlayManager.show(this, null, d, Calculator.calculate(d, prefs.minPerKm, prefs.minPerHour))
    }

    private fun atualizarStatus() {
        findViewById<TextView>(R.id.tvOverlayStatus).text =
            if (Settings.canDrawOverlays(this)) "Sobreposicao: permitida"
            else "Sobreposicao: NAO permitida"

        findViewById<TextView>(R.id.tvA11yStatus).text =
            if (servicoAtivo()) "Leitura automatica: ativa"
            else "Leitura automatica: desativada"
    }

    private fun servicoAtivo(): Boolean {
        val expected = "$packageName/${CalculatorService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun parseDouble(ed: EditText, def: Double): Double =
        ed.text.toString().replace(',', '.').toDoubleOrNull() ?: def
}
