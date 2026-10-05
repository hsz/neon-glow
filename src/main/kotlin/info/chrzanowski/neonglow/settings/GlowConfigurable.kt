package info.chrzanowski.neonglow.settings

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
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.NeonGlowBundle
import info.chrzanowski.neonglow.ui.GlowPreviewPanel
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

    override fun getDisplayName(): String = NeonGlowBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        panel?.let { return it }
        draft = settings.state.normalized()
        val presets = ComboBox(GlowPreset.entries.toTypedArray()).apply {
            selectedIndex = -1
            renderer = SimpleListCellRenderer.create(NeonGlowBundle.message("settings.preset.choose")) { it.toString() }
            accessibleContext.accessibleName = NeonGlowBundle.message("settings.preset.label")
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
            row(NeonGlowBundle.message("settings.preset.label")) {
                cell(presets).applyToComponent { name = "preset" }
            }
            row {
                comment(NeonGlowBundle.message("settings.preset.comment"))
            }
            row {
                comment(NeonGlowBundle.message("settings.preset.theme.comment"))
            }
            row {
                checkBox(NeonGlowBundle.message("settings.enabled"))
                    .bindSelected({ currentDraft().enabled }, { currentDraft().enabled = it })
                    .previewCheckBox("enabled")
                    .applyToComponent { enabled = this }
                    .comment(NeonGlowBundle.message("settings.enabled.comment"))
            }
            group(NeonGlowBundle.message("settings.targets")) {
                row {
                    checkBox(NeonGlowBundle.message("settings.editorText"))
                        .bindSelected({ currentDraft().editorText }, { currentDraft().editorText = it })
                        .previewCheckBox("editorText")
                        .applyToComponent { editorText = this }
                        .comment(NeonGlowBundle.message("settings.editorText.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.uiText"))
                        .bindSelected({ currentDraft().uiText }, { currentDraft().uiText = it })
                        .previewCheckBox("uiText")
                        .applyToComponent { uiText = this }
                        .comment(NeonGlowBundle.message("settings.uiText.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.regularText"))
                        .bindSelected({ currentDraft().regularText }, { currentDraft().regularText = it })
                        .previewCheckBox("regularText")
                        .applyToComponent { regularText = this }
                        .comment(NeonGlowBundle.message("settings.regularText.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.icons"))
                        .bindSelected({ currentDraft().icons }, { currentDraft().icons = it })
                        .previewCheckBox("icons")
                        .applyToComponent { icons = this }
                        .comment(NeonGlowBundle.message("settings.icons.comment"))
                }
            }
            row(NeonGlowBundle.message("settings.brightness")) {
                labelledSlider(
                    "brightness", MIN_BRIGHTNESS_PERCENT, MAX_BRIGHTNESS_PERCENT,
                    NeonGlowBundle.message("settings.brightness.min"), NeonGlowBundle.message("settings.brightness.max"),
                    { currentDraft().brightness.toPercent() }, { currentDraft().brightness = it.toScale() },
                    labelKey = "settings.brightness",
                ).applyToComponent { brightness = this }
                    .comment(NeonGlowBundle.message("settings.brightness.comment"))
            }
            collapsibleGroup(NeonGlowBundle.message("settings.advanced")) {
                row(NeonGlowBundle.message("settings.strength.editor")) {
                    strengthSlider("editorGlowStrength", { currentDraft().editorGlowStrength.toPercent() },
                        { currentDraft().editorGlowStrength = it.toScale() }, "settings.strength.editor")
                        .applyToComponent { editorStrength = this }
                }
                row(NeonGlowBundle.message("settings.strength.ui")) {
                    strengthSlider("uiGlowStrength", { currentDraft().uiGlowStrength.toPercent() },
                        { currentDraft().uiGlowStrength = it.toScale() }, "settings.strength.ui")
                        .applyToComponent { uiStrength = this }
                }
                row(NeonGlowBundle.message("settings.strength.icons")) {
                    strengthSlider("iconGlowStrength", { currentDraft().iconGlowStrength.toPercent() },
                        { currentDraft().iconGlowStrength = it.toScale() }, "settings.strength.icons")
                        .applyToComponent { iconStrength = this }
                }
                row {
                    comment(NeonGlowBundle.message("settings.strength.comment"))
                }
                row {
                    checkBox(NeonGlowBundle.message("settings.synthwaveStyle"))
                        .bindSelected({ currentDraft().synthwaveStyle }, { currentDraft().synthwaveStyle = it })
                        .previewCheckBox("synthwaveStyle")
                        .applyToComponent { synthwaveStyle = this }
                        .comment(NeonGlowBundle.message("settings.synthwaveStyle.comment"))
                }
                row(NeonGlowBundle.message("settings.radius")) {
                    labelledSlider(
                        "radiusPx", MIN_RADIUS, MAX_RADIUS,
                        NeonGlowBundle.message("settings.radius.min"), NeonGlowBundle.message("settings.radius.max"),
                        { currentDraft().radiusPx.roundToInt() }, { currentDraft().radiusPx = it.toFloat() },
                        labelKey = "settings.radius", unit = " px",
                    ).applyToComponent { radius = this }
                        .comment(NeonGlowBundle.message("settings.radius.comment"))
                }
                row(NeonGlowBundle.message("settings.intensity")) {
                    labelledSlider(
                        "intensity", MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT,
                        NeonGlowBundle.message("settings.intensity.min"), NeonGlowBundle.message("settings.intensity.max"),
                        { currentDraft().intensity.toPercent() }, { currentDraft().intensity = it.toScale() },
                        labelKey = "settings.intensity",
                    ).applyToComponent { intensity = this }
                        .comment(NeonGlowBundle.message("settings.intensity.comment"))
                }
            }.apply { expanded = false }
            row {
                checkBox(NeonGlowBundle.message("settings.performanceMode"))
                    .bindSelected({ currentDraft().performanceMode }, { currentDraft().performanceMode = it })
                    .previewCheckBox("performanceMode")
                    .applyToComponent { performanceMode = this }
                    .comment(NeonGlowBundle.message("settings.performanceMode.comment"))
            }
            row {
                comment(NeonGlowBundle.message("settings.powerSave.comment"))
            }
            row {
                checkBox(NeonGlowBundle.message("settings.preview.show"))
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
                    accessibleContext.accessibleName = NeonGlowBundle.message("settings.preview.accessibleName")
                    isVisible = false
                    preview = this
                })
            }
            row {
                comment(NeonGlowBundle.message("settings.preview.comment"))
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
