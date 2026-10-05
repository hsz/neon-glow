package info.chrzanowski.neonglow.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.neonglow.NeonGlowBundle
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.text.JTextComponent

class GlowConfigurableTest : BasePlatformTestCase() {

    private lateinit var settings: GlowSettings
    private lateinit var configurable: GlowConfigurable
    private lateinit var component: JComponent
    private lateinit var previewState: () -> GlowSettings.State
    private lateinit var preview: JPanel
    private val applied = mutableListOf<GlowSettings.State>()

    override fun setUp() {
        super.setUp()
        settings = GlowSettings()
        applied.clear()
        configurable = GlowConfigurable(settings, previewFactory = {
            previewState = it
            JPanel().also { panel -> preview = panel }
        }) {
            settings.loadState(it)
            applied += it
        }
        component = configurable.createComponent()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
        } finally {
            super.tearDown()
        }
    }

    fun `test new page is unmodified, reuses its component and shows the bundle labels`() {
        assertFalse(configurable.isModified())
        assertSame(component, configurable.createComponent())
        assertEquals(NeonGlowBundle.message("settings.displayName"), configurable.displayName)
        assertEquals(NeonGlowBundle.message("settings.displayName"), GlowConfigurable().displayName)

        assertNull("opening settings must not imply a preset was chosen", presets().selectedItem)

        val labels = descendants(component).filterIsInstance<JLabel>().map { it.text }.toList()
        assertTrue(NeonGlowBundle.message("settings.radius") in labels)
        assertTrue(NeonGlowBundle.message("settings.intensity") in labels)
        assertTrue(NeonGlowBundle.message("settings.brightness") in labels)
        assertTrue(NeonGlowBundle.message("settings.preset.label") in labels)
        for (key in listOf("settings.strength.editor", "settings.strength.ui", "settings.strength.icons")) {
            assertTrue(NeonGlowBundle.message(key) in labels)
        }
        assertEquals(NeonGlowBundle.message("settings.enabled"), checkBox().text)
        assertEquals(NeonGlowBundle.message("settings.regularText"), checkBox("regularText").text)
        assertEquals(NeonGlowBundle.message("settings.synthwaveStyle"), checkBox("synthwaveStyle").text)
        assertEquals(NeonGlowBundle.message("settings.performanceMode"), checkBox("performanceMode").text)
        assertFalse(descendants(component).any { it is JButton && it.name == "usePreset" })
        assertEquals(NeonGlowBundle.message("settings.preset.label"), presets().accessibleContext.accessibleName)
        val placeholder = presets().renderer.getListCellRendererComponent(javax.swing.JList(), null, -1, false, false)
        assertEquals(NeonGlowBundle.message("settings.preset.choose"), (placeholder as JLabel).text)
        assertEquals(listOf("preset", "enabled", "editorText", "uiText", "regularText", "icons", "brightness",
            "editorGlowStrength", "uiGlowStrength", "iconGlowStrength", "synthwaveStyle",
            "radiusPx", "intensity", "performanceMode", "showPreview"), descendants(component).filter {
            it is JCheckBox || it is JSlider || it is JComboBox<*>
        }.mapNotNull { it.name }.toList())
    }

    fun `test every glow target has a separate visible description`() {
        val descriptions = descendants(component).mapNotNull {
            when (it) {
                is JLabel -> it.text
                is JTextComponent -> it.document.getText(0, it.document.length)
                else -> null
            }
        }.map { it.replace(Regex("\\s+"), " ").trim() }.toList()
        for (target in listOf("editorText", "uiText", "regularText", "icons")) {
            assertEquals(NeonGlowBundle.message("settings.$target"), checkBox(target).text)
            val description = NeonGlowBundle.message("settings.$target.comment")
            assertTrue("$target must have a separate description", descriptions.any { description in it })
        }
        assertEquals("Editor text", checkBox("editorText").text)
        assertEquals("UI text", checkBox("uiText").text)
        assertFalse(configurable.isModified())
    }

    fun `test everyday controls are visible and fine tuning is initially collapsed`() {
        for (name in listOf("enabled", "editorText", "uiText", "regularText", "icons", "performanceMode", "showPreview")) {
            assertTrue("$name must be visible", visibleWithinPage(checkBox(name)))
        }
        assertTrue(visibleWithinPage(slider("brightness")))
        for (name in listOf("editorGlowStrength", "uiGlowStrength", "iconGlowStrength", "radiusPx", "intensity")) {
            assertFalse("$name must start collapsed", visibleWithinPage(slider(name)))
        }
        assertFalse(visibleWithinPage(checkBox("synthwaveStyle")))
        assertFalse(configurable.isModified())
    }

    fun `test every slider exposes its name and numeric value through edits presets and reset`() {
        val labels = mapOf("brightness" to "settings.brightness", "radiusPx" to "settings.radius",
            "intensity" to "settings.intensity", "editorGlowStrength" to "settings.strength.editor",
            "uiGlowStrength" to "settings.strength.ui", "iconGlowStrength" to "settings.strength.icons")
        for ((name, key) in labels) {
            val control = slider(name)
            val readout = strengthReadout(name)
            assertEquals(NeonGlowBundle.message(key), control.accessibleContext.accessibleName)
            assertSame(control, readout.labelFor)
            control.value = control.minimum
            assertEquals("${control.value}${if (name == "radiusPx") " px" else "%"}", readout.text)
        }
        presets().selectedItem = GlowPreset.CLASSIC
        assertEquals("6 px", strengthReadout("radiusPx").text)
        assertEquals("100%", strengthReadout("intensity").text)
        assertEquals("45%", strengthReadout("brightness").text)
        configurable.reset()
        assertEquals("6 px", strengthReadout("radiusPx").text)
        assertEquals("200%", strengthReadout("intensity").text)
        assertEquals("50%", strengthReadout("brightness").text)
        assertFalse(configurable.isModified())
    }

    fun `test controls show the current settings`() {
        assertTrue(checkBox().isSelected)
        for (target in listOf("editorText", "uiText", "icons")) assertTrue(checkBox(target).isSelected)
        assertTrue(checkBox("synthwaveStyle").isSelected)
        assertFalse(checkBox("regularText").isSelected)
        assertEquals(GlowSettings.DEFAULT_RADIUS.toInt(), slider("radiusPx").value)
        assertEquals((GlowSettings.DEFAULT_INTENSITY * 100).toInt(), slider("intensity").value)
        assertEquals(GlowSettings.RADIUS_RANGE.start.toInt(), slider("radiusPx").minimum)
        assertEquals(GlowSettings.RADIUS_RANGE.endInclusive.toInt(), slider("radiusPx").maximum)
        assertEquals(25, slider("intensity").minimum)
        assertEquals(400, slider("intensity").maximum)
        assertEquals(50, slider("brightness").value)
        assertEquals(0, slider("brightness").minimum)
        assertEquals(100, slider("brightness").maximum)
        for ((name, percent) in mapOf("editorGlowStrength" to 75, "uiGlowStrength" to 50, "iconGlowStrength" to 50)) {
            assertEquals(percent, slider(name).value)
            assertEquals(0, slider(name).minimum)
            assertEquals(100, slider(name).maximum)
            assertEquals("$percent%", strengthReadout(name).text)
        }
        assertFalse(checkBox("performanceMode").isSelected)
        assertEquals(GlowSettings.State(), previewState())
    }

    fun `test untouched fractional sliders survive apply and unrelated edits`() {
        val live = GlowSettings.State(radiusPx = 6.4f, intensity = 1.234f, brightness = 0.456f,
            editorGlowStrength = 0.789f, uiGlowStrength = 0.234f, iconGlowStrength = 0.567f)
        settings.loadState(live)
        configurable.reset()
        assertFalse(configurable.isModified())

        configurable.apply()
        assertEquals(live, settings.state)
        assertEquals(live, previewState())
        checkBox("performanceMode").isSelected = true
        configurable.apply()
        assertEquals(live.copy(performanceMode = true), settings.state)
        assertFalse(configurable.isModified())
    }

    fun `test only edited sliders quantize saved values and reset restores precise preview`() {
        val live = GlowSettings.State(radiusPx = 6.4f, intensity = 1.234f, brightness = 0.456f,
            editorGlowStrength = 0.789f, uiGlowStrength = 0.234f, iconGlowStrength = 0.567f)
        settings.loadState(live)
        configurable.reset()
        slider("brightness").value = 37
        assertEquals(live.copy(brightness = 0.37f), previewState())
        configurable.apply()
        assertEquals(live.copy(brightness = 0.37f), settings.state)
        slider("radiusPx").value = 9
        configurable.reset()
        assertEquals(settings.state, previewState())
        assertFalse(configurable.isModified())
    }

    fun `test apply commits the draft once and reset discards edits`() {
        checkBox().isSelected = false
        slider("radiusPx").value = 10
        slider("intensity").value = 150
        slider("brightness").value = 45
        assertTrue(configurable.isModified())
        assertTrue("nothing applied before Apply", applied.isEmpty())

        configurable.apply()

        assertEquals(listOf(GlowSettings.State(enabled = false, radiusPx = 10f, intensity = 1.5f, brightness = 0.45f)), applied)
        assertEquals(GlowSettings.State(enabled = false, radiusPx = 10f, intensity = 1.5f, brightness = 0.45f), settings.state)
        assertFalse(configurable.isModified())

        slider("radiusPx").value = 2
        slider("brightness").value = 0
        assertTrue(configurable.isModified())
        configurable.reset()
        assertFalse(configurable.isModified())
        assertEquals(10, slider("radiusPx").value)
        assertEquals(45, slider("brightness").value)
        assertTrue(descendants(component).filterIsInstance<JLabel>().any { it.text == "45%" })
        assertEquals(1, applied.size)
    }

    fun `test target choices are independent drafts and survive apply reset and reopening`() {
        for (target in listOf("editorText", "uiText", "icons")) checkBox(target).isSelected = false
        assertTrue(configurable.isModified())
        assertEquals(GlowSettings.State(), settings.state)
        configurable.apply()
        assertEquals(GlowSettings.State(editorText = false, uiText = false, icons = false), settings.state)
        assertFalse(configurable.isModified())
        checkBox("uiText").isSelected = true
        configurable.reset()
        assertFalse(checkBox("uiText").isSelected)
        configurable.disposeUIResources()
        component = configurable.createComponent()
        for (target in listOf("editorText", "uiText", "icons")) assertFalse(checkBox(target).isSelected)
        assertFalse(configurable.isModified())
    }

    fun `test style is an independent draft and survives apply reset and reopening`() {
        settings.loadState(GlowSettings.State(synthwaveStyle = false))
        configurable.reset()
        checkBox("synthwaveStyle").isSelected = true
        assertTrue(configurable.isModified())
        assertEquals(GlowSettings.State(synthwaveStyle = false), settings.state)
        assertTrue(applied.isEmpty())
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertFalse(checkBox("synthwaveStyle").isSelected)
        assertFalse(configurable.isModified())

        checkBox("synthwaveStyle").isSelected = true
        configurable.apply()
        val styled = GlowSettings.State(synthwaveStyle = true)
        assertEquals(listOf(styled), applied)
        assertEquals(styled, settings.state)
        assertFalse(configurable.isModified())

        checkBox("synthwaveStyle").isSelected = false
        assertTrue(configurable.isModified())
        configurable.reset()
        assertTrue(checkBox("synthwaveStyle").isSelected)
        assertFalse(configurable.isModified())
        checkBox("synthwaveStyle").isSelected = false
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertTrue(checkBox("synthwaveStyle").isSelected)
        assertEquals(listOf(styled), applied)
        assertFalse(configurable.isModified())

        checkBox("synthwaveStyle").isSelected = false
        configurable.apply()
        assertEquals(listOf(styled, GlowSettings.State(synthwaveStyle = false)), applied)
        assertEquals(GlowSettings.State(synthwaveStyle = false), settings.state)
        assertFalse(configurable.isModified())
    }

    fun `test reset reloads the live style choice`() {
        for (style in listOf(true, false)) {
            settings.loadState(GlowSettings.State(synthwaveStyle = style))
            configurable.reset()
            assertEquals(style, checkBox("synthwaveStyle").isSelected)
            assertFalse(configurable.isModified())
            assertTrue(applied.isEmpty())
        }
    }

    fun `test brightness endpoints are valid and discarding the page never applies them`() {
        for (percent in listOf(0, 100)) {
            slider("brightness").value = percent
            configurable.apply()
            assertEquals(percent / 100f, settings.state.brightness)
            assertFalse(configurable.isModified())
        }
        slider("brightness").value = 45
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertEquals(100, slider("brightness").value)
        assertEquals(2, applied.size)
        assertFalse(configurable.isModified())
    }

    fun `test strengths and performance mode are isolated drafts through apply reset and discard`() {
        slider("editorGlowStrength").value = 0
        slider("uiGlowStrength").value = 25
        slider("iconGlowStrength").value = 65
        checkBox("performanceMode").isSelected = true
        val expected = GlowSettings.State(editorGlowStrength = 0f, uiGlowStrength = 0.25f, iconGlowStrength = 0.65f,
            performanceMode = true)
        assertEquals(expected, previewState())
        assertEquals("0%", strengthReadout("editorGlowStrength").text)
        assertEquals("25%", strengthReadout("uiGlowStrength").text)
        assertEquals("65%", strengthReadout("iconGlowStrength").text)
        assertTrue(configurable.isModified())
        assertEquals(GlowSettings.State(), settings.state)
        assertTrue(applied.isEmpty())

        configurable.apply()
        assertEquals(listOf(expected), applied)
        assertEquals(expected, settings.state)
        assertFalse(configurable.isModified())
        slider("editorGlowStrength").value = 100
        slider("uiGlowStrength").value = 0
        slider("iconGlowStrength").value = 100
        checkBox("performanceMode").isSelected = false
        configurable.reset()
        assertEquals(expected, previewState())
        assertEquals("0%", strengthReadout("editorGlowStrength").text)
        assertEquals("25%", strengthReadout("uiGlowStrength").text)
        assertEquals("65%", strengthReadout("iconGlowStrength").text)
        assertFalse(configurable.isModified())

        slider("iconGlowStrength").value = 0
        checkBox("performanceMode").isSelected = false
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertEquals(expected, previewState())
        assertEquals(expected, settings.state)
        assertEquals(listOf(expected), applied)
        assertFalse(configurable.isModified())
    }

    fun `test strength endpoints apply without changing global brightness or targets`() {
        for (percent in listOf(0, 100)) {
            for (name in listOf("editorGlowStrength", "uiGlowStrength", "iconGlowStrength")) slider(name).value = percent
            configurable.apply()
            assertEquals(GlowSettings.State(editorGlowStrength = percent / 100f, uiGlowStrength = percent / 100f,
                iconGlowStrength = percent / 100f), settings.state)
            assertFalse(configurable.isModified())
        }
        assertEquals(2, applied.size)
    }

    fun `test choosing a preset immediately updates the draft without changing live settings`() {
        assertEquals(GlowPreset.entries.toList(), (0 until presets().itemCount).map { presets().getItemAt(it) })
        for (preset in GlowPreset.entries) {
            presets().selectedItem = preset
            assertEquals(preset.createState(), previewState())
            assertEquals(GlowSettings.State(), settings.state)
            assertEquals(preset.createState() != settings.state, configurable.isModified())
            assertTrue(applied.isEmpty())
        }
    }

    fun `test clearing the preset selection leaves settings unchanged`() {
        presets().selectedIndex = -1
        assertEquals(GlowSettings.State(), previewState())
        assertEquals(GlowSettings.State(), settings.state)
        assertFalse(configurable.isModified())
        assertTrue(applied.isEmpty())
    }

    fun `test reselecting the same preset restores its values after customization`() {
        presets().selectedItem = GlowPreset.FOCUS
        slider("brightness").value = 37
        checkBox("uiText").isSelected = true
        assertTrue(previewState() != GlowPreset.FOCUS.createState())
        presets().selectedItem = GlowPreset.FOCUS
        assertEquals(GlowPreset.FOCUS.createState(), previewState())
        assertEquals("45%", strengthReadout("brightness").text)
        assertFalse(checkBox("uiText").isSelected)
        assertEquals(GlowSettings.State(), settings.state)
        assertTrue(configurable.isModified())
        assertTrue(applied.isEmpty())
    }

    fun `test every selected preset applies once and selecting the live preset is unmodified`() {
        for (preset in GlowPreset.entries) {
            presets().selectedItem = preset
            assertEquals(preset.createState(), previewState())
            configurable.apply()
            assertEquals(preset.createState(), settings.state)
            assertEquals(preset.createState(), applied.last())
            assertNull(presets().selectedItem)
            assertFalse(configurable.isModified())
            presets().selectedItem = preset
            assertEquals(preset.createState(), previewState())
            assertFalse(configurable.isModified())
        }
        assertEquals(GlowPreset.entries.map { it.createState() }, applied)
    }

    fun `test preset selection replaces only the draft and reset restores live defaults`() {
        for (preset in GlowPreset.entries) {
            slider("radiusPx").value = 16
            presets().selectedItem = preset
            assertEquals(preset.createState(), previewState())
            assertEquals(GlowSettings.State(), settings.state)
            assertTrue(configurable.isModified())
            assertTrue(applied.isEmpty())
            configurable.reset()
            assertNull(presets().selectedItem)
            assertEquals(GlowSettings.State(), previewState())
            assertFalse(configurable.isModified())
        }
    }

    fun `test customized preset applies once without saving preset identity and discards later presets`() {
        presets().selectedItem = GlowPreset.FOCUS
        slider("brightness").value = 37
        slider("uiGlowStrength").value = 20
        checkBox("uiText").isSelected = true
        val customized = GlowPreset.FOCUS.createState().copy(brightness = 0.37f, uiGlowStrength = 0.2f, uiText = true)
        assertEquals(customized, previewState())
        configurable.apply()
        assertEquals(listOf(customized), applied)
        assertEquals(customized, settings.state)
        assertNull(presets().selectedItem)
        assertFalse(configurable.isModified())

        presets().selectedItem = GlowPreset.NEON
        assertEquals(GlowPreset.NEON.createState(), previewState())
        configurable.reset()
        assertEquals(customized, previewState())
        assertNull(presets().selectedItem)
        assertFalse(configurable.isModified())
        presets().selectedItem = GlowPreset.NEON
        assertEquals(GlowPreset.NEON.createState(), previewState())
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertEquals(customized, previewState())
        assertNull(presets().selectedItem)
        assertEquals(listOf(customized), applied)
        assertFalse(configurable.isModified())
    }

    fun `test preview reads current controls without committing or hiding modifications`() {
        assertFalse(checkBox("showPreview").isSelected)
        assertFalse(preview.isVisible)
        assertEquals(NeonGlowBundle.message("settings.preview.accessibleName"), preview.accessibleContext.accessibleName)
        checkBox("showPreview").isSelected = true
        assertTrue(preview.isVisible)
        assertEquals(GlowSettings.State(), previewState())
        assertFalse(configurable.isModified())
        assertTrue(applied.isEmpty())

        checkBox().isSelected = false
        checkBox("editorText").isSelected = false
        checkBox("uiText").isSelected = false
        checkBox("icons").isSelected = false
        checkBox("synthwaveStyle").isSelected = true
        checkBox("performanceMode").isSelected = true
        slider("brightness").value = 45
        slider("radiusPx").value = 9
        slider("intensity").value = 150
        slider("editorGlowStrength").value = 30
        slider("uiGlowStrength").value = 20
        slider("iconGlowStrength").value = 10
        val expected = GlowSettings.State(enabled = false, editorText = false, uiText = false, icons = false,
            synthwaveStyle = true, performanceMode = true, brightness = 0.45f, radiusPx = 9f, intensity = 1.5f,
            editorGlowStrength = 0.3f, uiGlowStrength = 0.2f, iconGlowStrength = 0.1f)
        repeat(3) {
            assertEquals(expected, previewState())
            assertTrue(configurable.isModified())
            assertEquals(GlowSettings.State(), settings.state)
            assertTrue(applied.isEmpty())
        }
        val snapshot = previewState()
        snapshot.enabled = true
        snapshot.editorGlowStrength = 1f
        assertEquals(expected, previewState())
        checkBox("showPreview").isSelected = false
        assertFalse(preview.isVisible)
        assertTrue(configurable.isModified())
        configurable.apply()
        assertEquals(listOf(expected), applied)
        assertEquals(expected, settings.state)
        assertFalse(configurable.isModified())
        slider("brightness").value = 0
        assertEquals(0f, previewState().brightness)
        configurable.reset()
        assertEquals(expected, previewState())
        assertFalse(configurable.isModified())
    }

    fun `test reset reloads live strengths and performance mode without selecting a preset`() {
        val live = GlowSettings.State(editorGlowStrength = 0.12f, uiGlowStrength = 0.34f, iconGlowStrength = 0.56f,
            performanceMode = true, brightness = 0.78f)
        settings.loadState(live)
        configurable.reset()
        assertEquals(live, previewState())
        assertEquals("12%", strengthReadout("editorGlowStrength").text)
        assertEquals("34%", strengthReadout("uiGlowStrength").text)
        assertEquals("56%", strengthReadout("iconGlowStrength").text)
        assertNull(presets().selectedItem)
        assertFalse(configurable.isModified())
        assertTrue(applied.isEmpty())
    }

    fun `test regular text switch previews drafts applies resets and discards without changing other targets`() {
        assertFalse(checkBox("regularText").isSelected)
        checkBox("regularText").isSelected = true
        assertEquals(GlowSettings.State(regularText = true), previewState())
        assertTrue(configurable.isModified())
        assertEquals(GlowSettings.State(), settings.state)
        assertTrue(applied.isEmpty())
        configurable.reset()
        assertFalse(checkBox("regularText").isSelected)
        assertFalse(configurable.isModified())

        checkBox("synthwaveStyle").isSelected = true
        checkBox("regularText").isSelected = true
        val expected = GlowSettings.State(synthwaveStyle = true, regularText = true)
        configurable.apply()
        assertEquals(listOf(expected), applied)
        assertEquals(expected, settings.state)
        assertEquals(expected, previewState())
        assertFalse(configurable.isModified())

        checkBox("regularText").isSelected = false
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertTrue(checkBox("regularText").isSelected)
        assertEquals(expected, previewState())
        assertFalse(configurable.isModified())
    }

    private fun presets(): JComboBox<*> = descendants(component).filterIsInstance<JComboBox<*>>().single { it.name == "preset" }

    private fun strengthReadout(name: String): JLabel =
        descendants(component).filterIsInstance<JLabel>().single { it.name == "${name}Value" }

    private fun checkBox(name: String = "enabled"): JCheckBox =
        descendants(component).filterIsInstance<JCheckBox>().single { it.name == name }

    private fun slider(name: String): JSlider = descendants(component).filterIsInstance<JSlider>().single { it.name == name }

    private fun visibleWithinPage(control: Component): Boolean =
        generateSequence(control) { if (it === component) null else it.parent }.all { it.isVisible }

    private fun descendants(root: Component): Sequence<Component> = sequence {
        yield(root)
        if (root is Container) for (child in root.components) yieldAll(descendants(child))
    }
}
