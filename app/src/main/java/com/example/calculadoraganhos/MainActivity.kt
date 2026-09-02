package com.example.calculadoraganhos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.calculadoraganhos.ui.DriveWinApp
import com.example.calculadoraganhos.ui.DriveWinTheme

class MainActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(result.resultCode, result.data!!)
            OcrFallback.setProjection(projection)
            Toast.makeText(this, "Captura de tela autorizada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Captura nao autorizada", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) RideForegroundService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveWinTheme {
                DriveWinApp(
                    openA11y = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    openOverlay = {
                        if (Settings.canDrawOverlays(this)) {
                            Toast.makeText(this, "Sobreposicao ja permitida", Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    },
                    openBattery = {
                        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
                        if (pm.isIgnoringBatteryOptimizations(packageName)) {
                            Toast.makeText(this, "Bateria ja isenta", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            } catch (e: Exception) {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    },
                    startMonitor = { startMonitor() },
                    requestCapture = { requestCapture() }
                )
            }
        }
    }

    override fun onDestroy() {
        OcrFallback.setProjection(null)
        super.onDestroy()
    }

    private fun startMonitor() {
        val a11y = settingsServiceActive()
        val overlay = Settings.canDrawOverlays(this)
        when {
            !a11y && !overlay -> toast("Ative a acessibilidade e a sobreposicao")
            !a11y -> toast("Ative a acessibilidade")
            !overlay -> toast("Permita a sobreposicao")
            else -> {
                toast("Monitoramento ativo - abra a Uber ou a 99")
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    RideForegroundService.start(this)
                }
            }
        }
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            captureLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (e: Exception) {
            toast("Nao foi possivel solicitar a captura de tela")
        }
    }

    private fun settingsServiceActive(): Boolean {
        val expected = "$packageName/${CalculatorService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
