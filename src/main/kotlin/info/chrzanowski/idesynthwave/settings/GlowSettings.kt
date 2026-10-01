package info.chrzanowski.idesynthwave.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persists the glow settings across IDE restarts.
 */
@Service(Service.Level.APP)
@State(name = "IdeSynthwave", storages = [Storage("ide-synthwave.xml")])
class GlowSettings : PersistentStateComponent<GlowSettings.State> {

    data class State(
        var enabled: Boolean = true,
        /** Visible halo reach in user-space pixels (`2σ`); the raster extends to `3σ`. */
        var radiusPx: Float = DEFAULT_RADIUS,
        /** Alpha multiplier of the blurred coverage; above `1` thickens the halo of thin strokes. */
        var intensity: Float = DEFAULT_INTENSITY,
    ) {
        /** Clamps the numbers into their supported ranges and replaces non-finite values with the defaults. */
        fun normalized(): State = copy(
            radiusPx = radiusPx.takeIf { it.isFinite() }?.coerceIn(RADIUS_RANGE) ?: DEFAULT_RADIUS,
            intensity = intensity.takeIf { it.isFinite() }?.coerceIn(INTENSITY_RANGE) ?: DEFAULT_INTENSITY,
        )
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state.normalized()
    }

    companion object {
        const val DEFAULT_RADIUS: Float = 6f
        const val DEFAULT_INTENSITY: Float = 3f

        val RADIUS_RANGE: ClosedFloatingPointRange<Float> = 1f..16f
        val INTENSITY_RANGE: ClosedFloatingPointRange<Float> = 0.25f..4f

        @JvmStatic
        fun getInstance(): GlowSettings = ApplicationManager.getApplication().getService(GlowSettings::class.java)
    }
}
