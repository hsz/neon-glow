package info.chrzanowski.neonglow.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import info.chrzanowski.neonglow.render.SynthwaveTextStyle

/**
 * Persists the glow settings across IDE restarts.
 */
@Service(Service.Level.APP)
@State(name = "NeonGlow", storages = [Storage("neon-glow.xml")])
class GlowSettings : PersistentStateComponent<GlowSettings.State> {

    data class State(
        var enabled: Boolean = true,
        /** Same-colour halo reach (`2σ`) in user-space pixels; style mode scales upstream layers from 6 px. */
        var radiusPx: Float = DEFAULT_RADIUS,
        /** Alpha multiplier of the blurred coverage; above `1` thickens the halo of thin strokes. */
        var intensity: Float = DEFAULT_INTENSITY,
        var editorText: Boolean = true,
        var uiText: Boolean = true,
        var icons: Boolean = true,
        /** Opacity of variable-colour halo layers after intensity saturation; zero restores original rendering. */
        var brightness: Float = DEFAULT_BRIGHTNESS,
        /** SynthWave '84 text colour mapping and layered glow; icons keep their own colours. */
        var synthwaveStyle: Boolean = true,
        var editorGlowStrength: Float = DEFAULT_EDITOR_STRENGTH,
        var uiGlowStrength: Float = DEFAULT_STRENGTH,
        var iconGlowStrength: Float = DEFAULT_STRENGTH,
        /** Limits new masks generated during each paint without disabling already cached glow. */
        var performanceMode: Boolean = false,
        /** Same-colour glow for text without an eligible SynthWave-style rule, in either text target. */
        var regularText: Boolean = false,
        /** Tracks whether the initial theme preservation check has completed on first run or install. */
        var initialThemePreserved: Boolean = false,
    ) {
        /** Clamps the numbers into their supported ranges and replaces non-finite values with the defaults. */
        fun normalized(): State = copy(
            radiusPx = radiusPx.takeIf { it.isFinite() }?.coerceIn(RADIUS_RANGE) ?: DEFAULT_RADIUS,
            intensity = intensity.takeIf { it.isFinite() }?.coerceIn(INTENSITY_RANGE) ?: DEFAULT_INTENSITY,
            brightness = brightness.takeIf { it.isFinite() }?.coerceIn(BRIGHTNESS_RANGE) ?: DEFAULT_BRIGHTNESS,
            editorGlowStrength = editorGlowStrength.takeIf { it.isFinite() }?.coerceIn(STRENGTH_RANGE) ?: DEFAULT_EDITOR_STRENGTH,
            uiGlowStrength = uiGlowStrength.takeIf { it.isFinite() }?.coerceIn(STRENGTH_RANGE) ?: DEFAULT_STRENGTH,
            iconGlowStrength = iconGlowStrength.takeIf { it.isFinite() }?.coerceIn(STRENGTH_RANGE) ?: DEFAULT_STRENGTH,
        )

        /** Resolves effective brightness for the given background or theme, capping at 25% by default on light surfaces. */
        fun effectiveBrightness(backgroundRgb: Int?, isBrightTheme: Boolean = false): Float {
            val isLight = SynthwaveTextStyle.isLight(backgroundRgb) || (backgroundRgb == null && isBrightTheme)
            return if (isLight && brightness == DEFAULT_BRIGHTNESS) DEFAULT_LIGHT_BRIGHTNESS else brightness
        }
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state.normalized()
    }

    companion object {
        const val DEFAULT_RADIUS: Float = 6f
        const val DEFAULT_INTENSITY: Float = 2f
        const val DEFAULT_BRIGHTNESS: Float = 0.5f
        const val DEFAULT_LIGHT_BRIGHTNESS: Float = 0.25f
        const val DEFAULT_EDITOR_STRENGTH: Float = 0.75f
        const val DEFAULT_STRENGTH: Float = 0.5f

        val RADIUS_RANGE: ClosedFloatingPointRange<Float> = 1f..16f
        val INTENSITY_RANGE: ClosedFloatingPointRange<Float> = 0.25f..4f
        val BRIGHTNESS_RANGE: ClosedFloatingPointRange<Float> = 0f..1f
        val STRENGTH_RANGE: ClosedFloatingPointRange<Float> = 0f..1f

        @JvmStatic
        fun getInstance(): GlowSettings = ApplicationManager.getApplication().getService(GlowSettings::class.java)
    }
}
