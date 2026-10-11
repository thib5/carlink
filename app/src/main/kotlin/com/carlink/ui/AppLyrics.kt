package com.carlink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private data class LyricLine(
    val timeMs: Long,
    val text: String,
)

private sealed interface LyricsResult {
    data object Loading : LyricsResult

    data object NotFound : LyricsResult

    data object Offline : LyricsResult

    data object Instrumental : LyricsResult

    data class Synced(
        val lines: List<LyricLine>,
    ) : LyricsResult

    data class Plain(
        val text: String,
    ) : LyricsResult
}

private val LRC_TIME = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

private fun parseLrc(lrc: String): List<LyricLine> {
    val out = ArrayList<LyricLine>()
    lrc.lineSequence().forEach { raw ->
        val stamps = LRC_TIME.findAll(raw).toList()
        if (stamps.isEmpty()) return@forEach
        val text = raw.substring(stamps.last().range.last + 1).trim()
        stamps.forEach { m ->
            val ms = m.groupValues[1].toLong() * 60_000L + (m.groupValues[2].toDouble() * 1000).toLong()
            out += LyricLine(ms, text)
        }
    }
    return out.sortedBy { it.timeMs }
}

private fun resultFrom(o: JSONObject): LyricsResult? {
    if (o.optBoolean("instrumental", false)) return LyricsResult.Instrumental
    val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
    if (synced != null) {
        val lines = parseLrc(synced)
        if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
    }
    val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
    return plain?.let { LyricsResult.Plain(it) }
}

/** LRCLIB (free, no key): exact match first, then a fuzzy search. */
private suspend fun fetchLyrics(np: NowPlaying): LyricsResult {
    val title = np.title ?: return LyricsResult.NotFound
    val artist = np.artist.orEmpty()
    val sb = StringBuilder("https://lrclib.net/api/get?track_name=").append(AppNet.enc(title))
    sb.append("&artist_name=").append(AppNet.enc(artist))
    if (!np.album.isNullOrBlank()) sb.append("&album_name=").append(AppNet.enc(np.album))
    if (np.durationMs > 0) sb.append("&duration=").append(np.durationMs / 1000)
    var anyResponse = false
    AppNet.get(sb.toString())?.let { body ->
        anyResponse = true
        runCatching { resultFrom(JSONObject(body)) }.getOrNull()?.let { return it }
    }
    val search =
        "https://lrclib.net/api/search?track_name=${AppNet.enc(title)}&artist_name=${AppNet.enc(artist)}"
    val body = AppNet.get(search)
    if (body == null) return if (anyResponse) LyricsResult.NotFound else probeOffline()
    val arr = runCatching { JSONArray(body) }.getOrNull() ?: return LyricsResult.NotFound
    var plainFallback: LyricsResult? = null
    for (i in 0 until arr.length()) {
        val r = resultFrom(arr.getJSONObject(i)) ?: continue
        if (r is LyricsResult.Synced) return r
        if (plainFallback == null) plainFallback = r
    }
    return plainFallback ?: LyricsResult.NotFound
}

private suspend fun probeOffline(): LyricsResult =
    if (AppNet.get("https://lrclib.net/api/search?q=test") == null) LyricsResult.Offline else LyricsResult.NotFound

@Composable
fun LyricsApp() {
    val np by NowPlayingStore.state.collectAsState()
    var result by remember { mutableStateOf<LyricsResult>(LyricsResult.Loading) }
    var offsetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(np.songKey) {
        offsetMs = 0L
        if (!np.hasSong) return@LaunchedEffect
        result = LyricsResult.Loading
        result = fetchLyrics(np)
    }

    if (!np.hasSong) {
        AppMessage(Icons.Default.MusicOff, "Aucune chanson en cours", "Lance de la musique sur CarPlay.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            np.title.orEmpty(),
            color = AppColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            np.artist.orEmpty(),
            color = AppColors.Secondary,
            fontSize = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            when (val r = result) {
                LyricsResult.Loading -> AppMessage(Icons.Default.Lyrics, "Recherche des paroles…")
                LyricsResult.NotFound -> AppMessage(Icons.Default.Lyrics, "Paroles introuvables", "Pas de paroles pour cette chanson.")
                LyricsResult.Offline -> AppMessage(Icons.Default.CloudOff, "Pas d'internet", "L'auto n'a pas accès à internet en ce moment.")
                LyricsResult.Instrumental -> AppMessage(Icons.Default.Lyrics, "Instrumental", "Cette chanson n'a pas de paroles.")
                is LyricsResult.Plain -> PlainLyrics(r.text)
                is LyricsResult.Synced -> SyncedLyrics(r.lines, np, offsetMs)
            }
        }
        if (result is LyricsResult.Synced) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppRoundButton(Icons.Default.Remove, "Paroles plus tôt", { offsetMs -= 500 }, size = 60)
                Text(
                    "Décalage ${if (offsetMs >= 0) "+" else ""}${"%.1f".format(offsetMs / 1000.0)} s",
                    color = AppColors.Secondary,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                AppRoundButton(Icons.Default.Add, "Paroles plus tard", { offsetMs += 500 }, size = 60)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    np: NowPlaying,
    offsetMs: Long,
) {
    val now = rememberTicker(200L)
    val pos = np.livePositionMs(now) + offsetMs
    val current = lines.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(current, lines) {
        // Keep the current line about a third of the way down the panel.
        listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            val color by animateColorAsState(
                when {
                    active -> AppColors.Text
                    i < current -> AppColors.Faint
                    else -> AppColors.Secondary
                },
                label = "lyricColor",
            )
            Text(
                text = line.text.ifBlank { "♪" },
                color = color,
                fontSize = if (active) 34.sp else 27.sp,
                lineHeight = if (active) 40.sp else 33.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text(
            "Paroles non synchronisées",
            color = AppColors.Faint,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text,
            color = AppColors.Text,
            fontSize = 25.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
        )
    }
}
