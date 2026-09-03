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

    private var captureAfterNotif = false

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(result.resultCode, result.data!!)
            OcrFallback.setProjection(projection)
            Prefs(this).ocrEnabled = true
            DriveWinLog.log("app", "captura de tela autorizada - OCR ativo")
            Toast.makeText(this, "Captura autorizada (TELA INTEIRA). OCR ativo.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Captura nao autorizada - OCR desativado", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        DriveWinLog.log("app", if (granted) "notificacao autorizada" else "notificacao negada")
        launchMonitorAfterNotif(captureAfterNotif)
        captureAfterNotif = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashReport.save(this, throwable)
            } catch (_: Exception) {
            }
            prevHandler?.uncaughtException(thread, throwable)
        }
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
                    startMonitor = { toggleMonitor(true) },
                    stopMonitor = { toggleMonitor(false) },
                    testOverlay = { testOverlay() },
                    requestCapture = { requestCapture() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun toggleMonitor(on: Boolean) {
        if (on) {
            val a11y = settingsServiceActive()
            val overlay = Settings.canDrawOverlays(this)
            if (!a11y) {
                toast("Abra a Acessibilidade e ligue o DriveWin")
                DriveWinLog.log("app", "LIGAR: acessibilidade desativada")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }
            if (!overlay) {
                toast("Permita a sobreposicao do DriveWin")
                DriveWinLog.log("app", "LIGAR: sobreposicao nao permitida")
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
            Prefs(this).monitorOn = true
            DriveWinLog.log("app", "LIGADO - leitura ativa")
            launchMonitor(!OcrFallback.available())
            toast("Monitoramento LIGADO - abra a Uber ou a 99")
        } else {
            Prefs(this).monitorOn = false
            DriveWinLog.log("app", "DESLIGADO")
            OcrFallback.setProjection(null)
            RideForegroundService.stop(this)
            OverlayManager.hide()
            toast("Monitoramento DESLIGADO")
        }
    }

    private fun launchMonitor(capture: Boolean) {
        val needNotif = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
        if (needNotif) {
            captureAfterNotif = capture
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchMonitorAfterNotif(capture)
        }
    }

    private fun launchMonitorAfterNotif(capture: Boolean) {
        RideForegroundService.start(this)
        if (capture) {
            DriveWinLog.log("app", "LIGAR: pedindo autorizacao de captura de tela")
            toast("Confirme a captura e escolha TELA INTEIRA")
            requestCapture()
        }
    }

    private fun testOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Permita a sobreposicao primeiro")
            return
        }
        val prefs = Prefs(this)
        val data = RideData(fare = 25.0, totalKm = 8.0, totalMin = 20.0)
        val res = Calculator.calculate(data, prefs.minPerKm, prefs.minPerHour)
        DriveWinLog.log("app", "TESTE do card solicitado")
        OverlayManager.show(
            this,
            OverlayManager.OverlayContent(data, res, "TESTE", false, 1.0),
            beep = prefs.overlayAlert
        )
        toast("Card de teste exibido por ${prefs.overlayShowSeconds}s")
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
