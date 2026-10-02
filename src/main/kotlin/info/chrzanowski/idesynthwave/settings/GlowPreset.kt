package info.chrzanowski.idesynthwave.settings

import info.chrzanowski.idesynthwave.SynthwaveBundle

/** Starting points for a settings draft, not a persisted identity or a theme selection. */
enum class GlowPreset(private val messageKey: String) {
    CLASSIC("settings.preset.classic"),
    NEON("settings.preset.neon"),
    FOCUS("settings.preset.focus"),
    ACCESSIBLE("settings.preset.accessible");

    fun createState(): GlowSettings.State = when (this) {
        CLASSIC -> GlowSettings.State(
            enabled = true,
            brightness = 0.45f,
            intensity = 1f,
            radiusPx = 6f,
            synthwaveStyle = true,
            editorGlowStrength = 1f,
            uiGlowStrength = 0.25f,
            iconGlowStrength = 0.35f,
            editorText = true,
            uiText = true,
            icons = true,
            performanceMode = false,
        )
        NEON -> CLASSIC.createState().copy(brightness = 0.7f, intensity = 2f, uiGlowStrength = 0.45f, iconGlowStrength = 0.65f)
        FOCUS -> CLASSIC.createState().copy(uiText = false, icons = false, uiGlowStrength = 0f, iconGlowStrength = 0f,
            performanceMode = true)
        ACCESSIBLE -> GlowSettings.State(enabled = false)
    }

    override fun toString(): String = SynthwaveBundle.message(messageKey)
}