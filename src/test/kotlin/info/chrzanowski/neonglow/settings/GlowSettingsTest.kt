package info.chrzanowski.neonglow.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.render.GlyphKey
import org.jdom.Element
import java.awt.Rectangle

class GlowSettingsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        GlowSettings.getInstance().loadState(GlowSettings.State())
    }

    override fun tearDown() {
        try {
            // The application-level settings are shared with other test classes; leave the defaults behind.
            GlowSettings.getInstance().loadState(GlowSettings.State())
            GlowManager.getInstance().atlas.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test defaults`() {
        val state = GlowSettings().state
        assertTrue(state.enabled)
        assertTrue(state.editorText)
        assertTrue(state.uiText)
        assertTrue(state.icons)
        assertFalse(state.regularText)
        assertTrue(state.synthwaveStyle)
        assertEquals(6f, state.radiusPx)
        assertEquals(2f, state.intensity)
        assertEquals(0.5f, state.brightness)
        assertEquals(0.75f, state.editorGlowStrength)
        assertEquals(0.5f, state.uiGlowStrength)
        assertEquals(0.5f, state.iconGlowStrength)
        assertFalse(state.performanceMode)
        assertEquals(state, state.normalized())
    }

    fun `test state round-trips through XML`() {
        val original = GlowSettings.State(enabled = false, radiusPx = 9f, intensity = 1.5f,
            editorText = false, uiText = false, icons = false, brightness = 0.45f, synthwaveStyle = false,
            editorGlowStrength = 0f, uiGlowStrength = 0.25f, iconGlowStrength = 0.65f, performanceMode = true,
            regularText = true)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(original), GlowSettings.State::class.java)
        val settings = GlowSettings()
        settings.loadState(restored)

        assertEquals(original, restored)
        assertEquals(original, settings.state)
    }

    fun `test missing XML options use the current defaults`() {
        val settings = GlowSettings()
        settings.loadState(GlowSettings.State(enabled = false, brightness = 1f, intensity = 3f, synthwaveStyle = false,
            editorGlowStrength = 1f, uiGlowStrength = 1f, iconGlowStrength = 1f, regularText = true))
        settings.loadState(XmlSerializer.deserialize(Element("state"), GlowSettings.State::class.java))
        assertEquals(GlowSettings.State(), settings.state)
        assertFalse(settings.state.regularText)
        assertTrue(settings.state.synthwaveStyle)
    }

    fun `test existing XML without target choices preserves all-on behaviour`() {
        val xml = Element("state").addContent(Element("option").setAttribute("name", "radiusPx").setAttribute("value", "9.0"))
        val restored = XmlSerializer.deserialize(xml, GlowSettings.State::class.java)
        assertEquals(GlowSettings.State(radiusPx = 9f), restored)
    }

    fun `test regular text toggle preserves warm masks and all other settings`() {
        val manager = GlowManager.getInstance()
        val original = GlowSettings.State(synthwaveStyle = true, brightness = 0.45f)
        manager.applySettings(original)
        val mask = manager.atlas.get(key()) { outline }
        for (enabled in listOf(false, true)) {
            val expected = original.copy(regularText = enabled)
            manager.applySettings(expected)
            assertEquals(expected, GlowSettings.getInstance().state)
            assertSame(mask, manager.atlas.find(key()))
        }
    }

    fun `test existing XML preserves an explicit same-colour choice`() {
        val xml = Element("state")
            .addContent(Element("option").setAttribute("name", "brightness").setAttribute("value", "0.45"))
            .addContent(Element("option").setAttribute("name", "editorText").setAttribute("value", "false"))
            .addContent(Element("option").setAttribute("name", "synthwaveStyle").setAttribute("value", "false"))
        val settings = GlowSettings()
        settings.loadState(GlowSettings.State(synthwaveStyle = true))
        settings.loadState(XmlSerializer.deserialize(xml, GlowSettings.State::class.java))
        assertEquals(GlowSettings.State(editorText = false, brightness = 0.45f, synthwaveStyle = false), settings.state)
        assertFalse(settings.state.synthwaveStyle)
    }

    fun `test legacy XML preserves old choices without opting into presets or performance mode`() {
        val xml = Element("state")
            .addContent(Element("option").setAttribute("name", "enabled").setAttribute("value", "false"))
            .addContent(Element("option").setAttribute("name", "radiusPx").setAttribute("value", "9.0"))
            .addContent(Element("option").setAttribute("name", "intensity").setAttribute("value", "1.5"))
            .addContent(Element("option").setAttribute("name", "brightness").setAttribute("value", "0.45"))
            .addContent(Element("option").setAttribute("name", "synthwaveStyle").setAttribute("value", "true"))
            .addContent(Element("option").setAttribute("name", "editorText").setAttribute("value", "false"))
            .addContent(Element("option").setAttribute("name", "icons").setAttribute("value", "false"))
        val settings = GlowSettings()
        settings.loadState(GlowSettings.State(uiText = false, icons = false, brightness = 0.45f, intensity = 1f,
            editorGlowStrength = 1f, uiGlowStrength = 0f, iconGlowStrength = 0f, performanceMode = true,
            regularText = true))
        settings.loadState(XmlSerializer.deserialize(xml, GlowSettings.State::class.java))

        assertEquals(GlowSettings.State(enabled = false, radiusPx = 9f, intensity = 1.5f, brightness = 0.45f,
            synthwaveStyle = true, editorText = false, icons = false), settings.state)
        assertEquals(0.75f, settings.state.editorGlowStrength)
        assertEquals(0.5f, settings.state.uiGlowStrength)
        assertEquals(0.5f, settings.state.iconGlowStrength)
        assertFalse(settings.state.performanceMode)
    }

    fun `test strengths repair non-finite values loaded from XML`() {
        for (value in listOf("NaN", "Infinity", "-Infinity")) {
            val xml = Element("state")
            for (name in listOf("editorGlowStrength", "uiGlowStrength", "iconGlowStrength")) {
                xml.addContent(Element("option").setAttribute("name", name).setAttribute("value", value))
            }
            xml.addContent(Element("option").setAttribute("name", "performanceMode").setAttribute("value", "true"))
            val settings = GlowSettings()
            settings.loadState(XmlSerializer.deserialize(xml, GlowSettings.State::class.java))
            assertEquals(GlowSettings.State(performanceMode = true), settings.state)
        }
    }

    fun `test strengths clamp independently and preserve endpoints and performance mode`() {
        val settings = GlowSettings()
        val original = GlowSettings.State(editorGlowStrength = -2f, uiGlowStrength = 0.35f, iconGlowStrength = 2f,
            performanceMode = true)
        settings.loadState(original)
        assertEquals(original.copy(editorGlowStrength = 0f, iconGlowStrength = 1f), settings.state)
        assertEquals(-2f, original.editorGlowStrength)
        assertEquals(2f, original.iconGlowStrength)
        assertNotSame(original, settings.state)

        for ((value, expected) in listOf(-1f to 0f, 2f to 1f, 0f to 0f, 0.45f to 0.45f, 1f to 1f)) {
            settings.loadState(GlowSettings.State(editorGlowStrength = value, uiGlowStrength = value,
                iconGlowStrength = value, performanceMode = true))
            assertEquals(expected, settings.state.editorGlowStrength)
            assertEquals(expected, settings.state.uiGlowStrength)
            assertEquals(expected, settings.state.iconGlowStrength)
            assertTrue(settings.state.performanceMode)
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            settings.loadState(GlowSettings.State(editorGlowStrength = value, uiGlowStrength = value,
                iconGlowStrength = value, performanceMode = true))
            assertEquals(0.75f, settings.state.editorGlowStrength)
            assertEquals(0.5f, settings.state.uiGlowStrength)
            assertEquals(0.5f, settings.state.iconGlowStrength)
            assertTrue(settings.state.performanceMode)
        }
    }

    fun `test normalization preserves style choice`() {
        for (style in listOf(false, true)) {
            val state = GlowSettings.State(radiusPx = Float.NaN, intensity = 100f, brightness = -1f, synthwaveStyle = style)
            val settings = GlowSettings()
            settings.loadState(state)
            assertEquals(GlowSettings.State(intensity = 4f, brightness = 0f, synthwaveStyle = style), settings.state)
        }
    }

    fun `test loadState clamps and repairs the numbers`() {
        val settings = GlowSettings()
        settings.loadState(GlowSettings.State(radiusPx = 100f, intensity = -1f))
        assertEquals(GlowSettings.RADIUS_RANGE.endInclusive, settings.state.radiusPx)
        assertEquals(GlowSettings.INTENSITY_RANGE.start, settings.state.intensity)

        settings.loadState(GlowSettings.State(radiusPx = Float.NaN, intensity = Float.POSITIVE_INFINITY))
        assertEquals(GlowSettings.DEFAULT_RADIUS, settings.state.radiusPx)
        assertEquals(GlowSettings.DEFAULT_INTENSITY, settings.state.intensity)
    }

    fun `test brightness repairs non-finite values clamps outliers and preserves zero`() {
        val settings = GlowSettings()
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            settings.loadState(GlowSettings.State(brightness = value))
            assertEquals(GlowSettings.DEFAULT_BRIGHTNESS, settings.state.brightness)
        }
        for ((value, expected) in listOf(-2f to 0f, 2f to 1f, 0f to 0f, 0.45f to 0.45f, 1f to 1f)) {
            settings.loadState(GlowSettings.State(brightness = value))
            assertEquals(expected, settings.state.brightness)
        }
    }

    fun `test manager applies settings and clears the atlas`() {
        val manager = GlowManager.getInstance()
        val settings = GlowSettings.getInstance()
        manager.atlas.intensity = settings.state.intensity
        val first = manager.atlas.get(key()) { outline }
        assertEquals(1, manager.atlas.size)

        manager.applySettings(GlowSettings.State(radiusPx = 3f, intensity = 1f))
        assertEquals(3f, settings.state.radiusPx)
        assertEquals(1f, settings.state.intensity)
        assertEquals("atlas dropped after a settings change", 0, manager.atlas.size)

        manager.atlas.get(key()) { outline }
        val sizeBefore = manager.atlas.size
        manager.applySettings(GlowSettings.State(radiusPx = 3f, intensity = 1f))
        assertEquals("unchanged settings keep the atlas", sizeBefore, manager.atlas.size)
        assertNotSame(first, manager.atlas.find(key()))
    }

    fun `test manager toggles the enabled flag`() {
        val manager = GlowManager.getInstance()
        val choices = GlowSettings.State(editorText = false, icons = false, brightness = 0.45f, synthwaveStyle = true)
        GlowSettings.getInstance().loadState(choices)
        assertTrue(manager.isEnabled)
        manager.isEnabled = false
        assertEquals(choices.copy(enabled = false), GlowSettings.getInstance().state)
        manager.isEnabled = true
        assertEquals(choices, GlowSettings.getInstance().state)
    }

    private val outline = Rectangle(0, -8, 6, 8)

    private fun key() = GlyphKey(1, "Monospaced", 0, 13f, 0xFF00FF, 1f, 6f)
}
