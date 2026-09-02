package com.example.calculadoraganhos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_leitura -> {
                    replace(LeituraFragment())
                    true
                }
                R.id.nav_desempenho -> {
                    replace(DesempenhoFragment())
                    true
                }
                R.id.nav_calculo -> {
                    replace(CalculoFragment())
                    true
                }
                R.id.nav_historico -> {
                    replace(HistoricoFragment())
                    true
                }
                R.id.nav_ajustes -> {
                    replace(AjustesFragment())
                    true
                }
                else -> false
            }
        }
        replace(LeituraFragment())
        nav.selectedItemId = R.id.nav_leitura
    }

    private fun replace(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, f).commit()
    }
}
