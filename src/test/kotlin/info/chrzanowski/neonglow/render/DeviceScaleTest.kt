package info.chrzanowski.neonglow.render

import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage

class DeviceScaleTest {
    @Test
    fun `offscreen images expose their transform scale regardless of JRE HiDPI mode`() {
        val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            assertEquals(1f, DeviceScale.of(g))
            g.scale(2.0, 2.0)
            assertEquals(2f, DeviceScale.of(g))
            g.scale(0.0, 0.0)
            assertEquals("degenerate transforms fall back to 1", 1f, DeviceScale.of(g))
        } finally {
            g.dispose()
        }
    }
}
