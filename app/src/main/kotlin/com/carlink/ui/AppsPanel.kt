package com.carlink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlink.ui.settings.CarlinkSettings

/** The custom apps reachable from the CarPlay "Applications" button. */
enum class CarApp(
    val label: String,
    val icon: ImageVector,
    val colors: List<Color>,
) {
    LYRICS("Paroles", Icons.Default.Lyrics, listOf(Color(0xFFFF5E8A), Color(0xFFFF2D55))),
    QUIZ("Quiz musical", Icons.Default.Quiz, listOf(Color(0xFFB57BFF), Color(0xFF6E44FF))),
    WEATHER("Météo", Icons.Default.WbSunny, listOf(Color(0xFF5AC8FA), Color(0xFF007AFF))),
    CAMERA("Caméra", Icons.Default.Videocam, listOf(Color(0xFF6EE7A0), Color(0xFF28B463))),
}

/**
 * Side panel drawn over the LIVE CarPlay video (right side, CarPlay stays usable on the left).
 * Shows the app launcher, then the chosen app. Respects the user's edge margins so the curved
 * screen edge doesn't cut it. Touches outside the panel go straight to CarPlay.
 */
@Composable
fun AppsPanel(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = remember { CarlinkSettings.getInstance(ctx) }
    var current by rememberSaveable { mutableStateOf<CarApp?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val right = maxWidth * (settings.marginRightPercent / 100f)
        val left = maxWidth * (settings.marginLeftPercent / 100f)
        val top = maxHeight * (settings.marginTopPercent / 100f)
        val bottom = maxHeight * (settings.marginBottomPercent / 100f)
        val usable = maxWidth - right - left
        val target = if (expanded) usable - 24.dp else (usable * 0.42f).coerceAtLeast(440.dp).coerceAtMost(usable)
        val width by animateDpAsState(target, label = "panelWidth")

        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = right + 12.dp, top = top + 12.dp, bottom = bottom + 12.dp)
                    .width(width)
                    .fillMaxHeight()
                    .appGlass(alpha = if (expanded) 0.9f else 0.82f)
                    // Swallow taps on empty panel areas so they don't fall through to CarPlay.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PanelHeader(
                    title = current?.label ?: "Applications",
                    showBack = current != null,
                    expanded = expanded,
                    onBack = { current = null },
                    onToggleExpand = { expanded = !expanded },
                    onClose = onClose,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 8 }).togetherWith(fadeOut())
                        },
                        label = "appContent",
                    ) { app ->
                        when (app) {
                            null ->
                                Launcher(
                                    onOpen = { current = it },
                                    onOpenSettings = onOpenSettings,
                                )
                            CarApp.LYRICS -> LyricsApp()
                            CarApp.QUIZ -> QuizApp()
                            CarApp.WEATHER -> WeatherApp()
                            CarApp.CAMERA -> CameraApp()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    showBack: Boolean,
    expanded: Boolean,
    onBack: () -> Unit,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            AppRoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour", onBack, size = 64)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            color = AppColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AppRoundButton(
            if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
            if (expanded) "Réduire" else "Agrandir",
            onToggleExpand,
            size = 64,
        )
        Spacer(Modifier.width(12.dp))
        AppRoundButton(Icons.Default.Close, "Fermer", onClose, size = 64)
    }
}

@Composable
private fun Launcher(
    onOpen: (CarApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(CarApp.entries) { app ->
            AppTile(app.label, app.icon, app.colors) { onOpen(app) }
        }
        item {
            AppTile("Réglages", Icons.Default.Settings, listOf(Color(0xFFA0A4AE), Color(0xFF5B5F68)), onOpenSettings)
        }
    }
}

/** iOS-style app tile: gradient squircle icon with a glass rim, label underneath. */
@Composable
private fun AppTile(
    label: String,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(108.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(colors))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
        }
        Text(
            label,
            color = AppColors.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
