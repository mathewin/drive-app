package com.example.calculadoraganhos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RideForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            startInForeground()
            DriveWinLog.log("fgs", "servico em primeiro plano ativo (notificacao DriveWin)")
            START_STICKY
        } catch (e: Throwable) {
            android.util.Log.w("DriveWin", "fgs startForeground fail: ${e.message}")
            DriveWinLog.log("fgs", "ERRO ao subir FGS: ${e.message}")
            try {
                stopSelf()
            } catch (_: Exception) {
            }
            START_NOT_STICKY
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "DriveWin", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Monitor de ofertas de corrida"
                }
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DriveWin ativo")
            .setContentText("Monitorando ofertas da Uber e 99")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    companion object {
        const val CHANNEL = "drivewin_monitor"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RideForegroundService::class.java))
            } catch (e: Exception) {
                try {
                    context.startService(Intent(context, RideForegroundService::class.java))
                } catch (e2: Exception) {
                    android.util.Log.w("DriveWin", "fgs start fail: ${e2.message}")
                }
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RideForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
