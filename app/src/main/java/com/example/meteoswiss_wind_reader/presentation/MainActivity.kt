package com.example.meteoswiss_wind_reader.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    MeteoSwissWindReaderTheme {
        AppScaffold {
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
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    windSpeeds.forEachIndexed { index, speed ->
                        if (index > 0) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "·",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Text(
                            text = String.format("%.1f", speed),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = fetchLast10WindSpeeds()
            windSpeeds = result.speeds
            error = result.error
            isLoading = false
        }
    }
}

data class FetchResult(
    val speeds: List<Double>,
    val error: String?
)

suspend fun fetchLast10WindSpeeds(): FetchResult {
    val client = HttpClient(Android)
    return try {
        val csv: String = client.get("https://data.geo.admin.ch/ch.meteoschweiz.ogd-smn/bou/ogd-smn_bou_t_now.csv").bodyAsText()
        val speeds = parseLast10WindSpeeds(csv)
        FetchResult(speeds = speeds, error = if (speeds.isEmpty()) "No data" else null)
    } catch (e: Exception) {
        FetchResult(emptyList(), e.message ?: "Unknown error")
    } finally {
        client.close()
    }
}

fun parseLast10WindSpeeds(csv: String): List<Double> {
    val lines = csv.trim().split("\n")
    if (lines.size < 2) return emptyList()
    
    val headers = lines[0].split(";")
    val fkl010z1Index = headers.indexOf("fkl010z1")
    val fve010z0Index = headers.indexOf("fve010z0")
    val fkl010z0Index = headers.indexOf("fkl010z0")
    
    val speeds = mutableListOf<Double>()
    
    for (i in lines.size - 1 downTo 1) {
        if (speeds.size >= 5) break
        val line = lines[i].trim()
        if (line.isEmpty()) continue
        
        val values = line.split(";")
        val speed = values.getOrNull(fkl010z1Index)?.toDoubleOrNull()
            ?: values.getOrNull(fkl010z0Index)?.toDoubleOrNull()
            ?: values.getOrNull(fve010z0Index)?.toDoubleOrNull()
        speed?.let { speeds.add(it) }
    }
    
    return speeds
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp()
}