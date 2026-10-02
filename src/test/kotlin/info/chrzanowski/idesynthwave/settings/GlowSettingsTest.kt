package info.chrzanowski.idesynthwave.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import info.chrzanowski.idesynthwave.GlowManager
import info.chrzanowski.idesynthwave.render.GlyphKey
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
        assertEquals(GlowSettings.DEFAULT_RADIUS, state.radiusPx)
        assertEquals(GlowSettings.DEFAULT_INTENSITY, state.intensity)
        assertEquals(state, state.normalized())
    }

    fun `test state round-trips through XML`() {
        val original = GlowSettings.State(enabled = false, radiusPx = 9f, intensity = 1.5f)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(original), GlowSettings.State::class.java)
        val settings = GlowSettings()
        settings.loadState(restored)

        assertEquals(original, restored)
        assertEquals(original, settings.state)
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
        assertTrue(manager.isEnabled)
        manager.isEnabled = false
        assertFalse(GlowSettings.getInstance().state.enabled)
        manager.isEnabled = true
        assertTrue(GlowSettings.getInstance().state.enabled)
    }

    private val outline = Rectangle(0, -8, 6, 8)

    private fun key() = GlyphKey(1, "Monospaced", 0, 13f, 0xFF00FF, 1f, 6f)
}
