package info.chrzanowski.neonglow.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.NeonGlowBundle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.math.roundToInt

/**
 * Settings UI for the glow, appearing under Appearance & Behavior.
 *
 * All edits go to an independent draft: Apply hands the draft to [GlowManager.applySettings] once, preserving warm
 * masks where possible and repainting every editor. Reset reloads live settings; disposal discards the draft.
 * The radius is edited in whole pixels, brightness, intensity and target strengths in whole percents.
 */
class GlowConfigurable internal constructor(
    private val settings: GlowSettings,
    private val applySettings: (GlowSettings.State) -> Unit,
) : Configurable {

    constructor() : this(GlowSettings.getInstance(), applySettings = { GlowManager.getInstance().applySettings(it) })

    private var panel: DialogPanel? = null
    private var draft: GlowSettings.State? = null

    private fun currentDraft(): GlowSettings.State = checkNotNull(draft)

    override fun getDisplayName(): String = NeonGlowBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        panel?.let { return it }
        draft = settings.state.normalized()
        panel = panel {
            group(NeonGlowBundle.message("settings.support.title")) {
                row {
                    icon(AllIcons.General.Information)
                    text(NeonGlowBundle.message("settings.support.comment"))
                }
            }
            row {
                checkBox(NeonGlowBundle.message("settings.enabled"))
                    .bindSelected({ currentDraft().enabled }, { currentDraft().enabled = it })
                    .applyToComponent { name = "enabled" }
                    .comment(NeonGlowBundle.message("settings.enabled.comment"))
            }
            group(NeonGlowBundle.message("settings.targets")) {
                row {
                    checkBox(NeonGlowBundle.message("settings.editorText"))
                        .bindSelected({ currentDraft().editorText }, { currentDraft().editorText = it })
                        .applyToComponent { name = "editorText" }
                        .comment(NeonGlowBundle.message("settings.editorText.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.uiText"))
                        .bindSelected({ currentDraft().uiText }, { currentDraft().uiText = it })
                        .applyToComponent { name = "uiText" }
                        .comment(NeonGlowBundle.message("settings.uiText.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.regularText"))
                        .bindSelected({ currentDraft().regularText }, { currentDraft().regularText = it })
                        .applyToComponent { name = "regularText" }
                        .comment(NeonGlowBundle.message("settings.regularText.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.icons"))
                        .bindSelected({ currentDraft().icons }, { currentDraft().icons = it })
                        .applyToComponent { name = "icons" }
                        .comment(NeonGlowBundle.message("settings.icons.comment"))
                }
            }
            row(NeonGlowBundle.message("settings.brightness")) {
                labelledSlider(
                    "brightness", MIN_BRIGHTNESS_PERCENT, MAX_BRIGHTNESS_PERCENT,
                    NeonGlowBundle.message("settings.brightness.min"), NeonGlowBundle.message("settings.brightness.max"),
                    { currentDraft().brightness.toPercent() }, { currentDraft().brightness = it.toScale() },
                    labelKey = "settings.brightness",
                )
                    .comment(NeonGlowBundle.message("settings.brightness.comment"))
            }
            collapsibleGroup(NeonGlowBundle.message("settings.advanced")) {
                row(NeonGlowBundle.message("settings.strength.editor")) {
                    strengthSlider("editorGlowStrength", { currentDraft().editorGlowStrength.toPercent() },
                        { currentDraft().editorGlowStrength = it.toScale() }, "settings.strength.editor")
                        .comment(NeonGlowBundle.message("settings.strength.editor.comment"))
                }
                row(NeonGlowBundle.message("settings.strength.ui")) {
                    strengthSlider("uiGlowStrength", { currentDraft().uiGlowStrength.toPercent() },
                        { currentDraft().uiGlowStrength = it.toScale() }, "settings.strength.ui")
                        .comment(NeonGlowBundle.message("settings.strength.ui.comment"))
                }
                row(NeonGlowBundle.message("settings.strength.icons")) {
                    strengthSlider("iconGlowStrength", { currentDraft().iconGlowStrength.toPercent() },
                        { currentDraft().iconGlowStrength = it.toScale() }, "settings.strength.icons")
                        .comment(NeonGlowBundle.message("settings.strength.icons.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.synthwaveStyle"))
                        .bindSelected({ currentDraft().synthwaveStyle }, { currentDraft().synthwaveStyle = it })
                        .applyToComponent { name = "synthwaveStyle" }
                        .comment(NeonGlowBundle.message("settings.synthwaveStyle.comment"))
                }
                row(NeonGlowBundle.message("settings.radius")) {
                    labelledSlider(
                        "radiusPx", MIN_RADIUS, MAX_RADIUS,
                        NeonGlowBundle.message("settings.radius.min"), NeonGlowBundle.message("settings.radius.max"),
                        { currentDraft().radiusPx.roundToInt() }, { currentDraft().radiusPx = it.toFloat() },
                        labelKey = "settings.radius", unit = " px",
                    )
                        .comment(NeonGlowBundle.message("settings.radius.comment"))
                }
                row(NeonGlowBundle.message("settings.intensity")) {
                    labelledSlider(
                        "intensity", MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT,
                        NeonGlowBundle.message("settings.intensity.min"), NeonGlowBundle.message("settings.intensity.max"),
                        { currentDraft().intensity.toPercent() }, { currentDraft().intensity = it.toScale() },
                        labelKey = "settings.intensity",
                    )
                        .comment(NeonGlowBundle.message("settings.intensity.comment"))
                }
            }.apply { expanded = false }
            row {
                checkBox(NeonGlowBundle.message("settings.performanceMode"))
                    .bindSelected({ currentDraft().performanceMode }, { currentDraft().performanceMode = it })
                    .applyToComponent { name = "performanceMode" }
                    .comment(NeonGlowBundle.message("settings.performanceMode.comment"))
            }
            row {
                comment(NeonGlowBundle.message("settings.powerSave.comment"))
            }
        }
        return panel!!
    }

    private fun Row.strengthSlider(id: String, read: () -> Int, write: (Int) -> Unit, labelKey: String): Cell<JSlider> =
        labelledSlider(
            id, MIN_STRENGTH_PERCENT, MAX_STRENGTH_PERCENT,
            NeonGlowBundle.message("settings.strength.min"), NeonGlowBundle.message("settings.strength.max"), read, write,
            labelKey = labelKey,
        )

    private fun Row.labelledSlider(
        id: String, min: Int, max: Int, minLabel: String, maxLabel: String, read: () -> Int, write: (Int) -> Unit,
        labelKey: String, unit: String = "%",
    ): Cell<JSlider> {
        val valueLabel = JLabel().apply { name = "${id}Value" }
        val control = slider(min, max, 0, 0)
            .labelTable(mapOf(min to JLabel(minLabel), max to JLabel(maxLabel)))
            .bindValue(read) { if (it != read()) write(it) }
            .applyToComponent {
                name = id
                accessibleContext.accessibleName = NeonGlowBundle.message(labelKey)
                valueLabel.labelFor = this
                valueLabel.text = "$value$unit"
                paintTicks = false
                addChangeListener {
                    valueLabel.text = "$value$unit"
                }
            }
        cell(valueLabel)
        return control
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
        val MIN_BRIGHTNESS_PERCENT = GlowSettings.BRIGHTNESS_RANGE.start.toPercent()
        val MAX_BRIGHTNESS_PERCENT = GlowSettings.BRIGHTNESS_RANGE.endInclusive.toPercent()
        val MIN_STRENGTH_PERCENT = GlowSettings.STRENGTH_RANGE.start.toPercent()
        val MAX_STRENGTH_PERCENT = GlowSettings.STRENGTH_RANGE.endInclusive.toPercent()

        fun Float.toPercent(): Int = (this * 100f).roundToInt()
        fun Int.toScale(): Float = this / 100f
    }
}
