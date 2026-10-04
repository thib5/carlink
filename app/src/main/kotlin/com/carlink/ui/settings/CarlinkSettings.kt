package com.carlink.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * User settings for the personal CarPlay build ("Réglages" screen).
 *
 * Plain SharedPreferences (synchronous, ANR-free at this size) — every value is read on the main
 * thread while MainActivity builds the AdapterConfig. Any change that the adapter must learn about
 * sets [adapterConfigDirty], which forces the next connection to send a FULL init (see
 * [AdapterConfigPreference.getInitializationMode]); [com.carlink.MainActivity.reinitialize] then
 * rebuilds the session so the change applies immediately.
 *
 * PERSISTENCE CONTRACT: values are stored by their explicit `key`/`value` — never by enum ordinal.
 */
class CarlinkSettings private constructor(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== Affichage ====================

    /**
     * "Zoom" (taille des icônes), in percent. 100 = native resolution (tiny CarPlay icons on a
     * large high-res panel). Higher values ask the iPhone to render a SMALLER canvas
     * (display / zoom) which the head unit then scales up to fill the screen → everything in
     * CarPlay (icons, text, buttons) appears proportionally bigger. DPI is kept unchanged on
     * purpose so CarPlay keeps the same render scale and only the canvas shrinks.
     */
    var zoomPercent: Int
        get() = prefs.getInt(KEY_ZOOM, DEFAULT_ZOOM).coerceIn(MIN_ZOOM, MAX_ZOOM)
        set(value) = prefs.edit { putInt(KEY_ZOOM, value.coerceIn(MIN_ZOOM, MAX_ZOOM)) }

    var displayMode: DisplayModeSetting
        get() = DisplayModeSetting.fromKey(prefs.getString(KEY_DISPLAY_MODE, null))
        set(value) = prefs.edit { putString(KEY_DISPLAY_MODE, value.key) }

    var fps: Int
        get() = if (prefs.getInt(KEY_FPS, 60) == 30) 30 else 60
        set(value) = prefs.edit { putInt(KEY_FPS, if (value == 30) 30 else 60) }

    var handDrive: HandDriveSetting
        get() = HandDriveSetting.fromValue(prefs.getInt(KEY_HAND_DRIVE, HandDriveSetting.LEFT.value))
        set(value) = prefs.edit { putInt(KEY_HAND_DRIVE, value.value) }

    /**
     * Edge margins (percent of the visible width/height, per side). The GM panel's corners/edges
     * are curved and physically cut the picture; these grow CarPlay's SafeArea so its UI stays
     * inside while the wallpaper still reaches the edge.
     */
    var marginLeftPercent: Int
        get() = prefs.getInt(KEY_MARGIN_LEFT, 0).coerceIn(0, MAX_MARGIN)
        set(value) = prefs.edit { putInt(KEY_MARGIN_LEFT, value.coerceIn(0, MAX_MARGIN)) }

    var marginRightPercent: Int
        get() = prefs.getInt(KEY_MARGIN_RIGHT, 0).coerceIn(0, MAX_MARGIN)
        set(value) = prefs.edit { putInt(KEY_MARGIN_RIGHT, value.coerceIn(0, MAX_MARGIN)) }

    var marginTopPercent: Int
        get() = prefs.getInt(KEY_MARGIN_TOP, 0).coerceIn(0, MAX_MARGIN)
        set(value) = prefs.edit { putInt(KEY_MARGIN_TOP, value.coerceIn(0, MAX_MARGIN)) }

    var marginBottomPercent: Int
        get() = prefs.getInt(KEY_MARGIN_BOTTOM, 0).coerceIn(0, MAX_MARGIN)
        set(value) = prefs.edit { putInt(KEY_MARGIN_BOTTOM, value.coerceIn(0, MAX_MARGIN)) }

    // ==================== Son ====================

    var audioOutput: AudioOutputSetting
        get() = AudioOutputSetting.fromKey(prefs.getString(KEY_AUDIO_OUTPUT, null))
        set(value) = prefs.edit { putString(KEY_AUDIO_OUTPUT, value.key) }

    /** Adapter-side media buffer (ms). Lower = less lag, higher = fewer dropouts. */
    var mediaDelayMs: Int
        get() = prefs.getInt(KEY_MEDIA_DELAY, DEFAULT_MEDIA_DELAY).let { v ->
            if (v in MEDIA_DELAY_OPTIONS) v else DEFAULT_MEDIA_DELAY
        }
        set(value) = prefs.edit { putInt(KEY_MEDIA_DELAY, if (value in MEDIA_DELAY_OPTIONS) value else DEFAULT_MEDIA_DELAY) }

    var micSource: MicSourceSetting
        get() = MicSourceSetting.fromKey(prefs.getString(KEY_MIC_SOURCE, null))
        set(value) = prefs.edit { putString(KEY_MIC_SOURCE, value.key) }

    // ==================== Connexion ====================

    var wifiBand: WifiBandSetting
        get() = WifiBandSetting.fromKey(prefs.getString(KEY_WIFI_BAND, null))
        set(value) = prefs.edit { putString(KEY_WIFI_BAND, value.key) }

    // ==================== Bookkeeping ====================

    /** True when a setting changed since the last successful FULL init. */
    var adapterConfigDirty: Boolean
        get() = prefs.getBoolean(KEY_DIRTY, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_DIRTY, value) }

    /** Restore every setting to its default (and flag a FULL re-init). */
    fun resetToDefaults() {
        prefs.edit(commit = true) {
            clear()
            putBoolean(KEY_DIRTY, true)
        }
    }

    /** Immutable snapshot of everything (for the settings UI state). */
    fun snapshot(): Snapshot =
        Snapshot(
            zoomPercent = zoomPercent,
            displayMode = displayMode,
            fps = fps,
            handDrive = handDrive,
            audioOutput = audioOutput,
            mediaDelayMs = mediaDelayMs,
            micSource = micSource,
            wifiBand = wifiBand,
            marginLeft = marginLeftPercent,
            marginRight = marginRightPercent,
            marginTop = marginTopPercent,
            marginBottom = marginBottomPercent,
        )

    /** Persist a full snapshot in one go. Returns true if anything changed. */
    fun apply(new: Snapshot): Boolean {
        val old = snapshot()
        if (old == new) return false
        prefs.edit(commit = true) {
            putInt(KEY_ZOOM, new.zoomPercent.coerceIn(MIN_ZOOM, MAX_ZOOM))
            putString(KEY_DISPLAY_MODE, new.displayMode.key)
            putInt(KEY_FPS, new.fps)
            putInt(KEY_HAND_DRIVE, new.handDrive.value)
            putString(KEY_AUDIO_OUTPUT, new.audioOutput.key)
            putInt(KEY_MEDIA_DELAY, new.mediaDelayMs)
            putString(KEY_MIC_SOURCE, new.micSource.key)
            putString(KEY_WIFI_BAND, new.wifiBand.key)
            putInt(KEY_MARGIN_LEFT, new.marginLeft.coerceIn(0, MAX_MARGIN))
            putInt(KEY_MARGIN_RIGHT, new.marginRight.coerceIn(0, MAX_MARGIN))
            putInt(KEY_MARGIN_TOP, new.marginTop.coerceIn(0, MAX_MARGIN))
            putInt(KEY_MARGIN_BOTTOM, new.marginBottom.coerceIn(0, MAX_MARGIN))
            putBoolean(KEY_DIRTY, true)
        }
        return true
    }

    data class Snapshot(
        val zoomPercent: Int,
        val displayMode: DisplayModeSetting,
        val fps: Int,
        val handDrive: HandDriveSetting,
        val audioOutput: AudioOutputSetting,
        val mediaDelayMs: Int,
        val micSource: MicSourceSetting,
        val wifiBand: WifiBandSetting,
        val marginLeft: Int = 0,
        val marginRight: Int = 0,
        val marginTop: Int = 0,
        val marginBottom: Int = 0,
    )

    companion object {
        private const val PREFS_NAME = "carlink_user_settings"

        private const val KEY_ZOOM = "zoom_percent"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_FPS = "fps"
        private const val KEY_HAND_DRIVE = "hand_drive"
        private const val KEY_AUDIO_OUTPUT = "audio_output"
        private const val KEY_MEDIA_DELAY = "media_delay_ms"
        private const val KEY_MIC_SOURCE = "mic_source"
        private const val KEY_WIFI_BAND = "wifi_band"
        private const val KEY_MARGIN_LEFT = "margin_left_percent"
        private const val KEY_MARGIN_RIGHT = "margin_right_percent"
        private const val KEY_MARGIN_TOP = "margin_top_percent"
        private const val KEY_MARGIN_BOTTOM = "margin_bottom_percent"
        private const val KEY_DIRTY = "adapter_config_dirty"

        const val MIN_ZOOM = 100
        const val MAX_ZOOM = 200
        const val DEFAULT_ZOOM = 100

        /** Zoom presets shown as big buttons (percent). */
        val ZOOM_PRESETS = listOf(100, 125, 150, 175, 200)

        /** Max edge margin per side (percent). */
        const val MAX_MARGIN = 15

        const val DEFAULT_MEDIA_DELAY = 500
        val MEDIA_DELAY_OPTIONS = listOf(300, 500, 1000, 2000)

        @Volatile
        private var instance: CarlinkSettings? = null

        fun getInstance(context: Context): CarlinkSettings =
            instance ?: synchronized(this) {
                instance ?: CarlinkSettings(context.applicationContext).also { instance = it }
            }

        /** Factory defaults (what "Remettre par défaut" restores). */
        val DEFAULTS =
            Snapshot(
                zoomPercent = DEFAULT_ZOOM,
                displayMode = DisplayModeSetting.DEFAULT,
                fps = 60,
                handDrive = HandDriveSetting.LEFT,
                audioOutput = AudioOutputSetting.DEFAULT,
                mediaDelayMs = DEFAULT_MEDIA_DELAY,
                micSource = MicSourceSetting.DEFAULT,
                wifiBand = WifiBandSetting.DEFAULT,
            )
    }
}

/** How the AAOS system bars (top status bar / dock) behave around CarPlay. */
enum class DisplayModeSetting(
    val key: String,
    val label: String,
    val description: String,
) {
    FULLSCREEN("fullscreen", "Plein écran", "CarPlay prend tout l'écran. Glisse depuis le bord pour voir les barres de l'auto."),
    FULLSCREEN_STATUS(
        "fullscreen_status",
        "Plein écran + barre du haut",
        "CarPlay prend tout l'écran et la barre du haut reste affichée par-dessus, sur le fond CarPlay.",
    ),
    BARS_VISIBLE("bars_visible", "Barres visibles", "Les barres de l'auto restent affichées. CarPlay prend le reste."),
    STATUS_HIDDEN("status_hidden", "Sans barre du haut", "Cache la barre d'état du haut, garde la barre de l'auto."),
    DOCK_HIDDEN("dock_hidden", "Sans barre de l'auto", "Cache la barre/le dock de l'auto, garde la barre du haut."),
    ;

    companion object {
        val DEFAULT = FULLSCREEN

        fun fromKey(key: String?): DisplayModeSetting = entries.find { it.key == key } ?: DEFAULT
    }
}

enum class AudioOutputSetting(
    val key: String,
    val label: String,
    val description: String,
) {
    ADAPTER(
        "adapter",
        "Par l'app (USB)",
        "Le son CarPlay passe par l'adaptateur et sort dans les haut-parleurs de l'auto via cette app.",
    ),
    BLUETOOTH(
        "bluetooth",
        "Bluetooth du téléphone",
        "Le son sort directement du iPhone vers le Bluetooth de l'auto. CarPlay n'envoie que l'image.",
    ),
    ;

    companion object {
        val DEFAULT = ADAPTER

        fun fromKey(key: String?): AudioOutputSetting = entries.find { it.key == key } ?: DEFAULT
    }
}

enum class MicSourceSetting(
    val key: String,
    val label: String,
) {
    CAR("car", "Micro de l'auto"),
    PHONE("phone", "Micro du téléphone"),
    ;

    companion object {
        val DEFAULT = CAR

        fun fromKey(key: String?): MicSourceSetting = entries.find { it.key == key } ?: DEFAULT
    }
}

enum class WifiBandSetting(
    val key: String,
    val label: String,
) {
    BAND_5GHZ("5ghz", "5 GHz (recommandé)"),
    BAND_24GHZ("24ghz", "2,4 GHz"),
    ;

    companion object {
        val DEFAULT = BAND_5GHZ

        fun fromKey(key: String?): WifiBandSetting = entries.find { it.key == key } ?: DEFAULT
    }
}

enum class HandDriveSetting(
    val value: Int,
    val label: String,
) {
    LEFT(0, "Volant à gauche"),
    RIGHT(1, "Volant à droite"),
    ;

    companion object {
        fun fromValue(value: Int): HandDriveSetting = entries.find { it.value == value } ?: LEFT
    }
}
