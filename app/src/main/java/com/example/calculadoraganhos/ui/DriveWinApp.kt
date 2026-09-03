package com.example.calculadoraganhos.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculadoraganhos.AppState
import com.example.calculadoraganhos.CalculatorService
import com.example.calculadoraganhos.CrashReport
import com.example.calculadoraganhos.DriveWinLog
import com.example.calculadoraganhos.OcrFallback
import com.example.calculadoraganhos.ParsingUtils
import com.example.calculadoraganhos.Prefs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun DriveWinApp(
    openA11y: () -> Unit,
    openOverlay: () -> Unit,
    openBattery: () -> Unit,
    startMonitor: () -> Unit,
    stopMonitor: () -> Unit,
    testOverlay: () -> Unit,
    requestCapture: () -> Unit,
    requestNotif: () -> Unit,
    requestStorage: () -> Unit,
    testNotif: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("Leitura") },
                    icon = { Text(if (tab == 0) "\u25C9" else "\u25CB") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("Metas") },
                    icon = { Text(if (tab == 1) "\u25C9" else "\u25CB") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    label = { Text("Historico") },
                    icon = { Text(if (tab == 2) "\u25C9" else "\u25CB") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> LeituraScreen(
                Modifier.padding(padding),
                openA11y, openOverlay, openBattery, startMonitor, stopMonitor, testOverlay
            )
            1 -> MetasScreen(
                Modifier.padding(padding),
                requestCapture,
                requestNotif,
                requestStorage,
                testNotif
            )
            else -> HistoryScreen(Modifier.padding(padding))
        }
    }
}

@Composable
private fun LeituraScreen(
    modifier: Modifier = Modifier,
    openA11y: () -> Unit,
    openOverlay: () -> Unit,
    openBattery: () -> Unit,
    startMonitor: () -> Unit,
    stopMonitor: () -> Unit,
    testOverlay: () -> Unit
) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val a11y = remember(tick) { a11yActive(ctx) }
    val overlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val battery = remember(tick) { batteryIgnored(ctx) }
    val monitorFlag = remember(tick) { Prefs(ctx).monitorOn }
    val serviceState = AppState.serviceState
    val lastOffer = remember(tick) { Prefs(ctx).lastOffer }
    val ocrAvail = remember(tick) { OcrFallback.available() }
    val on = monitorFlag && a11y && overlay

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("DRIVEWIN", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "CALCULADORA DE GANHOS",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(20.dp))
        StatusCard(on = on, a11y = a11y, overlay = overlay, serviceState = serviceState)

        Spacer(Modifier.height(24.dp))
        SectionTitle("LIGA / DESLIGA")
        Spacer(Modifier.height(8.dp))
        MonitorToggle(
            on = on,
            pending = monitorFlag && !(a11y && overlay),
            onChecked = { if (it) startMonitor() else stopMonitor() }
        )

        Spacer(Modifier.height(24.dp))
        SectionTitle("PERMISSOES")
        Spacer(Modifier.height(8.dp))
        PermRow("Acessibilidade", if (a11y) "Ativa" else "Desativada", a11y, openA11y)
        PermRow("Sobreposicao", if (overlay) "Permitida" else "Nao permitida", overlay, openOverlay)
        PermRow("Bateria", if (battery) "Isenta" else "Ativa", battery, openBattery)

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = testOverlay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("TESTAR CARD", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("ULTIMA OFERTA")
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                lastOffer ?: "Nenhuma oferta detectada ainda. Abra a Uber ou a 99 e aguarde.",
                color = if (lastOffer != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        if (Build.VERSION.SDK_INT >= 30) {
            Spacer(Modifier.height(12.dp))
            Text(
                "OCR via acessibilidade: ativo automaticamente (sem dialogo)",
                color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp
            )
        } else if (ocrAvail) {
            Spacer(Modifier.height(12.dp))
            Text(
                "OCR ativo: captura manual autorizada",
                color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("LOG (O QUE O APP VE)")
        Spacer(Modifier.height(8.dp))
        val crash = remember(tick) { CrashReport.last(ctx) }
        if (crash != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3A1010), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFF4D4D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ULTIMO CRASH",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { CrashReport.clear(ctx) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("Apagar", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(crash, color = Color(0xFFFFB3B3), fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        val lines = remember(tick) { DriveWinLog.lines() }
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (lines.isEmpty()) {
                Text(
                    "Sem eventos ainda. Abra a Uber ou a 99 com o monitor LIGADO.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
            } else {
                Column {
                    lines.takeLast(26).forEach { l ->
                        Text(l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MonitorToggle(on: Boolean, pending: Boolean, onChecked: (Boolean) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Monitor de corridas",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (on) "LIGADO" else if (pending) "PENDENTE" else "DESLIGADO",
                    color = when {
                        on -> MaterialTheme.colorScheme.primary
                        pending -> Color(0xFFF5A623)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Switch(checked = on, onCheckedChange = onChecked)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    on -> "Ligado - le as ofertas da Uber e da 99 e mostra o card"
                    pending -> "Ligue a Acessibilidade e a Sobreposicao para ativar"
                    else -> "Desligado - o app nao le ofertas"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatusCard(on: Boolean, a11y: Boolean, overlay: Boolean, serviceState: String) {
    val ready = a11y && overlay
    val (title, color) = when {
        on -> "MONITORANDO" to MaterialTheme.colorScheme.primary
        ready -> "DESLIGADO" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "CONFIGURE AS PERMISSOES" to Color(0xFFF5A623)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (on) "Estado: $serviceState" else "Estado: ---",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (on) "O card aparece sozinho em cada oferta"
                else if (!ready) "Conceda as permissoes abaixo para comecar"
                else "Use o LIGA/DESLIGA acima para ativar",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PermRow(label: String, status: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            status,
            color = if (ok) MaterialTheme.colorScheme.primary else Color(0xFFF5A623),
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ok) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.secondary,
                contentColor = if (ok) MaterialTheme.colorScheme.primary else Color(0xFF000000)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(if (ok) "OK" else "Conceder", fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MetasScreen(
    modifier: Modifier = Modifier,
    requestCapture: () -> Unit,
    requestNotif: () -> Unit,
    requestStorage: () -> Unit,
    testNotif: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("METAS", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "O card compara cada oferta com suas metas e da uma nota de 0 a 100.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))
        SliderSection(
            label = "Meta R\$/km",
            format = { ParsingUtils.formatMoney(it.toDouble()) + "/km" },
            value = prefs.minPerKm.toFloat(),
            range = 0f..5f,
            onValue = { prefs.minPerKm = it.toDouble() }
        )
        SliderSection(
            label = "Meta R\$/h",
            format = { ParsingUtils.formatMoney(it.toDouble()) + "/h" },
            value = prefs.minPerHour.toFloat(),
            range = 0f..100f,
            onValue = { prefs.minPerHour = it.toDouble() }
        )

        Spacer(Modifier.height(24.dp))
        Text("CLASSIFICACAO", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LegendRow(Color(0xFF31F900), "Excelente - ambas as metas superadas com boa margem")
        LegendRow(Color(0xFF31F900), "Boa - ambas as metas atingidas")
        LegendRow(Color(0xFFF5A623), "Media - apenas uma meta atingida")
        LegendRow(Color(0xFFFF4D4D), "Ruim - nenhuma meta atingida")

        Spacer(Modifier.height(16.dp))
        Text(
            "Nota: 50% desempenho R\$/km + 50% R\$/h, sempre comparado com as metas, limite de 100.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))
        Text("APARENCIA DO CARD", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SliderSection(
            label = "Opacidade",
            format = { it.toInt().toString() + "%" },
            value = prefs.overlayOpacity * 100,
            range = 30f..100f,
            onValue = { prefs.overlayOpacity = it / 100f }
        )
        SliderSection(
            label = "Fonte",
            format = { it.toInt().toString() },
            value = prefs.overlayFontSize,
            range = 10f..20f,
            onValue = { prefs.overlayFontSize = it }
        )
        SliderSection(
            label = "Tempo de exibicao",
            format = { it.toInt().toString() + "s" },
            value = prefs.overlayShowSeconds.toFloat(),
            range = 3f..15f,
            onValue = { prefs.overlayShowSeconds = it.toInt() }
        )
        ToggleRow("Mostrar R\$/km", prefs.showPerKm) { prefs.showPerKm = it }
        ToggleRow("Mostrar R\$/h", prefs.showPerHour) { prefs.showPerHour = it }
        ToggleRow("Mostrar nota", prefs.showScore) { prefs.showScore = it }
        ToggleRow("Mostrar embarque/desembarque no card", prefs.showAddresses) { prefs.showAddresses = it }
        ToggleRow("Alerta sonoro + vibracao", prefs.overlayAlert) { prefs.overlayAlert = it }

        Spacer(Modifier.height(24.dp))
        Text("OCR (LEITURA POR IMAGEM)", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Ativo sozinho quando o monitor estiver LIGADO: tira screenshot via Acessibilidade (sem pedir nada) e le o card na tela.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
        )
        ToggleRow("Ativar OCR", prefs.ocrEnabled) { prefs.ocrEnabled = it }
        if (Build.VERSION.SDK_INT < 30) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Celular antigo: use a captura manual abaixo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = requestCapture,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (OcrFallback.available()) "Captura manual autorizada" else "Autorizar captura manual")
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Screenshot via acessibilidade: OK (sem dialogo)",
                color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("PRINTS E CARD NA BARRA", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Salva na galeria a tela de cada corrida nova (pasta Pictures/DriveWin) e mostra um card silencioso, que aparece so ao descer a barra de notificacoes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        ToggleRow("Salvar print automatico na galeria", prefs.printAuto) {
            prefs.printAuto = it
            if (it) requestStorage()
        }
        ToggleRow("Card na barra ao ler corrida", prefs.cardNotify) {
            prefs.cardNotify = it
            if (it) requestNotif()
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = testNotif,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Testar card na barra de notificacoes")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Envia agora um card de exemplo. Desca a aba de notificacoes para conferir.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
        )
        Spacer(Modifier.height(24.dp))
        Text("TEMA DO APP", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Modo claro", !AppTheme.dark) {
            prefs.darkMode = !it
            AppTheme.dark = !it
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliderSection(
    label: String,
    format: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit
) {
    var local by remember { mutableFloatStateOf(value) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Text(format(local), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onValue(it)
            },
            valueRange = range
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val entries = prefs.history()
    val options = listOf("Hoje", "7 dias", "30 dias", "Personalizado")
    var selIdx by remember { mutableIntStateOf(1) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var showRange by remember { mutableStateOf(false) }

    val startMs = when (selIdx) {
        0 -> startOfToday()
        1 -> startOfToday() - 6L * DAY_MS
        2 -> startOfToday() - 29L * DAY_MS
        else -> customStart ?: 0L
    }
    val endMs = if (selIdx == 3) (customEnd ?: Long.MAX_VALUE) else Long.MAX_VALUE
    val filtered = entries.filter { raw ->
        val ts = raw.substringBefore('|').toLongOrNull()
        ts == null || (ts in startMs..endMs)
    }
    val total = filtered.sumOf { raw ->
        val f = raw.split("|")
        if (f.size >= 3) f[2].toDoubleOrNull() ?: 0.0 else 0.0
    }

    if (showRange) {
        val rstate = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    rstate.selectedStartDateMillis?.let {
                        customStart = utcToLocalStart(it)
                        customEnd = rstate.selectedEndDateMillis?.let { e -> utcToLocalEnd(e) }
                    }
                    showRange = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showRange = false }) { Text("Cancelar") }
            }
        ) {
            DateRangePicker(state = rstate, showModeToggle = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Historico",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (entries.isNotEmpty()) {
                    Button(
                        onClick = {
                            prefs.clearHistory()
                            tick++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text("Limpar", fontSize = 12.sp)
                    }
                }
            }
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${filtered.size} corridas",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        ParsingUtils.formatMoney(total),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEachIndexed { i, label ->
                        FilterChip(
                            selected = selIdx == i,
                            onClick = {
                                selIdx = i
                                if (i == 3 && customStart == null) showRange = true
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                if (selIdx == 3) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(customStart?.let { histDate(it) } ?: "Escolha o periodo abaixo")
                            customEnd?.let { append(" ate ").append(histDate(it)) }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhuma corrida registrada ainda.\nAs ofertas exibidas aparecem aqui.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhuma corrida nesse periodo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            Column {
                filtered.forEach { raw ->
                    val f = raw.split("|")
                    if (f.size >= 8) {
                        val app = f[1]
                        val fare = f[2].toDoubleOrNull() ?: 0.0
                        val km = f[3].toDoubleOrNull() ?: 0.0
                        val min = f[4].toDoubleOrNull() ?: 0.0
                        val perKm = f[5].toDoubleOrNull() ?: 0.0
                        val perHour = f[6].toDoubleOrNull() ?: 0.0
                        val score = f[7].toIntOrNull() ?: 0
                        val dotColor = when {
                            score >= 80 -> MaterialTheme.colorScheme.primary
                            score >= 60 -> Color(0xFFF5A623)
                            else -> Color(0xFFFF4D4D)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "$app · ${histTime(f[0])}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    buildString {
                                        append(kmCompact(km)).append(" · ")
                                        append(minCompact(min)).append(" · ")
                                        append(ParsingUtils.formatMoney(perHour)).append("/h · ")
                                        append(ParsingUtils.formatMoney(perKm)).append("/km")
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                ParsingUtils.formatMoney(fare),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun histTime(ms: String): String {
    return try {
        val d = Date(ms.toLong())
        val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(d)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val sdf = if (day == today) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        sdf.format(d)
    } catch (e: Exception) {
        ""
    }
}

private fun kmCompact(v: Double): String {
    val num = if (v == v.toLong().toDouble()) {
        v.toLong().toString()
    } else {
        ParsingUtils.formatKm(v).replace(" km", "")
    }
    return "$num km"
}

private fun minCompact(v: Double): String {
    val m = v.toInt()
    val h = m / 60
    val rest = m % 60
    return if (h > 0) "${h}h ${rest}min" else "$rest min"
}

private const val DAY_MS = 86_400_000L

private fun startOfToday(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun utcToLocalStart(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return localAt(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
}

private fun utcToLocalEnd(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return localAt(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 23, 59, 59) + 999
}

private fun localAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
    val c = Calendar.getInstance()
    c.clear()
    c.set(year, month, day, hour, minute, second)
    return c.timeInMillis
}

private fun histDate(ms: Long): String {
    return try {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ms))
    } catch (e: Exception) {
        ""
    }
}

private fun a11yActive(ctx: Context): Boolean {
    val expected = "${ctx.packageName}/${CalculatorService::class.java.name}"
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun batteryIgnored(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}
