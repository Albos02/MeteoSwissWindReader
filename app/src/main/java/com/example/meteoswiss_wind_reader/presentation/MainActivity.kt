package com.example.meteoswiss_wind_reader.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.meteoswiss_wind_reader.R
import com.example.meteoswiss_wind_reader.presentation.theme.MeteoSwissWindReaderTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    var windSpeeds by remember { mutableStateOf<List<Double>>(emptyList()) }
    var lastTimestamp by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val pagerState = rememberPagerState { 2 }

    MeteoSwissWindReaderTheme {
        AppScaffold {
            HorizontalPager(
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> ChartScreen()
                    1 -> WindSpeedDataScreen(
                        windSpeeds = windSpeeds,
                        lastTimestamp = lastTimestamp,
                        isLoading = isLoading,
                        error = error
                    )
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = fetchLast10WindSpeeds()
            windSpeeds = result.speeds
            lastTimestamp = result.lastTimestamp
            error = result.error
            isLoading = false
        }
    }
}

@Composable
fun ChartScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Chart",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WindSpeedDataScreen(
    windSpeeds: List<Double>,
    lastTimestamp: String?,
    isLoading: Boolean,
    error: String?
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading...",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    } else if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Error: $error",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BOU",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            windSpeeds.reversed().chunked(5).forEach { rowSpeeds ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowSpeeds.forEachIndexed { index, speed ->
                        if (index > 0) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.padding(horizontal = 3.dp)
                            ) {
                                Text(
                                    text = "·",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Text(
                            text = String.format("%.1f", speed),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            lastTimestamp?.let { timestamp ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = formatTimestamp(timestamp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class FetchResult(
    val speeds: List<Double>,
    val lastTimestamp: String?,
    val error: String?
)

suspend fun fetchLast10WindSpeeds(): FetchResult {
    val client = HttpClient(Android)
    return try {
        val csv: String = client.get("https://data.geo.admin.ch/ch.meteoschweiz.ogd-smn/bou/ogd-smn_bou_t_now.csv").bodyAsText()
        val result = parseLast10WindSpeeds(csv)
        FetchResult(speeds = result.speeds, lastTimestamp = result.lastTimestamp, error = if (result.speeds.isEmpty()) "No data" else null)
    } catch (e: Exception) {
        FetchResult(emptyList(), null, e.message ?: "Unknown error")
    } finally {
        client.close()
    }
}

data class ParseResult(
    val speeds: List<Double>,
    val lastTimestamp: String?
)

fun parseLast10WindSpeeds(csv: String): ParseResult {
    val lines = csv.trim().split("\n")
    if (lines.size < 2) return ParseResult(emptyList(), null)
    
    val headers = lines[0].split(";")
    val fkl010z1Index = headers.indexOf("fkl010z1")
    val fve010z0Index = headers.indexOf("fve010z0")
    val fkl010z0Index = headers.indexOf("fkl010z0")
    val timestampIndex = headers.indexOf("reference_timestamp")
    
    val speeds = mutableListOf<Double>()
    var lastTimestamp: String? = null
    
    for (i in lines.size - 1 downTo 1) {
        if (speeds.size >= 10) break
        val line = lines[i].trim()
        if (line.isEmpty()) continue
        
        val values = line.split(";")
        val speed = values.getOrNull(fkl010z1Index)?.toDoubleOrNull()
            ?: values.getOrNull(fkl010z0Index)?.toDoubleOrNull()
            ?: values.getOrNull(fve010z0Index)?.toDoubleOrNull()
        speed?.let { speeds.add(it) }
        
        if (speeds.size == 1) {
            lastTimestamp = values.getOrNull(timestampIndex)
        }
    }
    
    return ParseResult(speeds, lastTimestamp)
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp()
}

fun formatTimestamp(timestamp: String): String {
    // Format: "26.09.2026 00:00" -> "26.09.2026 00:00 UTC"
    return "$timestamp UTC"
}