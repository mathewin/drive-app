package com.example.calculadoraganhos

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

object DriveWinLog {

    private const val TAG = "DriveWin"
    private const val MAX = 120

    private val buffer = mutableStateListOf<String>()

    fun log(source: String, msg: String) {
        val line = "[${System.currentTimeMillis() % 100000 / 1000}] $source: $msg"
        buffer.add(line)
        if (buffer.size > MAX) {
            repeat(20) { if (buffer.isNotEmpty()) buffer.removeAt(0) }
        }
        Log.d(TAG, line)
    }

    fun clear() {
        buffer.clear()
    }

    fun lines(): List<String> = buffer.toList()
}
