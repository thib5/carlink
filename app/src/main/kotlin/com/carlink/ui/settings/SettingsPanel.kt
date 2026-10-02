package com.carlink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlink.ui.theme.AutomotiveDimens
import com.carlink.ui.theme.GlassButton
import com.carlink.ui.theme.GlassShapes
import com.carlink.ui.theme.frostedGlass

private enum class SettingsSection(
    val title: String,
    val icon: ImageVector,
) {
    DISPLAY("Affichage", Icons.Default.ZoomIn),
    SOUND("Son", Icons.AutoMirrored.Filled.VolumeUp),
    CONNECTION("Connexion", Icons.Default.Wifi),
}

/**
 * Réglages — full-screen frosted-glass panel opened from the dashboard ("Réglages" button).
 *
 * Edits a local draft of [CarlinkSettings.Snapshot]; nothing is saved until "Appliquer".
 * [onApply] persists + applies it (MainActivity rebuilds the session with a FULL init when the
 * adapter needs to learn about the change).
 */
@Composable
fun SettingsPanel(
    onApply: (CarlinkSettings.Snapshot) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { CarlinkSettings.getInstance(context) }
    val saved = remember { store.snapshot() }
    var draft by remember { mutableStateOf(saved) }
    var section by remember { mutableStateOf(SettingsSection.DISPLAY) }
    var confirmDefaults by remember { mutableStateOf(false) }
    val changed = draft != saved
    val needsRestart = changed && !saved.differsOnlyInAppSide(draft)
    val colors = MaterialTheme.colorScheme

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Swallow taps on empty areas so they never reach the CarPlay video underneath.
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---------- Header: Back · title · Apply ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(
                    onClick = onClose,
                    modifier = Modifier.height(AutomotiveDimens.ButtonMinHeight),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (changed) "Annuler" else "Retour", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(24.dp))
                Icon(Icons.Default.Settings, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Réglages",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                    )
                    if (needsRestart) {
                        Text(
                            "La connexion CarPlay va redémarrer (environ 10 secondes).",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.tertiary,
                        )
                    }
                }
                ApplyButton(enabled = changed, onClick = { onApply(draft) })
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Body: section rail + content ----------
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val landscape = maxWidth >= maxHeight
                if (landscape) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsSection.entries.forEach { s ->
                                SectionTab(s, selected = s == section, modifier = Modifier.fillMaxWidth()) { section = s }
                            }
                        }
                        SectionContent(
                            section = section,
                            draft = draft,
                            onChange = { draft = it },
                            onResetDefaults = { confirmDefaults = true },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsSection.entries.forEach { s ->
                                SectionTab(s, selected = s == section, modifier = Modifier.weight(1f)) { section = s }
                            }
                        }
                        SectionContent(
                            section = section,
                            draft = draft,
                            onChange = { draft = it },
                            onResetDefaults = { confirmDefaults = true },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (confirmDefaults) {
        AlertDialog(
            onDismissRequest = { confirmDefaults = false },
            title = { Text("Remettre par défaut ?") },
            text = { Text("Tous les réglages reviennent à leurs valeurs d'origine. Appuie ensuite sur « Appliquer ».") },
            confirmButton = {
                TextButton(onClick = {
                    draft = CarlinkSettings.DEFAULTS
                    confirmDefaults = false
                }) { Text("Remettre par défaut") }
            },
            dismissButton = { TextButton(onClick = { confirmDefaults = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ApplyButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .height(AutomotiveDimens.ButtonMinHeight)
                .clip(GlassShapes.Button)
                .background(if (enabled) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f)
        Icon(Icons.Default.Check, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text("Appliquer", style = MaterialTheme.typography.titleLarge, color = content)
    }
}

@Composable
private fun SectionTab(
    section: SettingsSection,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .heightIn(min = 84.dp)
                .frostedGlass(GlassShapes.Inner, strong = true, tint = if (selected) colors.primary.copy(alpha = 0.28f) else null)
                .clip(GlassShapes.Inner)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurface, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            section.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = colors.onSurface,
        )
    }
}

@Composable
private fun SectionContent(
    section: SettingsSection,
    draft: CarlinkSettings.Snapshot,
    onChange: (CarlinkSettings.Snapshot) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.frostedGlass(GlassShapes.Card, strong = true)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            when (section) {
                SettingsSection.DISPLAY -> DisplaySection(draft, onChange)
                SettingsSection.SOUND -> SoundSection(draft, onChange)
                SettingsSection.CONNECTION -> ConnectionSection(draft, onChange, onResetDefaults)
            }
        }
    }
}

// ============================== Affichage ==============================

@Composable
private fun DisplaySection(
    draft: CarlinkSettings.Snapshot,
    onChange: (CarlinkSettings.Snapshot) -> Unit,
) {
    SettingGroup(
        title = "Taille des icônes (zoom)",
        hint = "Plus gros = plus facile à toucher en roulant, mais moins d'apps par page. " +
            "L'image peut être un peu moins nette à fort zoom.",
    ) {
        ZoomPicker(
            zoom = draft.zoomPercent,
            onZoom = { onChange(draft.copy(zoomPercent = it)) },
        )
    }

    SettingGroup(title = "Barres de l'auto") {
        DisplayModeSetting.entries.forEach { mode ->
            OptionCard(
                title = mode.label,
                description = mode.description,
                selected = draft.displayMode == mode,
                onClick = { onChange(draft.copy(displayMode = mode)) },
            )
        }
    }

    SettingGroup(title = "Fluidité de l'image") {
        PillRow(
            options = listOf(60 to "60 images/s (fluide)", 30 to "30 images/s (économie)"),
            selected = draft.fps,
            onSelect = { onChange(draft.copy(fps = it)) },
        )
    }

    SettingGroup(title = "Position du volant", hint = "Change de quel côté CarPlay place sa barre d'apps.") {
        PillRow(
            options = HandDriveSetting.entries.map { it to it.label },
            selected = draft.handDrive,
            onSelect = { onChange(draft.copy(handDrive = it)) },
        )
    }
}

/**
 * Zoom picker: big preset pills, fine −/+ (5 %) buttons and a live preview of how big CarPlay's
 * app icons will look on the car screen at that zoom.
 */
@Composable
private fun ZoomPicker(
    zoom: Int,
    onZoom: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−", enabled = zoom > CarlinkSettings.MIN_ZOOM) {
                onZoom(((zoom - 5) / 5 * 5).coerceAtLeast(CarlinkSettings.MIN_ZOOM))
            }
            Text(
                "$zoom %",
                modifier = Modifier.width(150.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = colors.primary,
            )
            StepButton("+", enabled = zoom < CarlinkSettings.MAX_ZOOM) {
                onZoom(((zoom + 5) / 5 * 5).coerceAtMost(CarlinkSettings.MAX_ZOOM))
            }
            Spacer(Modifier.width(12.dp))
            Text(zoomLabel(zoom), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        }
        PillRow(
            options = CarlinkSettings.ZOOM_PRESETS.map { it to "$it %" },
            selected = zoom,
            onSelect = onZoom,
        )
        ZoomPreview(zoom)
    }
}

private fun zoomLabel(zoom: Int): String =
    when {
        zoom <= 100 -> "Taille d'origine (très petit)"
        zoom < 140 -> "Un peu plus gros"
        zoom < 165 -> "Gros"
        zoom < 190 -> "Très gros"
        else -> "Énorme"
    }

/** Mock car screen with CarPlay-style icon grid scaled by [zoom] — a "what you'll get" preview. */
@Composable
private fun ZoomPreview(zoom: Int) {
    val colors = MaterialTheme.colorScheme
    val accents =
        listOf(
            Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFFFF375F),
            Color(0xFF5E5CE6), Color(0xFF64D2FF), Color(0xFFFFD60A), Color(0xFFBF5AF2),
        )
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0B0B0F))
                .border(BorderStroke(2.dp, colors.outline.copy(alpha = 0.5f)), RoundedCornerShape(18.dp)),
    ) {
        // At 100 % CarPlay's icons on this panel are ~1/14 of the screen height; scale with zoom.
        val icon = maxHeight * (0.14f * zoom / 100f)
        val gap = icon * 0.45f
        val dockWidth = icon * 1.6f
        Row(modifier = Modifier.fillMaxSize()) {
            // CarPlay dock strip
            Column(
                modifier = Modifier.width(dockWidth).fillMaxHeight().background(Color(0xFF1C1C22)).padding(top = gap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                repeat(3) { i ->
                    Box(Modifier.size(icon * 0.8f).clip(RoundedCornerShape(icon * 0.22f)).background(accents[i + 2]))
                }
            }
            // App grid
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().padding(gap)) {
                val cols = ((maxWidth + gap) / (icon + gap)).toInt().coerceAtLeast(1)
                val rows = ((maxHeight + gap) / (icon + gap)).toInt().coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(rows) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(cols) { c ->
                                Box(
                                    Modifier
                                        .size(icon)
                                        .clip(RoundedCornerShape(icon * 0.22f))
                                        .background(accents[(r * cols + c) % accents.size]),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Aperçu",
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(AutomotiveDimens.ButtonMinHeight)
                .frostedGlass(GlassShapes.Button, strong = true)
                .clip(GlassShapes.Button)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.3f),
        )
    }
}

// ============================== Son ==============================

@Composable
private fun SoundSection(
    draft: CarlinkSettings.Snapshot,
    onChange: (CarlinkSettings.Snapshot) -> Unit,
) {
    SettingGroup(title = "Sortie du son") {
        AudioOutputSetting.entries.forEach { out ->
            OptionCard(
                title = out.label,
                description = out.description,
                selected = draft.audioOutput == out,
                onClick = { onChange(draft.copy(audioOutput = out)) },
            )
        }
    }

    SettingGroup(
        title = "Son qui coupe après 2 secondes",
        hint = "Arrive quand le iPhone est aussi connecté au Bluetooth de l'auto : l'auto croit que " +
            "c'est une autre source et met la musique sur pause. Ces correctifs l'en empêchent.",
    ) {
        ToggleRow(
            title = "Ignorer les « stop » envoyés par l'auto",
            description = "Recommandé. L'auto ne pourra plus mettre CarPlay sur pause en changeant de source. " +
                "Les boutons pause du volant et de l'écran fonctionnent toujours.",
            checked = draft.ignoreSystemStop,
            onCheckedChange = { onChange(draft.copy(ignoreSystemStop = it)) },
        )
        ToggleRow(
            title = "Mode discret",
            description = "À essayer si ça coupe encore. L'app ne se déclare jamais « en lecture » auprès de l'auto, " +
                "donc l'auto ne coupe plus le Bluetooth. Effet secondaire : la carte média de l'auto montre CarPlay sur pause.",
            checked = draft.discreetMediaSession,
            onCheckedChange = { onChange(draft.copy(discreetMediaSession = it)) },
        )
    }

    SettingGroup(
        title = "Délai du son",
        hint = "Si le son grésille ou saute, prends une valeur plus stable.",
    ) {
        PillRow(
            options =
                listOf(
                    300 to "Rapide",
                    500 to "Normal",
                    1000 to "Stable",
                    2000 to "Très stable",
                ),
            selected = draft.mediaDelayMs,
            onSelect = { onChange(draft.copy(mediaDelayMs = it)) },
        )
    }

    SettingGroup(title = "Micro (Siri et appels)") {
        PillRow(
            options = MicSourceSetting.entries.map { it to it.label },
            selected = draft.micSource,
            onSelect = { onChange(draft.copy(micSource = it)) },
        )
    }
}

// ============================== Connexion ==============================

@Composable
private fun ConnectionSection(
    draft: CarlinkSettings.Snapshot,
    onChange: (CarlinkSettings.Snapshot) -> Unit,
    onResetDefaults: () -> Unit,
) {
    SettingGroup(
        title = "Wi-Fi de l'adaptateur",
        hint = "5 GHz est plus rapide. Essaie 2,4 GHz seulement si la connexion sans fil décroche souvent.",
    ) {
        PillRow(
            options = WifiBandSetting.entries.map { it to it.label },
            selected = draft.wifiBand,
            onSelect = { onChange(draft.copy(wifiBand = it)) },
        )
    }

    SettingGroup(title = "Tout remettre à zéro") {
        GlassButton(
            onClick = onResetDefaults,
            contentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.height(AutomotiveDimens.ButtonMinHeight),
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Remettre les réglages par défaut", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// ============================== Building blocks ==============================

@Composable
private fun SettingGroup(
    title: String,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        }
        content()
    }
}

/** Row of big selectable pills; wraps to a second line via weight-equal sizing. */
@Composable
private fun <T> PillRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(AutomotiveDimens.ButtonMinHeight)
                        .frostedGlass(GlassShapes.Button, strong = true, tint = if (isSel) colors.primary.copy(alpha = 0.35f) else null)
                        .then(
                            if (isSel) Modifier.border(BorderStroke(3.dp, colors.primary), GlassShapes.Button) else Modifier,
                        ).clip(GlassShapes.Button)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal),
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Big selectable card with a title + one-line explanation. */
@Composable
private fun OptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .frostedGlass(GlassShapes.Inner, tint = if (selected) colors.primary.copy(alpha = 0.3f) else null)
                .then(if (selected) Modifier.border(BorderStroke(3.dp, colors.primary), GlassShapes.Inner) else Modifier)
                .clip(GlassShapes.Inner)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.primary else Color.Transparent)
                    .border(BorderStroke(3.dp, if (selected) colors.primary else colors.outline), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .frostedGlass(GlassShapes.Inner)
                .clip(GlassShapes.Inner)
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(20.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
