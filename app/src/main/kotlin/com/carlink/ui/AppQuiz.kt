package com.carlink.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class SongDetails(
    val releaseDate: LocalDate?,
    val album: String?,
    val genre: String?,
    val artwork: ImageBitmap?,
)

private fun norm(s: String?): String =
    s.orEmpty().lowercase(Locale.ROOT).replace(Regex("""\(.*?\)|\[.*?]|feat\..*|[^a-z0-9àâäéèêëîïôöùûüç ]"""), " ")
        .replace(Regex("\\s+"), " ").trim()

/** iTunes Search API (free, no key): release date, album, genre and big artwork. */
private suspend fun fetchDetails(np: NowPlaying): SongDetails? {
    val term = AppNet.enc("${np.title.orEmpty()} ${np.artist.orEmpty()}".trim())
    val body = AppNet.get("https://itunes.apple.com/search?term=$term&entity=song&limit=15&country=CA") ?: return null
    val results = runCatching { JSONObject(body).getJSONArray("results") }.getOrNull() ?: return null
    if (results.length() == 0) return null
    val t = norm(np.title)
    val a = norm(np.artist)
    val al = norm(np.album)
    var best: JSONObject? = null
    var bestScore = -1
    for (i in 0 until results.length()) {
        val r = results.getJSONObject(i)
        var score = 0
        val rt = norm(r.optString("trackName"))
        if (rt == t) score += 4 else if (rt.contains(t) || t.contains(rt)) score += 2
        val ra = norm(r.optString("artistName"))
        if (ra == a) score += 3 else if (a.isNotEmpty() && (ra.contains(a) || a.contains(ra))) score += 1
        if (al.isNotEmpty() && norm(r.optString("collectionName")) == al) score += 3
        // Prefer the original release over compilations/remasters: earlier wins on ties.
        if (score > bestScore ||
            (score == bestScore && r.optString("releaseDate") < (best?.optString("releaseDate") ?: "9"))
        ) {
            best = r
            bestScore = score
        }
    }
    val b = best ?: return null
    if (bestScore < 3) return null
    val date = runCatching { LocalDate.parse(b.optString("releaseDate").take(10)) }.getOrNull()
    val artUrl = b.optString("artworkUrl100").takeIf { it.isNotBlank() }?.replace("100x100bb", "600x600bb")
    val art =
        artUrl?.let { url ->
            AppNet.getBytes(url)?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
        }
    return SongDetails(
        releaseDate = date,
        album = b.optString("collectionName").takeIf { it.isNotBlank() },
        genre = b.optString("primaryGenreName").takeIf { it.isNotBlank() },
        artwork = art,
    )
}

private val FR_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.CANADA_FRENCH)

@Composable
fun QuizApp() {
    val np by NowPlayingStore.state.collectAsState()
    var revealed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<SongDetails?>(null) }
    var loadingDetails by remember { mutableStateOf(false) }
    var scored by remember { mutableStateOf(false) }
    var found by rememberSaveable { mutableIntStateOf(0) }
    var missed by rememberSaveable { mutableIntStateOf(0) }

    // New song → hide everything again and prefetch the details in the background.
    LaunchedEffect(np.songKey) {
        revealed = false
        scored = false
        details = null
        if (!np.hasSong) return@LaunchedEffect
        loadingDetails = true
        details = fetchDetails(np)
        loadingDetails = false
    }

    if (!np.hasSong) {
        AppMessage(Icons.Default.MusicOff, "Aucune chanson en cours", "Lance de la musique, puis devine !")
        return
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Score
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScorePill(Icons.Default.Check, found, AppColors.Green)
            Spacer(Modifier.width(16.dp))
            ScorePill(Icons.Default.Close, missed, AppColors.Pink)
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = revealed,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
                label = "reveal",
            ) { isRevealed ->
                if (!isRevealed) {
                    RevealButton(onClick = { revealed = true })
                } else {
                    RevealedCard(np, details, loadingDetails)
                }
            }
        }

        if (revealed && !scored) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScoreButton("Je l'avais !", AppColors.Green, Modifier.weight(1f)) {
                    found++
                    scored = true
                }
                ScoreButton("Raté", AppColors.Pink, Modifier.weight(1f)) {
                    missed++
                    scored = true
                }
            }
        }
    }
}

@Composable
private fun RevealButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(230.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF5E8A), Color(0xFF8A5CFF), Color(0xFF4FA3FF))))
                    .border(2.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                Text("Révélation", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(modifier = Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QuestionMark, contentDescription = null, tint = AppColors.Secondary, modifier = Modifier.size(24.dp))
            Text("Qui chante ? Quelle chanson ?", color = AppColors.Secondary, fontSize = 20.sp)
        }
    }
}

@Composable
private fun RevealedCard(
    np: NowPlaying,
    details: SongDetails?,
    loading: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().appTile().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val art = details?.artwork
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight(0.45f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            np.title.orEmpty(),
            color = AppColors.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            np.artist.orEmpty(),
            color = AppColors.Accent,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        val album = np.album?.takeIf { it.isNotBlank() } ?: details?.album
        if (album != null) {
            Text(
                album,
                color = AppColors.Secondary,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val date = details?.releaseDate
        when {
            date != null ->
                Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${date.year}", color = AppColors.Orange, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "  ·  ${date.format(FR_DATE)}" + (details?.genre?.let { "  ·  $it" } ?: ""),
                        color = AppColors.Secondary,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            loading -> Text("Recherche de la date…", color = AppColors.Faint, fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp))
            else -> Text("Date inconnue", color = AppColors.Faint, fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun ScorePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.appTile(RoundedCornerShape(percent = 50)).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text("  $value", color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScoreButton(
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(76.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(color.copy(alpha = 0.22f))
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
