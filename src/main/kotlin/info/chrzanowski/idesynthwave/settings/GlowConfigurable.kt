package info.chrzanowski.idesynthwave.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import info.chrzanowski.idesynthwave.GlowManager
import info.chrzanowski.idesynthwave.SynthwaveBundle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.math.roundToInt

/**
 * Settings UI for the glow, appearing under Appearance & Behavior.
 *
 * All edits go to an independent draft: Apply hands the draft to [GlowManager.applySettings] once (which clears the
 * atlas and repaints every editor), Reset reloads the live settings, and disposing the page discards the draft.
 * The radius is edited in whole pixels, the intensity in whole percents.
 */
class GlowConfigurable internal constructor(
    private val settings: GlowSettings,
    private val applySettings: (GlowSettings.State) -> Unit,
) : Configurable {

    constructor() : this(GlowSettings.getInstance(), { GlowManager.getInstance().applySettings(it) })

    private var panel: DialogPanel? = null
    private var draft: GlowSettings.State? = null

    private fun currentDraft(): GlowSettings.State = checkNotNull(draft)

    override fun getDisplayName(): String = SynthwaveBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        panel?.let { return it }
        draft = settings.state.normalized()
        panel = panel {
            row {
                checkBox(SynthwaveBundle.message("settings.enabled"))
                    .bindSelected({ currentDraft().enabled }, { currentDraft().enabled = it })
                    .applyToComponent { name = "enabled" }
                    .comment(SynthwaveBundle.message("settings.enabled.comment"))
            }
            row(SynthwaveBundle.message("settings.radius")) {
                labelledSlider(
                    "radiusPx", MIN_RADIUS, MAX_RADIUS,
                    SynthwaveBundle.message("settings.radius.min"), SynthwaveBundle.message("settings.radius.max"),
                    { currentDraft().radiusPx.roundToInt() }, { currentDraft().radiusPx = it.toFloat() },
                ).comment(SynthwaveBundle.message("settings.radius.comment"))
            }
            row(SynthwaveBundle.message("settings.intensity")) {
                labelledSlider(
                    "intensity", MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT,
                    SynthwaveBundle.message("settings.intensity.min"), SynthwaveBundle.message("settings.intensity.max"),
                    { currentDraft().intensity.toPercent() }, { currentDraft().intensity = it.toScale() },
                ).comment(SynthwaveBundle.message("settings.intensity.comment"))
            }
            row {
                comment(SynthwaveBundle.message("settings.powerSave.comment"))
            }
        }
        return panel!!
    }

    private fun Row.labelledSlider(
        id: String, min: Int, max: Int, minLabel: String, maxLabel: String, read: () -> Int, write: (Int) -> Unit,
    ): Cell<JSlider> = slider(min, max, 0, 0)
        .labelTable(mapOf(min to JLabel(minLabel), max to JLabel(maxLabel)))
        .bindValue(read, write)
        .applyToComponent {
            name = id
            paintTicks = false
        }

    override fun isModified(): Boolean = panel?.let { it.isModified() || currentDraft() != settings.state.normalized() } ?: false

    override fun apply() {
        val currentPanel = panel ?: return
        currentPanel.apply()
        applySettings(currentDraft())
        reset()
    }

    override fun reset() {
        val currentPanel = panel ?: return
        draft = settings.state.normalized()
        currentPanel.reset()
    }

    override fun disposeUIResources() {
        panel = null
        draft = null
    }

    private companion object {
        val MIN_RADIUS = GlowSettings.RADIUS_RANGE.start.roundToInt()
        val MAX_RADIUS = GlowSettings.RADIUS_RANGE.endInclusive.roundToInt()
        val MIN_INTENSITY_PERCENT = GlowSettings.INTENSITY_RANGE.start.toPercent()
        val MAX_INTENSITY_PERCENT = GlowSettings.INTENSITY_RANGE.endInclusive.toPercent()

        fun Float.toPercent(): Int = (this * 100f).roundToInt()
        fun Int.toScale(): Float = this / 100f
    }
}
