package com.example.meteoswiss_wind_reader.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
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
import io.ktor.client.statement.HttpResponse
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
    var windText by remember { mutableStateOf("Loading...") }

    MeteoSwissWindReaderTheme {
        AppScaffold {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = windText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Fetch data on composition
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = fetchWindData()
            windText = result
        }
    }
}

suspend fun fetchWindData(): String {
    val client = HttpClient(Android)
    return try {
        val csv: String = client.get("https://data.geo.admin.ch/ch.meteoschweiz.ogd-smn/bou/ogd-smn_bou_t_now.csv").bodyAsText()
        val latestData = parseLatestWindData(csv)
        latestData?.let { data ->
            val speed = data.fkl010z1 ?: data.fkl010z0 ?: data.fve010z0 ?: 0.0
            val dir = data.dkl010z0 ?: 0.0
            "BOU\n${speed.toString()} km/h\n${directionToString(dir)}"
        } ?: "BOU\nNo data"
    } catch (e: Exception) {
        "Error: ${e.message}"
    } finally {
        client.close()
    }
}

fun parseLatestWindData(csv: String): WindData? {
    val lines = csv.trim().split("\n")
    if (lines.size < 2) return null
    
    val headers = lines[0].split(";")
    val fkl010z1Index = headers.indexOf("fkl010z1")
    val fve010z0Index = headers.indexOf("fve010z0")
    val fkl010z0Index = headers.indexOf("fkl010z0")
    val dkl010z0Index = headers.indexOf("dkl010z0")
    val fu3010z0Index = headers.indexOf("fu3010z0")
    val fkl010z3Index = headers.indexOf("fkl010z3")
    val fu3010z1Index = headers.indexOf("fu3010z1")
    val fu3010z3Index = headers.indexOf("fu3010z3")
    val stationIndex = headers.indexOf("station_abbr")
    val timestampIndex = headers.indexOf("reference_timestamp")

    var lastLine = ""
    for (i in lines.size - 1 downTo 1) {
        if (lines[i].trim().isNotEmpty()) {
            lastLine = lines[i]
            break
        }
    }
    
    if (lastLine.isEmpty()) return null
    
    val values = lastLine.split(";")
    return WindData(
        station_abbr = values.getOrNull(stationIndex) ?: "",
        reference_timestamp = values.getOrNull(timestampIndex) ?: "",
        fkl010z1 = values.getOrNull(fkl010z1Index)?.toDoubleOrNull(),
        fve010z0 = values.getOrNull(fve010z0Index)?.toDoubleOrNull(),
        fkl010z0 = values.getOrNull(fkl010z0Index)?.toDoubleOrNull(),
        dkl010z0 = values.getOrNull(dkl010z0Index)?.toDoubleOrNull(),
        fu3010z0 = values.getOrNull(fu3010z0Index)?.toDoubleOrNull(),
        fkl010z3 = values.getOrNull(fkl010z3Index)?.toDoubleOrNull(),
        fu3010z1 = values.getOrNull(fu3010z1Index)?.toDoubleOrNull(),
        fu3010z3 = values.getOrNull(fu3010z3Index)?.toDoubleOrNull(),
    )
}

fun directionToString(degrees: Double): String {
    val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val index = ((degrees + 11.25) / 22.5).toInt() % 16
    return dirs[index]
}

data class WindData(
    val station_abbr: String,
    val reference_timestamp: String,
    val fkl010z1: Double?,
    val fve010z0: Double?,
    val fkl010z0: Double?,
    val dkl010z0: Double?,
    val fu3010z0: Double?,
    val fkl010z3: Double?,
    val fu3010z1: Double?,
    val fu3010z3: Double?,
)

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp()
}