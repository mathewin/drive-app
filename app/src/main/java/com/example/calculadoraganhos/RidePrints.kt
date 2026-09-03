package com.example.calculadoraganhos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RidePrints {

    const val CHANNEL_CARDS = "drivewin_cards"
    const val NOTIF_CARD_ID = 1002

    private val COLOR_VERDE = 0xFF31F900.toInt()
    private val COLOR_ROSA = 0xFFC864AF.toInt()
    private val COLOR_AMBAR = 0xFFF5A623.toInt()
    private val COLOR_VERMELHO = 0xFFFF4D4D.toInt()
    private val COLOR_BADGE_TEXT = 0xFF0E0F12.toInt()

    fun saveToGallery(context: Context, bmp: Bitmap, tag: String): Boolean {
        return try {
            val safe = tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val name = "drivewin_${stamp()}_$safe.jpg"
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DriveWin")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                val wrote = resolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                } ?: false
                if (!wrote) {
                    resolver.delete(uri, null, null)
                    return false
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "DriveWin"
                )
                if (!dir.exists() && !dir.mkdirs()) return false
                val f = File(dir, name)
                FileOutputStreamSafe(f, bmp)
                MediaScannerConnection.scanFile(
                    context, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null
                )
                true
            }
        } catch (e: Exception) {
            DriveWinLog.log("print", "erro ao salvar na galeria: ${e.message}")
            false
        }
    }

    private fun FileOutputStreamSafe(f: File, bmp: Bitmap) {
        java.io.FileOutputStream(f).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }

    fun postCardNotification(
        context: Context,
        app: String,
        data: RideData,
        res: CalcResult,
        passenger: String?,
        pickup: String?,
        dropoff: String?
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_CARDS, "Corrida salva",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Card da ultima corrida lida (sem som)"
                        setSound(null, null)
                    }
                )
            }
            val pkg = context.packageName

            val small = android.widget.RemoteViews(pkg, R.layout.notif_ride_small)
            small.setTextViewText(R.id.tv_small_drivewin, "DRIVEWIN")
            small.setTextViewText(R.id.tv_small_app, app)
            small.setTextViewText(
                R.id.tv_small_summary,
                "${ParsingUtils.formatMoney(data.fare)} · " +
                    "${ParsingUtils.formatMin(data.totalTimeMin)} · " +
                    "${ParsingUtils.formatKm(data.totalDistanceKm)}"
            )
            small.setTextViewText(
                R.id.tv_small_meta,
                "${ParsingUtils.formatMoney(res.perKm)}/km · " +
                    "${ParsingUtils.formatMoney(res.perHour)}/h"
            )

            val big = android.widget.RemoteViews(pkg, R.layout.notif_ride_big)
            big.setTextViewText(R.id.tv_big_drivewin, "DRIVEWIN")
            big.setTextViewText(R.id.tv_big_badge, res.level.label)
            big.setInt(R.id.tv_big_badge, "setBackgroundColor", levelColor(res.level))
            big.setTextViewText(
                R.id.tv_big_fare,
                "${ParsingUtils.formatMoney(data.fare)} · " +
                    "${ParsingUtils.formatMin(data.totalTimeMin)} · " +
                    "${ParsingUtils.formatKm(data.totalDistanceKm)}"
            )
            big.setTextViewText(
                R.id.tv_big_meta,
                "${ParsingUtils.formatMoney(res.perKm)}/km · " +
                    "${ParsingUtils.formatMoney(res.perHour)}/h"
            )
            big.setTextViewText(
                R.id.tv_big_nota,
                if (passenger != null) "PASSAGEIRO  $passenger" else "NOTA ${res.score}/100"
            )

            val hasPickup = pickup != null && pickup.isNotBlank()
            val hasDrop = dropoff != null && dropoff.isNotBlank()
            if (hasPickup) {
                big.setTextViewText(R.id.tv_big_pickup, pickup)
                big.setOnClickPendingIntent(R.id.row_pickup, mapsPi(context, pickup!!))
            } else {
                big.setViewVisibility(R.id.row_pickup, android.view.View.GONE)
            }
            if (hasDrop) {
                big.setTextViewText(R.id.tv_big_dropoff, dropoff)
                big.setOnClickPendingIntent(R.id.row_dropoff, mapsPi(context, dropoff!!))
            } else {
                big.setViewVisibility(R.id.row_dropoff, android.view.View.GONE)
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_CARDS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("DriveWin")
                .setContentText("Corrida ${app} salva")
                .setCustomContentView(small)
                .setCustomBigContentView(big)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(false)
                .setAutoCancel(false)
                .build()

            try {
                nm.notify(NOTIF_CARD_ID, notif)
                DriveWinLog.log("print", "card da corrida atualizado na barra ($app)")
            } catch (e: SecurityException) {
                DriveWinLog.log("print", "sem permissao de notificacao para o card")
            }
        } catch (e: Exception) {
            DriveWinLog.log("print", "erro ao montar card de notificacao: ${e.message}")
        }
    }

    private fun mapsPi(context: Context, addr: String): PendingIntent? {
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(addr))
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getActivity(context, addr.hashCode() and 0x7fffffff, intent, flags)
        } catch (e: Exception) {
            DriveWinLog.log("print", "erro ao criar intent do mapa: ${e.message}")
            null
        }
    }

    private fun levelColor(level: Level): Int {
        return when (level) {
            Level.EXCELLENT, Level.GOOD -> COLOR_VERDE
            Level.MEDIUM -> COLOR_AMBAR
            Level.BAD -> COLOR_VERMELHO
        }
    }

    private fun stamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }
}
