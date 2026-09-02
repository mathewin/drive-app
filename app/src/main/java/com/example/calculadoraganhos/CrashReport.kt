package com.example.calculadoraganhos

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object CrashReport {

    private const val FILE = "drivewin_crash.txt"

    fun save(context: Context, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val content = "em " + System.currentTimeMillis() + "\n" + t.toString() + "\n" + sw.toString().take(3500)
        try {
            context.filesDir.resolve(FILE).writeText(content)
        } catch (_: Exception) {
        }
        DriveWinLog.log("crash", t.toString())
    }

    fun last(context: Context): String? {
        return try {
            context.filesDir.resolve(FILE)
                .takeIf { it.exists() }
                ?.readText()
                ?.take(2000)
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            context.filesDir.resolve(FILE).delete()
        } catch (_: Exception) {
        }
    }
}
