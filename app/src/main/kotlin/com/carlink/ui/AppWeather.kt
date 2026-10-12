package com.carlink.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val code: Int,
    val precipProb: Int,
    val isDay: Boolean,
)

private data class Day(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val precipProb: Int,
)

private data class Forecast(
    val place: String?,
    val temp: Double,
    val feels: Double,
    val code: Int,
    val isDay: Boolean,
    val wind: Double,
    val humidity: Int,
    val hours: List<Hour>,
    val days: List<Day>,
)

private sealed interface WeatherState {
    data object NeedPermission : WeatherState

    data object Locating : WeatherState

    data object NoLocation : WeatherState

    data object Loading : WeatherState

    data object Offline : WeatherState

    data class Ready(
        val f: Forecast,
    ) : WeatherState
}

/** WMO weather code → French label + icon. */
private fun wmo(
    code: Int,
    isDay: Boolean = true,
): Pair<String, ImageVector> =
    when (code) {
        0 -> (if (isDay) "Ensoleillé" else "Ciel dégagé") to (if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay)
        1 -> "Plutôt dégagé" to (if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay)
        2 -> "Partiellement nuageux" to Icons.Default.WbCloudy
        3 -> "Couvert" to Icons.Default.Cloud
        45, 48 -> "Brouillard" to Icons.Default.Cloud
        51, 53, 55, 56, 57 -> "Bruine" to Icons.Default.Grain
        61, 63, 65, 80, 81, 82 -> "Pluie" to Icons.Default.Umbrella
        66, 67 -> "Pluie verglaçante" to Icons.Default.Umbrella
        71, 73, 75, 77, 85, 86 -> "Neige" to Icons.Default.AcUnit
        95, 96, 99 -> "Orages" to Icons.Default.FlashOn
        else -> "—" to Icons.Default.Cloud
    }

private fun hasLocationPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private suspend fun locate(ctx: Context): Location? {
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
    val providers = lm.getProviders(true)
    // Fresh fix first (car GPS), last-known as fallback.
    for (p in listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
        if (p !in providers) continue
        val fix =
            withTimeoutOrNull(8000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        lm.getCurrentLocation(p, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        if (fix != null) return fix
    }
    return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
}

@Suppress("DEPRECATION")
private suspend fun placeName(
    ctx: Context,
    loc: Location,
): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val a = Geocoder(ctx, Locale.CANADA_FRENCH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull()
    }

private suspend fun fetchForecast(loc: Location): Forecast? {
    val url =
        "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7&forecast_hours=24"
    val body = AppNet.get(url) ?: return null
    return runCatching {
        val o = JSONObject(body)
        val c = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        val now = LocalDateTime.parse(c.getString("time")).withMinute(0)
        val hours =
            (0 until ht.length()).map { i ->
                Hour(
                    time = LocalDateTime.parse(ht.getString(i)),
                    temp = h.getJSONArray("temperature_2m").optDouble(i),
                    code = h.getJSONArray("weather_code").optInt(i),
                    precipProb = h.getJSONArray("precipitation_probability").optInt(i, 0),
                    isDay = h.getJSONArray("is_day").optInt(i, 1) == 1,
                )
            }.filter { !it.time.isBefore(now) }.take(12)
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days =
            (0 until dt.length()).map { i ->
                Day(
                    date = LocalDate.parse(dt.getString(i)),
                    code = d.getJSONArray("weather_code").optInt(i),
                    max = d.getJSONArray("temperature_2m_max").optDouble(i),
                    min = d.getJSONArray("temperature_2m_min").optDouble(i),
                    precipProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0),
                )
            }
        Forecast(
            place = null,
            temp = c.optDouble("temperature_2m"),
            feels = c.optDouble("apparent_temperature"),
            code = c.optInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
            wind = c.optDouble("wind_speed_10m"),
            humidity = c.optInt("relative_humidity_2m"),
            hours = hours,
            days = days,
        )
    }.getOrNull()
}

private fun Double.deg(): String = if (isNaN()) "–" else "${roundToInt()}°"

@Composable
fun WeatherApp() {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<WeatherState>(WeatherState.Locating) }
    var reload by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            granted = res.values.any { it }
        }

    LaunchedEffect(granted, reload) {
        if (!granted) {
            state = WeatherState.NeedPermission
            return@LaunchedEffect
        }
        state = WeatherState.Locating
        val loc = locate(ctx)
        if (loc == null) {
            state = WeatherState.NoLocation
            return@LaunchedEffect
        }
        state = WeatherState.Loading
        val f = fetchForecast(loc)
        state =
            if (f == null) {
                WeatherState.Offline
            } else {
                WeatherState.Ready(f.copy(place = placeName(ctx, loc)))
            }
    }

    when (val s = state) {
        WeatherState.NeedPermission -> {
            RetryMessage(Icons.Default.LocationOn, "Position nécessaire", "Pour la météo de l'endroit où tu es.") {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            LaunchedEffect(Unit) {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
        WeatherState.Locating -> AppMessage(Icons.Default.LocationOn, "Localisation…")
        WeatherState.NoLocation -> RetryMessage(Icons.Default.LocationOff, "Position introuvable", "Le GPS de l'auto n'a pas répondu.") { reload++ }
        WeatherState.Loading -> AppMessage(Icons.Default.WbCloudy, "Chargement de la météo…")
        WeatherState.Offline -> RetryMessage(Icons.Default.CloudOff, "Météo indisponible", "Pas d'internet dans l'auto ?") { reload++ }
        is WeatherState.Ready -> ForecastView(s.f) { reload++ }
    }
}

@Composable
private fun RetryMessage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AppMessage(icon, title, subtitle)
        AppRoundButton(Icons.Default.Refresh, "Réessayer", onRetry, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ForecastView(
    f: Forecast,
    onRefresh: () -> Unit,
) {
    // Wide panel (full screen): current + hours on the left, the week on the right.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 820.dp) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    NowBlock(f, onRefresh)
                    HoursBlock(f)
                }
                Column(modifier = Modifier.weight(0.85f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    DaysBlock(f)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NowBlock(f, onRefresh)
                HoursBlock(f)
                Spacer(Modifier.height(14.dp))
                DaysBlock(f)
            }
        }
    }
}

@Composable
private fun NowBlock(
    f: Forecast,
    onRefresh: () -> Unit,
) {
    val (label, icon) = wmo(f.code, f.isDay)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppColors.Secondary, modifier = Modifier.size(24.dp))
        Text(
            f.place ?: "Ici",
            color = AppColors.Secondary,
            fontSize = 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        AppRoundButton(Icons.Default.Refresh, "Actualiser", onRefresh, size = 56)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = if (f.isDay) AppColors.Orange else AppColors.Accent, modifier = Modifier.size(96.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(f.temp.deg(), color = AppColors.Text, fontSize = 76.sp, fontWeight = FontWeight.Light, lineHeight = 80.sp)
            Text(label, color = AppColors.Text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 16.dp)) {
        Stat(Icons.Default.WbSunny, "Ressenti ${f.feels.deg()}")
        Stat(Icons.Default.Air, "${f.wind.roundToInt()} km/h")
        Stat(Icons.Default.WaterDrop, "${f.humidity} %")
    }
}

@Composable
private fun HoursBlock(f: Forecast) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().appTile().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(f.hours) { h ->
            val (_, hi) = wmo(h.code, h.isDay)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
                Text("${h.time.hour} h", color = AppColors.Secondary, fontSize = 17.sp)
                Icon(hi, contentDescription = null, tint = AppColors.Text, modifier = Modifier.padding(vertical = 6.dp).size(30.dp))
                Text(h.temp.deg(), color = AppColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (h.precipProb >= 10) "${h.precipProb} %" else " ",
                    color = AppColors.Accent,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun DaysBlock(f: Forecast) {
    Column(modifier = Modifier.fillMaxWidth().appTile().padding(horizontal = 18.dp, vertical = 8.dp)) {
        f.days.forEachIndexed { i, d ->
            val (_, di) = wmo(d.code)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    if (i == 0) "Auj." else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CANADA_FRENCH).replaceFirstChar { it.uppercase() },
                    color = AppColors.Text,
                    fontSize = 21.sp,
                    modifier = Modifier.width(80.dp),
                )
                Icon(di, contentDescription = null, tint = AppColors.Text, modifier = Modifier.size(28.dp))
                Text(
                    if (d.precipProb >= 10) "${d.precipProb} %" else "",
                    color = AppColors.Accent,
                    fontSize = 16.sp,
                    modifier = Modifier.width(70.dp).padding(start = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(d.min.deg(), color = AppColors.Secondary, fontSize = 21.sp)
                Text("  /  ", color = AppColors.Faint, fontSize = 21.sp)
                Text(d.max.deg(), color = AppColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Text(
        "Données : Open-Meteo",
        color = AppColors.Faint,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
    )
}

@Composable
private fun Stat(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.appTile(RoundedCornerShape(percent = 50)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Secondary, modifier = Modifier.size(20.dp))
        Text("  $text", color = AppColors.Text, fontSize = 18.sp)
    }
}
