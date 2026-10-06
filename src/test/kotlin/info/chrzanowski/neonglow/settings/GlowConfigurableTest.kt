package info.chrzanowski.neonglow.settings

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.neonglow.NeonGlowBundle
import info.chrzanowski.neonglow.editor.EditorGlow
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.math.roundToInt

class GlowConfigurableTest : BasePlatformTestCase() {

    private lateinit var settings: GlowSettings
    private lateinit var configurable: GlowConfigurable
    private lateinit var component: JComponent
    private val applied = mutableListOf<GlowSettings.State>()

    override fun setUp() {
        super.setUp()
        settings = GlowSettings()
        applied.clear()
        configurable = GlowConfigurable(settings) {
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

        val labels = descendants(component).filterIsInstance<JLabel>().map { it.text }.toList()
        assertTrue(NeonGlowBundle.message("settings.radius") in labels)
        assertTrue(NeonGlowBundle.message("settings.intensity") in labels)
        assertTrue(NeonGlowBundle.message("settings.brightness") in labels)
        for (key in listOf("settings.strength.editor", "settings.strength.ui", "settings.strength.icons")) {
            assertTrue(NeonGlowBundle.message(key) in labels)
        }
        assertEquals(NeonGlowBundle.message("settings.enabled"), checkBox().text)
        assertEquals(NeonGlowBundle.message("settings.regularText"), checkBox("regularText").text)
        assertEquals(NeonGlowBundle.message("settings.synthwaveStyle"), checkBox("synthwaveStyle").text)
        assertEquals(NeonGlowBundle.message("settings.performanceMode"), checkBox("performanceMode").text)
        assertEquals(listOf("enabled", "editorText", "uiText", "regularText", "icons", "brightness",
            "editorGlowStrength", "uiGlowStrength", "iconGlowStrength", "synthwaveStyle",
            "radiusPx", "intensity", "performanceMode"), descendants(component).filter {
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

    fun `test neon styling and every strength have separate descriptions`() {
        val descriptions = descendants(component).mapNotNull {
            when (it) {
                is JLabel -> it.text
                is JTextComponent -> it.document.getText(0, it.document.length)
                else -> null
            }
        }.map { it.replace(Regex("\\s+"), " ").trim() }.toList()
        assertEquals("Neon text styling", checkBox("synthwaveStyle").text)
        for (key in listOf("settings.synthwaveStyle", "settings.strength.editor", "settings.strength.ui",
            "settings.strength.icons")) {
            val description = NeonGlowBundle.message("$key.comment")
            assertTrue("$key must have a separate description", descriptions.any { description in it })
        }
        for (target in listOf("regularText", "icons")) {
            assertTrue(NeonGlowBundle.message("settings.$target.comment").contains("Neon text styling"))
        }
        assertFalse(configurable.isModified())
        assertEquals(GlowSettings.State(), settings.state)
        assertTrue(applied.isEmpty())
    }

    fun `test everyday controls are visible and fine tuning is initially collapsed`() {
        for (name in listOf("enabled", "editorText", "uiText", "regularText", "icons", "performanceMode")) {
            assertTrue("$name must be visible", visibleWithinPage(checkBox(name)))
        }
        assertTrue(visibleWithinPage(slider("brightness")))
        for (name in listOf("editorGlowStrength", "uiGlowStrength", "iconGlowStrength", "radiusPx", "intensity")) {
            assertFalse("$name must start collapsed", visibleWithinPage(slider(name)))
        }
        assertFalse(visibleWithinPage(checkBox("synthwaveStyle")))
        assertFalse(configurable.isModified())
    }

    fun `test every slider exposes its name and numeric value through edits and reset`() {
        val labels = mapOf("brightness" to "settings.brightness", "radiusPx" to "settings.radius",
            "intensity" to "settings.intensity", "editorGlowStrength" to "settings.strength.editor",
            "uiGlowStrength" to "settings.strength.ui", "iconGlowStrength" to "settings.strength.icons")
        for ((name, key) in labels) {
            val control = slider(name)
            val readout = numericInput(name)
            assertEquals(NeonGlowBundle.message(key), control.accessibleContext.accessibleName)
            val unitLabel = descendants(component).filterIsInstance<JLabel>().single { it.name == "${name}Unit" }
            assertSame(readout, unitLabel.labelFor)
            assertEquals(if (name == "radiusPx") "px" else "%", unitLabel.text)
            control.value = control.minimum
            assertEquals(control.value.toString(), readout.text)
        }
        slider("radiusPx").value = 6
        slider("intensity").value = 100
        slider("brightness").value = 45
        assertEquals("6", numericInput("radiusPx").text)
        assertEquals("100", numericInput("intensity").text)
        assertEquals("45", numericInput("brightness").text)
        configurable.reset()
        assertEquals("6", numericInput("radiusPx").text)
        assertEquals("200", numericInput("intensity").text)
        assertEquals("50", numericInput("brightness").text)
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
            assertEquals(percent.toString(), numericInput(name).text)
        }
        assertFalse(checkBox("performanceMode").isSelected)
        assertControlsMatch(GlowSettings.State())
    }

    fun `test untouched fractional sliders survive apply and unrelated edits`() {
        val live = GlowSettings.State(radiusPx = 6.4f, intensity = 1.234f, brightness = 0.456f,
            editorGlowStrength = 0.789f, uiGlowStrength = 0.234f, iconGlowStrength = 0.567f)
        settings.loadState(live)
        configurable.reset()
        assertFalse(configurable.isModified())

        configurable.apply()
        assertEquals(live, settings.state)
        assertControlsMatch(live)
        checkBox("performanceMode").isSelected = true
        configurable.apply()
        assertEquals(live.copy(performanceMode = true), settings.state)
        assertFalse(configurable.isModified())
    }

    fun `test only edited sliders quantize saved values and reset restores controls`() {
        val live = GlowSettings.State(radiusPx = 6.4f, intensity = 1.234f, brightness = 0.456f,
            editorGlowStrength = 0.789f, uiGlowStrength = 0.234f, iconGlowStrength = 0.567f)
        settings.loadState(live)
        configurable.reset()
        slider("brightness").value = 37
        assertControlsMatch(live.copy(brightness = 0.37f))
        assertEquals(live, settings.state)
        assertTrue(applied.isEmpty())
        configurable.apply()
        assertEquals(live.copy(brightness = 0.37f), settings.state)
        slider("radiusPx").value = 9
        configurable.reset()
        assertControlsMatch(settings.state)
        assertEquals(live.copy(brightness = 0.37f), settings.state)
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
        assertEquals("45", numericInput("brightness").text)
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
        assertControlsMatch(expected)
        assertEquals("0", numericInput("editorGlowStrength").text)
        assertEquals("25", numericInput("uiGlowStrength").text)
        assertEquals("65", numericInput("iconGlowStrength").text)
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
        assertControlsMatch(expected)
        assertEquals("0", numericInput("editorGlowStrength").text)
        assertEquals("25", numericInput("uiGlowStrength").text)
        assertEquals("65", numericInput("iconGlowStrength").text)
        assertFalse(configurable.isModified())

        slider("iconGlowStrength").value = 0
        checkBox("performanceMode").isSelected = false
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertControlsMatch(expected)
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

    fun `test all control edits remain isolated until apply and reset discards later edits`() {
        assertControlsMatch(GlowSettings.State())
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
            assertControlsMatch(expected)
            assertTrue(configurable.isModified())
            assertEquals(GlowSettings.State(), settings.state)
            assertTrue(applied.isEmpty())
        }
        configurable.apply()
        assertEquals(listOf(expected), applied)
        assertEquals(expected, settings.state)
        assertFalse(configurable.isModified())
        slider("brightness").value = 0
        assertControlsMatch(expected.copy(brightness = 0f))
        assertEquals(expected, settings.state)
        assertTrue(configurable.isModified())
        configurable.reset()
        assertControlsMatch(expected)
        assertEquals(expected, settings.state)
        assertFalse(configurable.isModified())
    }

    fun `test reset reloads live strengths and performance mode without selecting a preset`() {
        val live = GlowSettings.State(editorGlowStrength = 0.12f, uiGlowStrength = 0.34f, iconGlowStrength = 0.56f,
            performanceMode = true, brightness = 0.78f)
        settings.loadState(live)
        configurable.reset()
        assertControlsMatch(live)
        assertEquals("12", numericInput("editorGlowStrength").text)
        assertEquals("34", numericInput("uiGlowStrength").text)
        assertEquals("56", numericInput("iconGlowStrength").text)
        assertFalse(configurable.isModified())
        assertTrue(applied.isEmpty())
    }

    fun `test regular text switch applies resets and discards drafts without changing other targets`() {
        assertFalse(checkBox("regularText").isSelected)
        checkBox("regularText").isSelected = true
        assertControlsMatch(GlowSettings.State(regularText = true))
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
        assertControlsMatch(expected)
        assertFalse(configurable.isModified())

        checkBox("regularText").isSelected = false
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertTrue(checkBox("regularText").isSelected)
        assertControlsMatch(expected)
        assertFalse(configurable.isModified())
    }

    fun `test page always shows a live preview without an extra toggle after reset and reopening`() {
        repeat(2) {
            assertFalse(descendants(component).any { it.name in listOf("showPreview", "glowPreview") })
            assertFalse(descendants(component).filterIsInstance<JCheckBox>().any { it.text == "Show draft preview" })
            assertTrue(visibleWithinPage(editorPreview()))
            assertEquals(settings.state, editorPreview().previewState)
            assertEquals(GlowSettings.State(), settings.state)
            assertTrue(applied.isEmpty())
            assertFalse(configurable.isModified())
            configurable.reset()
            configurable.disposeUIResources()
            component = configurable.createComponent()
        }
    }

    fun `test page has no glow preset selector and preserves saved settings after reset and reopening`() {
        val live = GlowSettings.State(enabled = false, uiText = false, icons = false, radiusPx = 6.4f,
            intensity = 1.234f, brightness = 0.456f, editorGlowStrength = 0.789f, uiGlowStrength = 0.234f,
            iconGlowStrength = 0.567f, performanceMode = true, regularText = true)
        settings.loadState(live)
        configurable.reset()
        repeat(2) {
            assertFalse(descendants(component).any { it.name in listOf("preset", "usePreset") })
            assertFalse(descendants(component).any { it is JComboBox<*> })
            assertControlsMatch(live)
            assertEquals(live, settings.state)
            assertFalse(configurable.isModified())
            assertTrue(applied.isEmpty())
            slider("brightness").value = 37
            checkBox("enabled").isSelected = true
            assertTrue(configurable.isModified())
            assertEquals(live, settings.state)
            configurable.reset()
            assertControlsMatch(live)
            configurable.disposeUIResources()
            component = configurable.createComponent()
        }
        configurable.apply()
        assertEquals(listOf(live), applied)
        assertEquals(live, settings.state)
        assertControlsMatch(live)
        assertFalse(configurable.isModified())
    }

    fun `test support comment is displayed on the settings page`() {
        val labels = descendants(component).filterIsInstance<JLabel>().map { it.text }.toList()
        assertTrue("Support title must be present in settings page", NeonGlowBundle.message("settings.support.title") in labels)

        val descriptions = descendants(component).mapNotNull {
            when (it) {
                is JLabel -> it.text
                is JTextComponent -> it.document.getText(0, it.document.length)
                else -> null
            }
        }.map { it.replace(Regex("\\s+"), " ").trim() }.toList()
        val supportComment = NeonGlowBundle.message("settings.support.comment")
        assertTrue("Support comment must be present in settings page", descriptions.any { supportComment in it || it.contains("GitHub Sponsors") || it.contains("Ko-fi") })

        val allComponents = descendants(component).toList()
        val supportIndex = allComponents.indexOfFirst {
            (it is JLabel && it.text == NeonGlowBundle.message("settings.support.title")) ||
            (it is JTextComponent && (it.document.getText(0, it.document.length).contains("GitHub Sponsors") || it.document.getText(0, it.document.length).contains("Ko-fi")))
        }
        val enabledIndex = allComponents.indexOfFirst { it is JCheckBox && it.name == "enabled" }
        assertTrue("Support section must appear before enabled checkbox", supportIndex != -1 && enabledIndex != -1 && supportIndex < enabledIndex)
    }

    fun `test reset to defaults updates all controls and applies only when requested`() {
        val live = GlowSettings.State(
            enabled = false, radiusPx = 12f, intensity = 3f, editorText = false, uiText = false, icons = false,
            brightness = 0.9f, synthwaveStyle = false, editorGlowStrength = 0.2f, uiGlowStrength = 0.3f,
            iconGlowStrength = 0.4f, performanceMode = true, regularText = true, initialThemePreserved = true,
        )
        settings.loadState(live)
        configurable.reset()
        slider("brightness").value = 17
        checkBox("enabled").isSelected = true

        val button = resetDefaultsButton()
        assertEquals(NeonGlowBundle.message("settings.resetToDefaults"), button.text)
        assertTrue(visibleWithinPage(button))
        button.doClick()

        val defaults = GlowSettings.State(initialThemePreserved = true)
        assertControlsMatch(defaults)
        assertEquals("50", numericInput("brightness").text)
        assertEquals("6", numericInput("radiusPx").text)
        assertEquals("200", numericInput("intensity").text)
        assertEquals("75", numericInput("editorGlowStrength").text)
        assertEquals("50", numericInput("uiGlowStrength").text)
        assertEquals("50", numericInput("iconGlowStrength").text)
        assertEquals(live, settings.state)
        assertTrue(applied.isEmpty())
        assertTrue(configurable.isModified())

        configurable.apply()
        assertEquals(listOf(defaults), applied)
        assertEquals(defaults, settings.state)
        assertFalse(configurable.isModified())
    }

    fun `test reset to defaults can be discarded by reset or disposal`() {
        val live = GlowSettings.State(enabled = false, brightness = 0.8f, initialThemePreserved = true)
        settings.loadState(live)
        configurable.reset()

        resetDefaultsButton().doClick()
        configurable.reset()
        assertControlsMatch(live)
        assertFalse(configurable.isModified())

        resetDefaultsButton().doClick()
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertControlsMatch(live)
        assertFalse(configurable.isModified())
        assertEquals(live, settings.state)
        assertTrue(applied.isEmpty())
    }

    fun `test reset to defaults is unmodified when defaults are already saved`() {
        for (initialThemePreserved in listOf(false, true)) {
            val defaults = GlowSettings.State(initialThemePreserved = initialThemePreserved)
            settings.loadState(defaults)
            configurable.reset()
            resetDefaultsButton().doClick()
            assertFalse(configurable.isModified())

            slider("brightness").value = 90
            checkBox("icons").isSelected = false
            resetDefaultsButton().doClick()
            assertControlsMatch(defaults)
            assertFalse(configurable.isModified())
            assertEquals(defaults, settings.state)
            assertTrue(applied.isEmpty())
        }
    }

    fun `test numeric inputs synchronize every slider and apply only on request`() {
        val values = mapOf("brightness" to 37, "radiusPx" to 9, "intensity" to 123,
            "editorGlowStrength" to 61, "uiGlowStrength" to 28, "iconGlowStrength" to 84)
        for ((name, value) in values) {
            val input = numericInput(name)
            assertTrue(input.isEditable)
            assertEquals(slider(name).value.toString(), input.text)
            assertEquals(visibleWithinPage(slider(name)), visibleWithinPage(input))
            assertTrue(input.accessibleContext.accessibleName.contains(slider(name).accessibleContext.accessibleName))
            input.text = value.toString()
            assertEquals(name, value, slider(name).value)
        }
        assertTrue(configurable.isModified())
        assertEquals(GlowSettings.State(), settings.state)
        assertTrue(applied.isEmpty())

        configurable.apply()
        val expected = GlowSettings.State(brightness = 0.37f, radiusPx = 9f, intensity = 1.23f,
            editorGlowStrength = 0.61f, uiGlowStrength = 0.28f, iconGlowStrength = 0.84f)
        assertEquals(listOf(expected), applied)
        assertEquals(expected, settings.state)
        assertFalse(configurable.isModified())

        for (name in values.keys) {
            slider(name).value = slider(name).minimum
            assertEquals(slider(name).minimum.toString(), numericInput(name).text)
        }
        configurable.reset()
        assertControlsMatch(expected)
        for ((name, value) in values) assertEquals(value.toString(), numericInput(name).text)
        assertFalse(configurable.isModified())
    }

    fun `test numeric inputs accept endpoints and reject invalid values without applying any settings`() {
        for (name in listOf("brightness", "radiusPx", "intensity", "editorGlowStrength", "uiGlowStrength", "iconGlowStrength")) {
            val control = slider(name)
            val input = numericInput(name)
            for (value in listOf(control.minimum, control.maximum)) {
                input.text = value.toString()
                assertEquals(value, control.value)
                configurable.apply()
                assertFalse(configurable.isModified())
            }
            val saved = settings.state.copy()
            val appliedCount = applied.size
            checkBox("enabled").isSelected = !saved.enabled
            for (text in listOf("", "abc", "1.5", "999999999999999999999", (control.minimum - 1).toString(), (control.maximum + 1).toString())) {
                input.text = text
                assertEquals(text, input.text)
                assertEquals(control.maximum, control.value)
                assertTrue(configurable.isModified())
                try {
                    configurable.apply()
                    fail("$name must reject '$text'")
                } catch (expected: ConfigurationException) {
                    assertFalse(expected.localizedMessage.isNullOrBlank())
                }
                assertEquals(saved, settings.state)
                assertEquals(appliedCount, applied.size)
            }
            configurable.reset()
            assertEquals(control.maximum.toString(), input.text)
            assertFalse(configurable.isModified())
            input.text = "invalid"
            control.value = control.minimum
            assertEquals(control.minimum.toString(), input.text)
            configurable.reset()
        }
    }

    fun `test numeric drafts are reset to defaults and discarded when the page closes`() {
        val live = GlowSettings.State(brightness = 0.8f, radiusPx = 12f, intensity = 3f, initialThemePreserved = true)
        settings.loadState(live)
        configurable.reset()
        numericInput("brightness").text = "42"
        numericInput("radiusPx").text = ""
        resetDefaultsButton().doClick()
        for (name in listOf("brightness", "radiusPx", "intensity", "editorGlowStrength", "uiGlowStrength", "iconGlowStrength")) {
            assertEquals(slider(name).value.toString(), numericInput(name).text)
        }
        assertControlsMatch(GlowSettings.State(initialThemePreserved = true))
        assertEquals(live, settings.state)
        assertTrue(applied.isEmpty())

        configurable.reset()
        assertEquals("80", numericInput("brightness").text)
        assertEquals("12", numericInput("radiusPx").text)
        numericInput("brightness").text = "42"
        configurable.disposeUIResources()
        component = configurable.createComponent()
        assertEquals("80", numericInput("brightness").text)
        assertFalse(configurable.isModified())
        assertEquals(live, settings.state)
        assertTrue(applied.isEmpty())
    }

    fun `test manual edits preserve untouched fractional settings`() {
        val live = GlowSettings.State(radiusPx = 6.4f, intensity = 1.234f, brightness = 0.456f,
            editorGlowStrength = 0.789f, uiGlowStrength = 0.234f, iconGlowStrength = 0.567f)
        settings.loadState(live)
        configurable.reset()
        assertFalse(configurable.isModified())
        numericInput("brightness").text = "37"
        configurable.apply()
        assertEquals(live.copy(brightness = 0.37f), settings.state)
        assertFalse(configurable.isModified())
    }

    fun `test live preview follows sliders and checkboxes without changing saved settings`() {
        val preview = editorPreview()
        val saved = settings.state.copy()
        val globalScheme = EditorColorsManager.getInstance().globalScheme
        val values = mapOf("brightness" to 37, "radiusPx" to 9, "intensity" to 123,
            "editorGlowStrength" to 61, "uiGlowStrength" to 28, "iconGlowStrength" to 84)
        for ((name, value) in values) slider(name).value = value
        for (name in listOf("enabled", "editorText", "uiText", "regularText", "icons", "synthwaveStyle", "performanceMode")) {
            checkBox(name).isSelected = !checkBox(name).isSelected
        }
        val expected = saved.copy(enabled = false, editorText = false, uiText = false, regularText = true,
            icons = false, synthwaveStyle = false, performanceMode = true, brightness = 0.37f, radiusPx = 9f,
            intensity = 1.23f, editorGlowStrength = 0.61f, uiGlowStrength = 0.28f, iconGlowStrength = 0.84f)
        assertEquals(expected, preview.previewState)
        assertEquals(saved, settings.state)
        assertTrue(applied.isEmpty())
        assertTrue(configurable.isModified())
        assertSame(globalScheme, EditorColorsManager.getInstance().globalScheme)
        configurable.apply()
        assertEquals(listOf(expected), applied)
        assertEquals(expected, preview.previewState)
        assertFalse(configurable.isModified())
    }

    fun `test live numeric preview retains valid values while input is incomplete or invalid`() {
        numericInput("brightness").text = "37"
        numericInput("radiusPx").text = "9"
        val expected = GlowSettings.State(brightness = 0.37f, radiusPx = 9f)
        assertEquals(expected, editorPreview().previewState)
        for (text in listOf("", "abc", "999", "1.5")) {
            numericInput("radiusPx").text = text
            assertEquals(expected, editorPreview().previewState)
        }
        try {
            configurable.apply()
            fail("Invalid input must not apply the preview")
        } catch (_: ConfigurationException) {
            assertEquals(GlowSettings.State(), settings.state)
            assertTrue(applied.isEmpty())
        }
        configurable.reset()
        assertEquals(GlowSettings.State(), editorPreview().previewState)
        assertFalse(configurable.isModified())
    }

    fun `test preview reset preserves fractions and defaults remain unsaved`() {
        val live = GlowSettings.State(brightness = 0.456f, radiusPx = 6.4f, intensity = 1.234f,
            editorGlowStrength = 0.789f, initialThemePreserved = true)
        settings.loadState(live)
        configurable.reset()
        assertEquals(live, editorPreview().previewState)
        numericInput("brightness").text = "37"
        assertEquals(live.copy(brightness = 0.37f), editorPreview().previewState)
        resetDefaultsButton().doClick()
        assertEquals(GlowSettings.State(initialThemePreserved = true), editorPreview().previewState)
        assertEquals(live, settings.state)
        assertTrue(applied.isEmpty())
        configurable.reset()
        assertEquals(live, editorPreview().previewState)
        assertFalse(configurable.isModified())
    }

    fun `test closing releases the preview editor and reopening discards the draft`() {
        val preview = editorPreview()
        val editor = preview.editor
        assertTrue(editor.isViewer)
        assertEquals(EditorKind.PREVIEW, editor.editorKind)
        assertTrue(visibleWithinPage(preview))
        assertSame(preview, editorPreview())
        assertSame(component, configurable.createComponent())
        assertNull(EditorGlow.of(editor))
        assertTrue(EditorFactory.getInstance().allEditors.contains(editor))
        numericInput("brightness").text = "12"
        configurable.disposeUIResources()
        assertTrue(editor.isDisposed)
        assertFalse(EditorFactory.getInstance().allEditors.contains(editor))
        component = configurable.createComponent()
        assertNotSame(preview, editorPreview())
        assertEquals(settings.state, editorPreview().previewState)
        assertFalse(configurable.isModified())
        assertTrue(applied.isEmpty())
    }

    private fun editorPreview(): GlowEditorPreview = descendants(component).filterIsInstance<GlowEditorPreview>().single()

    private fun resetDefaultsButton(): JButton =
        descendants(component).filterIsInstance<JButton>().single { it.name == "resetToDefaults" }

    private fun assertControlsMatch(expected: GlowSettings.State) {
        for ((name, selected) in mapOf("enabled" to expected.enabled, "editorText" to expected.editorText,
            "uiText" to expected.uiText, "regularText" to expected.regularText, "icons" to expected.icons,
            "synthwaveStyle" to expected.synthwaveStyle, "performanceMode" to expected.performanceMode)) {
            assertEquals(name, selected, checkBox(name).isSelected)
        }
        for ((name, value) in mapOf("radiusPx" to expected.radiusPx, "brightness" to expected.brightness * 100f,
            "intensity" to expected.intensity * 100f, "editorGlowStrength" to expected.editorGlowStrength * 100f,
            "uiGlowStrength" to expected.uiGlowStrength * 100f, "iconGlowStrength" to expected.iconGlowStrength * 100f)) {
            assertEquals(name, value.roundToInt(), slider(name).value)
        }
    }

    private fun numericInput(name: String): JTextField =
        descendants(component).filterIsInstance<JTextField>().single { it.name == "${name}Value" }

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
