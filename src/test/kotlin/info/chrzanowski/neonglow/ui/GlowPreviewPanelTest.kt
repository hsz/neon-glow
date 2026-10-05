package info.chrzanowski.neonglow.ui

import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage

class GlowPreviewPanelTest {
    @Test
    fun `preview renders draft changes at both scales without mutating settings`() {
        val state = GlowSettings.State(synthwaveStyle = true, brightness = 0.45f)
        val initial = state.copy()
        val preview = GlowPreviewPanel { state }
        preview.setSize(540, 160)
        fun paint(scale: Int): IntArray {
            val image = BufferedImage(540 * scale, 160 * scale, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try { g.scale(scale.toDouble(), scale.toDouble()); preview.paint(g) } finally { g.dispose() }
            return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        }
        for (scale in listOf(1, 2)) {
            state.enabled = true
            val neon = paint(scale)
            assertEquals(initial, state)
            state.enabled = false
            val flat = paint(scale)
            assertFalse(neon.contentEquals(flat))
            assertFalse(state.enabled)
            preview.removeNotify()
            assertArrayEquals(flat, paint(scale))
        }
    }
}