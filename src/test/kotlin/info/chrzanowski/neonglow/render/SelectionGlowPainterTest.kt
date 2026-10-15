package info.chrzanowski.neonglow.render

import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

class SelectionGlowPainterTest {
    @Test
    fun `nine slice matches a full Gaussian blur for narrow short and large rectangles`() {
        val state = GlowSettings.State(selectionGlow = true, brightness = 0.6f, intensity = 1.5f)
        for ((width, height) in listOf(1 to 1, 4 to 30, 120 to 18, 200 to 120)) {
            val actual = BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB)
            val expected = BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB)
            val bounds = Rectangle(30, 30, width, height)
            val painter = SelectionGlowPainter()
            val g = actual.createGraphics()
            try { painter.paint(g, bounds, Color.CYAN, Color.BLACK, state, 1f, GlowWorkBudget()) }
            finally { g.dispose() }
            val mask = GlowMaskRenderer.render(Rectangle(0, 0, width, height),
                GlowMaskRenderer.sigmaFor(state.radiusPx, 1f), Color.CYAN.rgb, 1f, state.intensity)
            val reference = expected.createGraphics()
            try {
                reference.composite = AlphaComposite.SrcOver.derive(state.brightness)
                reference.drawImage(mask.image, bounds.x + mask.offsetX, bounds.y + mask.offsetY, null)
            } finally { reference.dispose() }
            assertArrayEquals("${width}x$height", pixels(expected), pixels(actual))
        }
    }

    @Test
    fun `rounded paths match a full blur across tile boundaries and partial repaints`() {
        val state = GlowSettings.State(brightness = 0.6f, intensity = 1.5f)
        val shape = Path2D.Double(RoundRectangle2D.Double(30.0, 30.0, 560.0, 320.0, 20.0, 20.0))
        val mask = GlowMaskRenderer.render(shape, GlowMaskRenderer.sigmaFor(state.radiusPx, 1f), Color.CYAN.rgb, 1f, state.intensity)
        for (partial in listOf(false, true)) {
            val actual = BufferedImage(640, 400, BufferedImage.TYPE_INT_ARGB)
            val expected = BufferedImage(640, 400, BufferedImage.TYPE_INT_ARGB)
            val clip = if (partial) Rectangle(250, 20, 50, 360) else Rectangle(0, 0, 640, 400)
            val painter = SelectionGlowPainter()
            val g = actual.createGraphics()
            try {
                g.clip = clip
                painter.paint(g, shape, Color.CYAN, Color.BLACK, state, 1f, GlowWorkBudget())
            } finally { g.dispose() }
            val reference = expected.createGraphics()
            try {
                reference.clip = clip
                reference.composite = AlphaComposite.SrcOver.derive(state.brightness)
                reference.drawImage(mask.image, mask.offsetX, mask.offsetY, null)
            } finally { reference.dispose() }
            assertArrayEquals("Tiled path blur matches the reference (partial=$partial)", pixels(expected), pixels(actual))
        }
    }

    @Test
    fun `path cache uses geometry reuses translated shapes and shares cache limits`() {
        val painter = SelectionGlowPainter()
        val state = GlowSettings.State()
        val g = BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val shape = Path2D.Double(RoundRectangle2D.Double(30.0, 30.0, 100.0, 20.0, 10.0, 10.0))
        try {
            painter.paint(g, shape, Color.CYAN, Color.BLACK, state, 1f, GlowWorkBudget())
            val bytes = painter.bytes
            painter.paint(g, AffineTransform.getTranslateInstance(20.0, 30.0).createTransformedShape(shape),
                Color.CYAN, Color.BLACK, state, 1f, GlowWorkBudget())
            assertEquals(1, painter.size)
            assertEquals(bytes, painter.bytes)
            painter.paint(g, Path2D.Double(Rectangle(30, 30, 100, 20)), Color.CYAN, Color.BLACK, state, 1f, GlowWorkBudget())
            assertEquals("Paths with the same bounds but different contours need separate masks", 2, painter.size)
            g.clip = Rectangle(0, 0, 10, 10)
            painter.paint(g, shape, Color.RED, Color.BLACK, state, 1f, GlowWorkBudget())
            assertEquals("Fully clipped paths do not allocate masks", 2, painter.size)
            g.clip = null
            for (i in 0..63) painter.paint(g, shape, Color(i * 1000), Color.BLACK, state, 1f, GlowWorkBudget())
            assertTrue(painter.size <= 32)
            assertTrue(painter.bytes <= 4L * 1024 * 1024)
            painter.clear()
            assertEquals(0, painter.size)
            assertEquals(0L, painter.bytes)
        } finally { g.dispose() }
    }

    @Test
    fun `selection mask cache is bounded clears and skips fully clipped work`() {
        val painter = SelectionGlowPainter()
        val state = GlowSettings.State(selectionGlow = true)
        val g = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            g.clip = Rectangle(0, 0, 20, 20)
            painter.paint(g, Rectangle(100, 50, 40, 20), Color.CYAN, Color.BLACK, state, 1f, GlowWorkBudget())
            assertEquals(0, painter.size)
            g.clip = null
            for (i in 0..63) {
                painter.paint(g, Rectangle(40, 30, 80, 20), Color(i * 1000), Color.BLACK, state, 1f, GlowWorkBudget())
            }
            assertTrue(painter.size <= 32)
            assertTrue(painter.bytes <= 4L * 1024 * 1024)
            painter.clear()
            assertEquals(0, painter.size)
            assertEquals(0L, painter.bytes)
        } finally { g.dispose() }
    }

    @Test
    fun `performance mode caps cold selection masks without dropping cached halos`() {
        val painter = SelectionGlowPainter()
        val state = GlowSettings.State(selectionGlow = true, performanceMode = true)
        val budget = GlowWorkBudget { 0L }
        val g = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            for (i in 0..20) painter.paint(g, Rectangle(40, 30, 80, 20), Color(i * 1000), Color.BLACK, state, 1f, budget)
            assertEquals(GlowWorkBudget.MAX_IMAGES, painter.size)
            painter.paint(g, Rectangle(40, 30, 80, 20), Color.BLACK, Color.BLACK, state, 1f, budget)
            assertEquals(GlowWorkBudget.MAX_IMAGES, painter.size)
        } finally { g.dispose() }
    }

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}