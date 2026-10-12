package com.carlink.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
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
import com.carlink.CarlinkManager
import com.carlink.ui.settings.CarlinkSettings
import com.carlink.ui.settings.PhonesTabContent

/** The custom apps reachable from the CarPlay "Applications" button. */
enum class CarApp(
    val label: String,
    val icon: ImageVector,
    val colors: List<Color>,
) {
    LYRICS("Paroles", Icons.Default.Lyrics, listOf(Color(0xFFFF5E8A), Color(0xFFFF2D55))),
    QUIZ("Quiz musical", Icons.Default.Quiz, listOf(Color(0xFFB57BFF), Color(0xFF6E44FF))),
    WEATHER("Météo", Icons.Default.WbSunny, listOf(Color(0xFF5AC8FA), Color(0xFF007AFF))),
    DEVICES("Appareils", Icons.Default.PhoneIphone, listOf(Color(0xFF6EE7A0), Color(0xFF28B463))),
    DRAW("Dessin", Icons.Default.Palette, listOf(Color(0xFFFFB347), Color(0xFFFF6B3D))),
}

/** Which apps (and the menu, as "MENU") the user wants full screen. Kept across reboots. */
private object FullScreenPrefs {
    private const val KEY = "fullscreen"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("carlink_apps", Context.MODE_PRIVATE)

    fun load(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun save(
        ctx: Context,
        set: Set<String>,
    ) {
        prefs(ctx).edit().putStringSet(KEY, set).apply()
    }
}

/** Last app shown + when the panel closed (process memory; a reboot just shows the menu). */
private object PanelMemory {
    private const val REOPEN_WINDOW_MS = 3 * 60 * 1000L
    private var lastApp: CarApp? = null
    private var closedAt = 0L

    fun remember(app: CarApp?) {
        lastApp = app
        closedAt = SystemClock.elapsedRealtime()
    }

    fun appToReopen(): CarApp? =
        lastApp?.takeIf { closedAt > 0 && SystemClock.elapsedRealtime() - closedAt < REOPEN_WINDOW_MS }
}

private val SettingsColors = listOf(Color(0xFFA0A4AE), Color(0xFF5B5F68))
private val HomeColors = listOf(Color(0xFF3A3F4B), Color(0xFF22252D))

/**
 * Side panel drawn over the LIVE CarPlay video (right side, CarPlay stays usable on the left).
 * Follows CarPlay's own safe area: below a visible AAOS status bar and inside the user's edge
 * margins. A rail on the left of the panel is always there to switch app, go home or close.
 */
@Composable
fun AppsPanel(
    carlinkManager: CarlinkManager,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = remember { CarlinkSettings.getInstance(ctx) }
    // Reopen the last app if the panel was closed less than 3 minutes ago, otherwise the menu.
    var current by rememberSaveable { mutableStateOf(PanelMemory.appToReopen()) }
    val latestApp by rememberUpdatedState(current)
    DisposableEffect(Unit) {
        onDispose { PanelMemory.remember(latestApp) }
    }
    var fullScreen by remember { mutableStateOf(FullScreenPrefs.load(ctx)) }
    val key = current?.name ?: "MENU"
    val expanded = key in fullScreen
    val toggleFullScreen = {
        fullScreen = if (expanded) fullScreen - key else fullScreen + key
        FullScreenPrefs.save(ctx, fullScreen)
    }

    // Same top band CarPlay keeps clear (status bar shown in "Plein écran + barre du haut").
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        val right = maxWidth * (settings.marginRightPercent / 100f)
        val left = maxWidth * (settings.marginLeftPercent / 100f)
        val top = maxHeight * (settings.marginTopPercent / 100f)
        val bottom = maxHeight * (settings.marginBottomPercent / 100f)
        val usable = maxWidth - right - left
        val target =
            if (expanded) {
                usable - 16.dp
            } else {
                (usable * (settings.appsPanelWidthPercent / 100f)).coerceAtLeast(520.dp).coerceAtMost(usable)
            }
        val width by animateDpAsState(target, label = "panelWidth")
        // The GM screen's right edge is slanted and both right corners are cut: big matching
        // corners + a wide inner inset on the right so nothing lands under the curve.
        val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 120.dp, bottomStart = 30.dp, bottomEnd = 120.dp)

        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = right, top = top + 8.dp, bottom = bottom + 8.dp)
                    .width(width)
                    .fillMaxHeight()
                    .appGlass(shape = shape, alpha = if (current == CarApp.QUIZ) 1f else 0.94f)
                    // Swallow taps on empty panel areas so they don't fall through to CarPlay.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = 14.dp, end = 76.dp, top = 14.dp, bottom = 40.dp),
        ) {
            Rail(
                current = current,
                expanded = expanded,
                onToggleFullScreen = toggleFullScreen,
                onHome = { current = null },
                onOpen = { current = it },
                onSettings = onOpenSettings,
                onClose = onClose,
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    current?.label ?: "Applications",
                    color = AppColors.Text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                        label = "appContent",
                    ) { app ->
                        when (app) {
                            null -> Launcher(onOpen = { current = it }, onOpenSettings = onOpenSettings)
                            CarApp.LYRICS -> LyricsApp(big = expanded)
                            CarApp.QUIZ -> QuizApp(onNext = { carlinkManager.nextTrack() })
                            CarApp.WEATHER -> WeatherApp()
                            CarApp.DEVICES -> DevicesApp(carlinkManager)
                            CarApp.DRAW -> DrawApp()
                        }
                    }
                }
            }
        }
    }
}

/** Always-visible vertical rail: home, every app, settings, close. */
@Composable
private fun Rail(
    current: CarApp?,
    expanded: Boolean,
    onToggleFullScreen: () -> Unit,
    onHome: () -> Unit,
    onOpen: (CarApp) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RailButton(Icons.Default.Apps, "Menu", HomeColors, selected = current == null, onClick = onHome)
        CarApp.entries.forEach { app ->
            RailButton(app.icon, app.label, app.colors, selected = current == app) { onOpen(app) }
        }
        RailButton(Icons.Default.Settings, "Réglages", SettingsColors, selected = false, onClick = onSettings)
        Spacer(Modifier.height(6.dp))
        AppRoundButton(
            if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
            if (expanded) "Réduire" else "Plein écran",
            onToggleFullScreen,
            size = 56,
        )
        AppRoundButton(Icons.Default.Close, "Fermer", onClose, size = 56)
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(Brush.linearGradient(colors))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)))
                .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.95f else 0.3f), shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Launcher(
    onOpen: (CarApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyVerticalGrid(
        // Fixed-size cells packed from the left, next to the rail (no big gap on the left,
        // spare room ends up on the right, away from the slanted screen edge).
        columns = GridCells.FixedSize(150.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(CarApp.entries) { app ->
            AppTile(app.label, app.icon, app.colors) { onOpen(app) }
        }
        item {
            AppTile("Réglages", Icons.Default.Settings, SettingsColors, onOpenSettings)
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
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(colors))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
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

/** Known phones: tap one to switch CarPlay to it (same cards as the dashboard). */
@Composable
private fun DevicesApp(carlinkManager: CarlinkManager) {
    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent), typography = MaterialTheme.typography) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Touche un appareil pour basculer CarPlay dessus.",
                color = AppColors.Secondary,
                fontSize = 19.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PhonesTabContent(carlinkManager, columns = 2)
            }
        }
    }
}
