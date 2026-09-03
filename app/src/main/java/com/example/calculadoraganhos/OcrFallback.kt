package com.example.calculadoraganhos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrFallback {

    private const val TAG = "DriveWin"
    private const val COOLDOWN_MS = 8000L
    private const val MAX_OCR_DIMENSION = 1080

    private var mediaProjection: MediaProjection? = null
    private var lastAttemptMs = 0L
    private var lastHintMs = 0L
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private fun hint(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastHintMs < 15000) return
        lastHintMs = now
        DriveWinLog.log("ocr", msg)
    }

    fun setProjection(projection: MediaProjection?) {
        mediaProjection?.stop()
        mediaProjection = projection
        if (projection == null) {
            handler = null
            thread?.quitSafely()
            thread = null
        }
    }

    fun available(): Boolean = mediaProjection != null

    fun cropBottomRegion(bitmap: Bitmap): Bitmap {
        val top = (bitmap.height * BOTTOM_REGION_FRACTION).toInt()
        if (top <= 0) return bitmap
        val crop = Bitmap.createBitmap(bitmap.width, bitmap.height - top, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(crop)
        canvas.drawBitmap(bitmap, 0f, -top.toFloat(), null)
        return crop
    }

    fun runOcrOnBitmap(
        bitmap: Bitmap,
        parser: (List<TextItem>) -> ParsedCard?,
        onResult: (ParsedCard?, List<TextItem>) -> Unit
    ) {
        try {
            val source = downscaleIfNeeded(bitmap)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(source, 0))
                .addOnSuccessListener { result ->
                    val items = result.textBlocks
                        .flatMap { block -> block.lines }
                        .mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            TextItem(
                                line.text,
                                Rect(box.left, box.top, box.right, box.bottom)
                            )
                        }
                    val card = try {
                        parser(items)
                    } catch (e: Exception) {
                        Log.w(TAG, "parser fail: ${e.message}")
                        null
                    }
                    if (source !== bitmap) source.recycle()
                    onResult(card, items)
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ocr fail: ${e.message}")
                    if (source !== bitmap) source.recycle()
                    recognizer.close()
                    onResult(null, emptyList())
                }
        } catch (e: Exception) {
            Log.w(TAG, "ocr bitmap fail: ${e.message}")
            onResult(null, emptyList())
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val longest = maxOf(w, h)
            if (longest <= MAX_OCR_DIMENSION) return bitmap
            val scale = MAX_OCR_DIMENSION.toFloat() / longest
            val nw = (w * scale).toInt().coerceAtLeast(320)
            val nh = (h * scale).toInt().coerceAtLeast(320)
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    fun tryCaptureAndParse(
        context: Context,
        parser: (List<TextItem>) -> ParsedCard?,
        onResult: (ParsedCard?, List<TextItem>) -> Unit
    ): Boolean {
        val projection = mediaProjection
        if (projection == null) {
            hint("captura manual NAO autorizada (use so em aparelhos antigos)")
            return false
        }
        if (!Prefs(context).ocrEnabled) return false
        val now = System.currentTimeMillis()
        if (now - lastAttemptMs < COOLDOWN_MS) return false
        lastAttemptMs = now

        return try {
            if (thread == null) {
                thread = HandlerThread("DriveWinOCR").also { it.start() }
                handler = Handler(thread!!.looper)
            }
            val h = handler ?: return false
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val b = wm.currentWindowMetrics.bounds
            val width = b.width()
            val height = b.height()
            val dpi = context.resources.displayMetrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            var vd: VirtualDisplay? = null
            var cleaned = false

            fun cleanup() {
                if (cleaned) return
                cleaned = true
                try {
                    vd?.release()
                } catch (_: Exception) {
                }
                try {
                    reader.close()
                } catch (_: Exception) {
                }
            }

            vd = projection.createVirtualDisplay(
                "DriveWinOCR", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, h
            )

            h.postDelayed({
                val image = try {
                    reader.acquireLatestImage()
                } catch (_: Exception) {
                    null
                }
                if (image == null) {
                    cleanup()
                    onResult(null, emptyList())
                    return@postDelayed
                }
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowPadding = plane.rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(plane.buffer)
                image.close()
                val finalBmp = if (rowPadding > 0) {
                    Bitmap.createBitmap(bitmap, 0, 0, width, height)
                } else {
                    bitmap
                }
                val regionBmp = cropBottomRegion(finalBmp)
                runOcrOnBitmap(regionBmp, parser) { card, items ->
                    if (card != null) {
                        Log.d(TAG, "ocr ok fare=${card.data.fare} km=${card.data.totalDistanceKm} min=${card.data.totalTimeMin}")
                    }
                    if (regionBmp !== finalBmp) finalBmp.recycle()
                    regionBmp.recycle()
                    cleanup()
                    onResult(card?.copy(confidence = 0.6), items)
                }
            }, 150)
            true
        } catch (e: Exception) {
            Log.w(TAG, "ocr capture fail: ${e.message}")
            try {
                if (e is SecurityException) mediaProjection?.stop()
            } catch (_: Exception) {
            }
            false
        }
    }
}
