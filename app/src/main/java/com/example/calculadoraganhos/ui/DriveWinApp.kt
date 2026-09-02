package com.example.calculadoraganhos.ui

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.calculadoraganhos.OcrFallback
import com.example.calculadoraganhos.ParsingUtils
import com.example.calculadoraganhos.Prefs
import kotlinx.coroutines.delay

@Composable
fun DriveWinApp(
    openA11y: () -> Unit,
    openOverlay: () -> Unit,
    openBattery: () -> Unit,
    startMonitor: () -> Unit,
    requestCapture: () -> Unit
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
            }
        }
    ) { padding ->
        when (tab) {
            0 -> LeituraScreen(
                Modifier.padding(padding),
                openA11y, openOverlay, openBattery, startMonitor
            )
            else -> MetasScreen(Modifier.padding(padding), requestCapture)
        }
    }
}

@Composable
private fun LeituraScreen(
    modifier: Modifier = Modifier,
    openA11y: () -> Unit,
    openOverlay: () -> Unit,
    openBattery: () -> Unit,
    startMonitor: () -> Unit
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
    val serviceState = AppState.serviceState
    val lastOffer = remember(tick) { Prefs(ctx).lastOffer }
    val ocrAvail = remember(tick) { OcrFallback.available() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("DRIVEWIN", color = VerdeNeon, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "CALCULADORA DE GANHOS",
            color = RosaLilas, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(20.dp))
        StatusCard(a11y, overlay, serviceState)

        Spacer(Modifier.height(24.dp))
        SectionTitle("PERMISSOES")
        Spacer(Modifier.height(8.dp))
        PermRow("Acessibilidade", if (a11y) "Ativa" else "Desativada", a11y, openA11y)
        PermRow("Sobreposicao", if (overlay) "Permitida" else "Nao permitida", overlay, openOverlay)
        PermRow("Bateria", if (battery) "Isenta" else "Ativa", battery, openBattery)

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = startMonitor,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = VerdeNeon,
                contentColor = Color(0xFF000000)
            )
        ) {
            Text("INICIAR MONITORAMENTO", fontWeight = FontWeight.Bold)
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
                color = if (lastOffer != null) Color.White else Color(0xFF8A8A8A),
                fontSize = 14.sp
            )
        }

        if (ocrAvail) {
            Spacer(Modifier.height(12.dp))
            Text(
                "OCR ativo: captura de tela autorizada (fallback)",
                color = RosaLilas, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StatusCard(a11y: Boolean, overlay: Boolean, serviceState: String) {
    val ready = a11y && overlay
    val color = if (ready) VerdeNeon else Color(0xFFF5A623)
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (ready) "MONITORANDO" else "CONFIGURE AS PERMISSOES",
                color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Estado: $serviceState",
                color = Color(0xFF8A8A8A), fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (ready) "O card aparece sozinho em cada oferta"
                else "Conceda as permissoes abaixo para comecar",
                color = Color(0xFF8A8A8A), fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = RosaLilas, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PermRow(label: String, status: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFE8E8E8), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            status,
            color = if (ok) VerdeNeon else Color(0xFFF5A623),
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ok) MaterialTheme.colorScheme.surfaceVariant else RosaLilas,
                contentColor = if (ok) VerdeNeon else Color(0xFF000000)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(if (ok) "OK" else "Conceder", fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = Color(0xFF222327))
}

@Composable
private fun MetasScreen(modifier: Modifier = Modifier, requestCapture: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("METAS", color = VerdeNeon, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "O card compara cada oferta com suas metas e da uma nota de 0 a 100.",
            color = Color(0xFF8A8A8A), fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))
        SliderSection(
            label = "Meta R\$/km",
            valueText = "${ParsingUtils.formatMoney(prefs.minPerKm)}/km",
            value = prefs.minPerKm.toFloat(),
            range = 0f..5f,
            onValue = { prefs.minPerKm = it.toDouble() }
        )
        SliderSection(
            label = "Meta R\$/h",
            valueText = "${ParsingUtils.formatMoney(prefs.minPerHour)}/h",
            value = prefs.minPerHour.toFloat(),
            range = 0f..100f,
            onValue = { prefs.minPerHour = it.toDouble() }
        )

        Spacer(Modifier.height(24.dp))
        Text("CLASSIFICACAO", color = RosaLilas, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LegendRow(Color(0xFF31F900), "Excelente - ambas as metas superadas com boa margem")
        LegendRow(Color(0xFF31F900), "Boa - ambas as metas atingidas")
        LegendRow(Color(0xFFF5A623), "Media - apenas uma meta atingida")
        LegendRow(Color(0xFFFF4D4D), "Ruim - nenhuma meta atingida")

        Spacer(Modifier.height(16.dp))
        Text(
            "Nota: 50% desempenho R\$/km + 50% R\$/h, sempre comparado com as metas, limite de 100.",
            color = Color(0xFF8A8A8A), fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))
        Text("APARENCIA DO CARD", color = RosaLilas, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SliderSection(
            label = "Opacidade",
            valueText = "${(prefs.overlayOpacity * 100).toInt()}%",
            value = prefs.overlayOpacity * 100,
            range = 30f..100f,
            onValue = { prefs.overlayOpacity = it / 100f }
        )
        SliderSection(
            label = "Fonte",
            valueText = "${prefs.overlayFontSize.toInt()}",
            value = prefs.overlayFontSize,
            range = 10f..20f,
            onValue = { prefs.overlayFontSize = it }
        )
        SliderSection(
            label = "Tempo de exibicao",
            valueText = "${prefs.overlayShowSeconds}s",
            value = prefs.overlayShowSeconds.toFloat(),
            range = 3f..15f,
            onValue = { prefs.overlayShowSeconds = it.toInt() }
        )
        ToggleRow("Mostrar R\$/km", prefs.showPerKm) { prefs.showPerKm = it }
        ToggleRow("Mostrar R\$/h", prefs.showPerHour) { prefs.showPerHour = it }
        ToggleRow("Mostrar nota", prefs.showScore) { prefs.showScore = it }
        ToggleRow("Alerta sonoro + vibracao", prefs.overlayAlert) { prefs.overlayAlert = it }

        Spacer(Modifier.height(24.dp))
        Text("OCR (FALLBACK)", color = RosaLilas, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Usado apenas quando a acessibilidade nao conseguir ler o card.",
            color = Color(0xFF8A8A8A), fontSize = 11.sp
        )
        ToggleRow("Ativar OCR", prefs.ocrEnabled) { prefs.ocrEnabled = it }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = requestCapture,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = RosaLilas
            )
        ) {
            Text(if (OcrFallback.available()) "Captura de tela autorizada" else "Autorizar captura de tela")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliderSection(
    label: String,
    valueText: String,
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
            Text(label, color = Color(0xFFE8E8E8), fontSize = 13.sp)
            Text(valueText, color = VerdeNeon, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
        Text(label, color = Color(0xFFE8E8E8), fontSize = 14.sp, modifier = Modifier.weight(1f))
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
        Text(text, color = Color(0xFFB8B8B8), fontSize = 12.sp)
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
