package info.chrzanowski.idesynthwave.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.idesynthwave.SynthwaveBundle
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider

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
        assertEquals(SynthwaveBundle.message("settings.displayName"), configurable.displayName)
        assertEquals(SynthwaveBundle.message("settings.displayName"), GlowConfigurable().displayName)

        val labels = descendants(component).filterIsInstance<JLabel>().map { it.text }.toList()
        assertTrue(SynthwaveBundle.message("settings.radius") in labels)
        assertTrue(SynthwaveBundle.message("settings.intensity") in labels)
        assertEquals(SynthwaveBundle.message("settings.enabled"), checkBox().text)
        assertEquals(listOf("enabled", "radiusPx", "intensity"), descendants(component).mapNotNull { it.name }.toList())
    }

    fun `test controls show the current settings`() {
        assertTrue(checkBox().isSelected)
        assertEquals(GlowSettings.DEFAULT_RADIUS.toInt(), slider("radiusPx").value)
        assertEquals((GlowSettings.DEFAULT_INTENSITY * 100).toInt(), slider("intensity").value)
        assertEquals(GlowSettings.RADIUS_RANGE.start.toInt(), slider("radiusPx").minimum)
        assertEquals(GlowSettings.RADIUS_RANGE.endInclusive.toInt(), slider("radiusPx").maximum)
        assertEquals(25, slider("intensity").minimum)
        assertEquals(400, slider("intensity").maximum)
    }

    fun `test apply commits the draft once and reset discards edits`() {
        checkBox().isSelected = false
        slider("radiusPx").value = 10
        slider("intensity").value = 150
        assertTrue(configurable.isModified())
        assertTrue("nothing applied before Apply", applied.isEmpty())

        configurable.apply()

        assertEquals(listOf(GlowSettings.State(enabled = false, radiusPx = 10f, intensity = 1.5f)), applied)
        assertEquals(GlowSettings.State(enabled = false, radiusPx = 10f, intensity = 1.5f), settings.state)
        assertFalse(configurable.isModified())

        slider("radiusPx").value = 2
        assertTrue(configurable.isModified())
        configurable.reset()
        assertFalse(configurable.isModified())
        assertEquals(10, slider("radiusPx").value)
        assertEquals(1, applied.size)
    }

    private fun checkBox(): JCheckBox = descendants(component).filterIsInstance<JCheckBox>().single()

    private fun slider(name: String): JSlider = descendants(component).filterIsInstance<JSlider>().single { it.name == name }

    private fun descendants(root: Component): Sequence<Component> = sequence {
        yield(root)
        if (root is Container) for (child in root.components) yieldAll(descendants(child))
    }
}
