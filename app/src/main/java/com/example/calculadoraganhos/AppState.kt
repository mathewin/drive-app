package com.example.calculadoraganhos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppState {

    var serviceState by mutableStateOf("IDLE")
        private set

    var overlayVisible by mutableStateOf(false)
        private set

    fun updateServiceState(state: String) {
        serviceState = state
    }

    fun updateOverlayVisible(visible: Boolean) {
        overlayVisible = visible
    }
}
