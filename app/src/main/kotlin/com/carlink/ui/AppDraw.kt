package com.carlink.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finger stroke. Points are in dp from the canvas top-left (same look at any panel size). */
private class DrawStroke(
    val color: Int,
    val widthDp: Float,
    val points: List<Offset>,
)

private val DRAW_COLORS =
    listOf(
        Color(0xFFFF4D6D), // rouge cœur
        Color(0xFFFFB347),
        Color(0xFFFFE066),
        Color(0xFF4CD787),
        Color(0xFF5AC8FA),
        Color(0xFFB57BFF),
        Color.White,
    )

private val DRAW_SIZES = listOf(5f, 10f, 20f)

/** The drawing is kept in a file, so whoever opens the app next sees it. */
private object DrawingStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "drawing.json")

    fun load(ctx: Context): Pair<List<DrawStroke>, Long> =
        runCatching {
            val f = file(ctx)
            if (!f.exists()) return@runCatching emptyList<DrawStroke>() to 0L
            val arr = JSONArray(f.readText())
            val strokes =
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("p")
                    DrawStroke(
                        color = o.getInt("c"),
                        widthDp = o.getDouble("w").toFloat(),
                        points = (0 until p.length() / 2).map { k -> Offset(p.getDouble(2 * k).toFloat(), p.getDouble(2 * k + 1).toFloat()) },
                    )
                }
            strokes to f.lastModified()
        }.getOrDefault(emptyList<DrawStroke>() to 0L)

    fun save(
        ctx: Context,
        strokes: List<DrawStroke>,
    ) {
        runCatching {
            val arr = JSONArray()
            strokes.forEach { s ->
                val p = JSONArray()
                s.points.forEach { pt ->
                    p.put(Math.round(pt.x * 10) / 10.0)
                    p.put(Math.round(pt.y * 10) / 10.0)
                }
                arr.put(JSONObject().put("c", s.color).put("w", s.widthDp.toDouble()).put("p", p))
            }
            val tmp = File(ctx.filesDir, "drawing.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file(ctx))
        }
    }
}

@Composable
fun DrawApp() {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var savedAt by remember { mutableStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(DRAW_COLORS.first()) }
    var size by remember { mutableFloatStateOf(DRAW_SIZES[1]) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (s, t) = withContext(Dispatchers.IO) { DrawingStore.load(ctx) }
        strokes.clear()
        strokes.addAll(s)
        savedAt = t
        loaded = true
    }

    fun persist() {
        val snapshot = strokes.toList()
        savedAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { DrawingStore.save(ctx, snapshot) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar: colors, sizes, undo, clear.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DRAW_COLORS.forEach { c ->
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (c == color) 4.dp else 1.dp, Color.White.copy(alpha = if (c == color) 0.95f else 0.3f), CircleShape)
                            .clickable { color = c },
                )
            }
            Spacer(Modifier.width(8.dp))
            DRAW_SIZES.forEach { w ->
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (w == size) 0.25f else 0.08f))
                            .clickable { size = w },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size((w * 1.3f).dp).clip(CircleShape).background(Color.White))
                }
            }
            Spacer(Modifier.width(8.dp))
            AppRoundButton(Icons.AutoMirrored.Filled.Undo, "Annuler", {
                if (strokes.isNotEmpty()) {
                    strokes.removeAt(strokes.lastIndex)
                    persist()
                }
            }, size = 52)
            AppRoundButton(Icons.Default.DeleteOutline, "Tout effacer", { confirmClear = true }, size = 52, tint = AppColors.Pink)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0B0D12))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // A plain tap leaves a dot.
                        .pointerInput(color, size) {
                            detectTapGestures { pos ->
                                strokes.add(DrawStroke(color.toArgb(), size, listOf(pos / density)))
                                persist()
                            }
                        }.pointerInput(color, size) {
                            detectDragGestures(
                                onDragStart = { pos -> current = listOf(pos / density) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + (change.position / density)
                                },
                                onDragEnd = {
                                    if (current.isNotEmpty()) strokes.add(DrawStroke(color.toArgb(), size, current))
                                    current = emptyList()
                                    persist()
                                },
                                onDragCancel = { current = emptyList() },
                            )
                        },
            ) {
                fun draw(
                    pts: List<Offset>,
                    c: Color,
                    wDp: Float,
                ) {
                    if (pts.isEmpty()) return
                    val w = wDp * density
                    if (pts.size == 1) {
                        drawCircle(c, radius = w / 2, center = pts[0] * density)
                        return
                    }
                    val path = Path()
                    path.moveTo(pts[0].x * density, pts[0].y * density)
                    for (i in 1 until pts.size) path.lineTo(pts[i].x * density, pts[i].y * density)
                    drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                strokes.forEach { s -> draw(s.points, Color(s.color), s.widthDp) }
                draw(current, color, size)
            }

            if (loaded && strokes.isEmpty() && current.isEmpty()) {
                Text(
                    "Dessine avec ton doigt ♥\nLe dessin reste là pour la prochaine personne.",
                    color = AppColors.Faint,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else if (savedAt > 0) {
                Text(
                    "Dessiné le " + SimpleDateFormat("d MMM 'à' HH:mm", Locale.CANADA_FRENCH).format(Date(savedAt)),
                    color = AppColors.Faint,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Le dessin sera supprimé pour tout le monde.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    strokes.clear()
                    persist()
                }) { Text("Effacer", color = AppColors.Pink) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}
