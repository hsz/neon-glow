package info.chrzanowski.idesynthwave.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import info.chrzanowski.idesynthwave.GlowManager
import info.chrzanowski.idesynthwave.SynthwaveBundle
import info.chrzanowski.idesynthwave.ui.GlowPreviewPanel
import javax.swing.JCheckBox
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
    private val previewFactory: (() -> GlowSettings.State) -> JComponent,
    private val applySettings: (GlowSettings.State) -> Unit,
) : Configurable {

    internal constructor(settings: GlowSettings, applySettings: (GlowSettings.State) -> Unit) :
        this(settings, { GlowPreviewPanel(it) }, applySettings)

    constructor() : this(GlowSettings.getInstance(), applySettings = { GlowManager.getInstance().applySettings(it) })

    private var panel: DialogPanel? = null
    private var draft: GlowSettings.State? = null
    private var preview: JComponent? = null
    private var presetSelector: ComboBox<GlowPreset>? = null

    private fun currentDraft(): GlowSettings.State = checkNotNull(draft)

    override fun getDisplayName(): String = SynthwaveBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        panel?.let { return it }
        draft = settings.state.normalized()
        val presets = ComboBox(GlowPreset.entries.toTypedArray()).apply {
            selectedIndex = -1
            renderer = SimpleListCellRenderer.create(SynthwaveBundle.message("settings.preset.choose")) { it.toString() }
            accessibleContext.accessibleName = SynthwaveBundle.message("settings.preset.label")
        }
        presetSelector = presets
        lateinit var enabled: JCheckBox
        lateinit var editorText: JCheckBox
        lateinit var uiText: JCheckBox
        lateinit var regularText: JCheckBox
        lateinit var icons: JCheckBox
        lateinit var synthwaveStyle: JCheckBox
        lateinit var performanceMode: JCheckBox
        lateinit var brightness: JSlider
        lateinit var radius: JSlider
        lateinit var intensity: JSlider
        lateinit var editorStrength: JSlider
        lateinit var uiStrength: JSlider
        lateinit var iconStrength: JSlider
        panel = panel {
            row(SynthwaveBundle.message("settings.preset.label")) {
                cell(presets).applyToComponent { name = "preset" }
            }
            row {
                comment(SynthwaveBundle.message("settings.preset.comment"))
            }
            row {
                comment(SynthwaveBundle.message("settings.preset.theme.comment"))
            }
            row {
                checkBox(SynthwaveBundle.message("settings.enabled"))
                    .bindSelected({ currentDraft().enabled }, { currentDraft().enabled = it })
                    .previewCheckBox("enabled")
                    .applyToComponent { enabled = this }
                    .comment(SynthwaveBundle.message("settings.enabled.comment"))
            }
            group(SynthwaveBundle.message("settings.targets")) {
                row {
                    checkBox(SynthwaveBundle.message("settings.editorText"))
                        .bindSelected({ currentDraft().editorText }, { currentDraft().editorText = it })
                        .previewCheckBox("editorText")
                        .applyToComponent { editorText = this }
                }
                row {
                    checkBox(SynthwaveBundle.message("settings.uiText"))
                        .bindSelected({ currentDraft().uiText }, { currentDraft().uiText = it })
                        .previewCheckBox("uiText")
                        .applyToComponent { uiText = this }
                }
                row {
                    checkBox(SynthwaveBundle.message("settings.regularText"))
                        .bindSelected({ currentDraft().regularText }, { currentDraft().regularText = it })
                        .previewCheckBox("regularText")
                        .applyToComponent { regularText = this }
                        .comment(SynthwaveBundle.message("settings.regularText.comment"))
                }
                row {
                    checkBox(SynthwaveBundle.message("settings.icons"))
                        .bindSelected({ currentDraft().icons }, { currentDraft().icons = it })
                        .previewCheckBox("icons")
                        .applyToComponent { icons = this }
                }
            }
            row(SynthwaveBundle.message("settings.brightness")) {
                labelledSlider(
                    "brightness", MIN_BRIGHTNESS_PERCENT, MAX_BRIGHTNESS_PERCENT,
                    SynthwaveBundle.message("settings.brightness.min"), SynthwaveBundle.message("settings.brightness.max"),
                    { currentDraft().brightness.toPercent() }, { currentDraft().brightness = it.toScale() },
                    labelKey = "settings.brightness",
                ).applyToComponent { brightness = this }
                    .comment(SynthwaveBundle.message("settings.brightness.comment"))
            }
            row {
                checkBox(SynthwaveBundle.message("settings.performanceMode"))
                    .bindSelected({ currentDraft().performanceMode }, { currentDraft().performanceMode = it })
                    .previewCheckBox("performanceMode")
                    .applyToComponent { performanceMode = this }
                    .comment(SynthwaveBundle.message("settings.performanceMode.comment"))
            }
            row {
                comment(SynthwaveBundle.message("settings.powerSave.comment"))
            }
            collapsibleGroup(SynthwaveBundle.message("settings.advanced")) {
                row(SynthwaveBundle.message("settings.strength.editor")) {
                    strengthSlider("editorGlowStrength", { currentDraft().editorGlowStrength.toPercent() },
                        { currentDraft().editorGlowStrength = it.toScale() }, "settings.strength.editor")
                        .applyToComponent { editorStrength = this }
                }
                row(SynthwaveBundle.message("settings.strength.ui")) {
                    strengthSlider("uiGlowStrength", { currentDraft().uiGlowStrength.toPercent() },
                        { currentDraft().uiGlowStrength = it.toScale() }, "settings.strength.ui")
                        .applyToComponent { uiStrength = this }
                }
                row(SynthwaveBundle.message("settings.strength.icons")) {
                    strengthSlider("iconGlowStrength", { currentDraft().iconGlowStrength.toPercent() },
                        { currentDraft().iconGlowStrength = it.toScale() }, "settings.strength.icons")
                        .applyToComponent { iconStrength = this }
                }
                row {
                    comment(SynthwaveBundle.message("settings.strength.comment"))
                }
                row {
                    checkBox(SynthwaveBundle.message("settings.synthwaveStyle"))
                        .bindSelected({ currentDraft().synthwaveStyle }, { currentDraft().synthwaveStyle = it })
                        .previewCheckBox("synthwaveStyle")
                        .applyToComponent { synthwaveStyle = this }
                        .comment(SynthwaveBundle.message("settings.synthwaveStyle.comment"))
                }
                row(SynthwaveBundle.message("settings.radius")) {
                    labelledSlider(
                        "radiusPx", MIN_RADIUS, MAX_RADIUS,
                        SynthwaveBundle.message("settings.radius.min"), SynthwaveBundle.message("settings.radius.max"),
                        { currentDraft().radiusPx.roundToInt() }, { currentDraft().radiusPx = it.toFloat() },
                        labelKey = "settings.radius", unit = " px",
                    ).applyToComponent { radius = this }
                        .comment(SynthwaveBundle.message("settings.radius.comment"))
                }
                row(SynthwaveBundle.message("settings.intensity")) {
                    labelledSlider(
                        "intensity", MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT,
                        SynthwaveBundle.message("settings.intensity.min"), SynthwaveBundle.message("settings.intensity.max"),
                        { currentDraft().intensity.toPercent() }, { currentDraft().intensity = it.toScale() },
                        labelKey = "settings.intensity",
                    ).applyToComponent { intensity = this }
                        .comment(SynthwaveBundle.message("settings.intensity.comment"))
                }
            }.apply { expanded = false }
            row {
                checkBox(SynthwaveBundle.message("settings.preview.show"))
                    .applyToComponent {
                        name = "showPreview"
                        addItemListener {
                            preview?.isVisible = isSelected
                            preview?.repaint()
                        }
                    }
            }
            row {
                cell(previewFactory {
                    val saved = currentDraft()
                    saved.copy(
                        enabled = enabled.isSelected,
                        editorText = editorText.isSelected,
                        uiText = uiText.isSelected,
                        regularText = regularText.isSelected,
                        icons = icons.isSelected,
                        synthwaveStyle = synthwaveStyle.isSelected,
                        brightness = brightness.draftValue(saved.brightness),
                        radiusPx = radius.draftValue(saved.radiusPx, scale = 1f),
                        intensity = intensity.draftValue(saved.intensity),
                        editorGlowStrength = editorStrength.draftValue(saved.editorGlowStrength),
                        uiGlowStrength = uiStrength.draftValue(saved.uiGlowStrength),
                        iconGlowStrength = iconStrength.draftValue(saved.iconGlowStrength),
                        performanceMode = performanceMode.isSelected,
                    ).normalized()
                }.apply {
                    name = "glowPreview"
                    accessibleContext.accessibleName = SynthwaveBundle.message("settings.preview.accessibleName")
                    isVisible = false
                    preview = this
                })
            }
            row {
                comment(SynthwaveBundle.message("settings.preview.comment"))
            }
        }
        presets.addActionListener {
            val selected = presets.selectedItem as? GlowPreset ?: return@addActionListener
            draft = selected.createState()
            panel?.reset()
            preview?.repaint()
        }
        return panel!!
    }

    private fun Cell<JCheckBox>.previewCheckBox(id: String): Cell<JCheckBox> = applyToComponent {
        name = id
        addItemListener { preview?.repaint() }
    }

    private fun JSlider.draftValue(saved: Float, scale: Float = 100f): Float =
        if (value == (saved * scale).roundToInt()) saved else value / scale

    private fun Row.strengthSlider(id: String, read: () -> Int, write: (Int) -> Unit, labelKey: String): Cell<JSlider> =
        labelledSlider(
            id, MIN_STRENGTH_PERCENT, MAX_STRENGTH_PERCENT,
            SynthwaveBundle.message("settings.strength.min"), SynthwaveBundle.message("settings.strength.max"), read, write,
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
                accessibleContext.accessibleName = SynthwaveBundle.message(labelKey)
                valueLabel.labelFor = this
                valueLabel.text = "$value$unit"
                paintTicks = false
                addChangeListener {
                    valueLabel.text = "$value$unit"
                    preview?.repaint()
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
        presetSelector?.selectedIndex = -1
        preview?.repaint()
    }

    override fun disposeUIResources() {
        panel = null
        draft = null
        preview = null
        presetSelector = null
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
