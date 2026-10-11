package com.carlink.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ==================== Now playing (fed by CarlinkManager MEDIA_DATA) ====================

/** What the iPhone reports as playing. [positionMs] was true at [stampMs] (elapsedRealtime). */
@Immutable
data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val stampMs: Long = 0L,
    val isPlaying: Boolean = false,
) {
    val hasSong: Boolean get() = !title.isNullOrBlank()

    /** Stable key for "the song changed". */
    val songKey: String get() = "${title.orEmpty()}|${artist.orEmpty()}"

    /** Position right now, extrapolated between the adapter's ~1 s ticks. */
    fun livePositionMs(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) return positionMs
        val p = positionMs + (now - stampMs)
        return if (durationMs > 0) p.coerceAtMost(durationMs) else p
    }
}

object NowPlayingStore {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    fun update(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
    ) {
        _state.value =
            NowPlaying(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                positionMs = positionMs,
                stampMs = SystemClock.elapsedRealtime(),
                isPlaying = isPlaying,
            )
    }

    fun clear() {
        _state.value = NowPlaying()
    }
}

/** Recomposes every [periodMs] while composed — drives lyric sync / clocks. */
@Composable
fun rememberTicker(periodMs: Long = 250L): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

// ==================== Network ====================

object AppNet {
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** GET [url] and return the body, or null on any error / non-2xx. Runs on IO. */
    suspend fun get(url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("User-Agent", "Carlink/1.0 (personal car app)")
                c.setRequestProperty("Accept", "application/json")
                try {
                    if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }
}

/** GET raw bytes (images). Null on error. */
suspend fun AppNet.getBytes(url: String): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 10000
            try {
                if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

// ==================== Apple-style glass for the app panels ====================

/**
 * Dark "liquid glass" used over the LIVE CarPlay video. The SurfaceView can't be blurred live,
 * so the fill is denser than the dashboard glass to keep text readable over any CarPlay screen.
 */
fun Modifier.appGlass(
    shape: Shape = RoundedCornerShape(32.dp),
    alpha: Float = 0.80f,
): Modifier =
    this
        .clip(shape)
        .background(Color(0xFF12141C).copy(alpha = alpha))
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.03f))),
        ).border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))),
            shape = shape,
        )

/** Lighter inner glass tile (cards inside a panel). */
fun Modifier.appTile(shape: Shape = RoundedCornerShape(24.dp)): Modifier =
    this
        .clip(shape)
        .background(Color.White.copy(alpha = 0.07f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), shape)

object AppColors {
    val Text = Color.White
    val Secondary = Color.White.copy(alpha = 0.62f)
    val Faint = Color.White.copy(alpha = 0.35f)
    val Accent = Color(0xFF7FB2FF)
    val Pink = Color(0xFFFF5E8A)
    val Green = Color(0xFF4CD787)
    val Orange = Color(0xFFFFB347)
}

/** Big round glass icon button (≥ 76 dp touch target for driving). */
@Composable
fun AppRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 76,
    tint: Color = AppColors.Text,
) {
    Box(
        modifier =
            modifier
                .size(size.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((size * 0.45f).dp))
    }
}

/** Centered message used for "no song", "loading", errors. */
@Composable
fun AppMessage(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Faint, modifier = Modifier.size(72.dp))
        Text(
            title,
            color = AppColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = AppColors.Secondary,
                fontSize = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
