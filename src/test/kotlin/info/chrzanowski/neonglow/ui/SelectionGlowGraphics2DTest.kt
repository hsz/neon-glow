package info.chrzanowski.neonglow.ui

import info.chrzanowski.neonglow.render.SelectionGlowPainter
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

class SelectionGlowGraphics2DTest {
    private val selection = Color(0x7254b5)

    @Test
    fun `selection gains a halo without changing its original background`() {
        val plain = render(GlowSettings.State(selectionGlow = false))
        val glowing = render(GlowSettings.State())
        assertEquals(selection.rgb, glowing.getRGB(60, 40))
        assertEquals(0, plain.getRGB(39, 40))
        assertTrue(glowing.getRGB(39, 40) ushr 24 > 0)
    }

    @Test
    fun `rounded selection paths used by Islands UI gain a halo and retain the native fill`() {
        for (shape in listOf(
            Path2D.Double(RoundRectangle2D.Double(40.0, 30.0, 80.0, 20.0, 10.0, 10.0)),
            Path2D.Float(RoundRectangle2D.Double(40.0, 30.0, 80.0, 20.0, 10.0, 10.0)),
        )) {
            val plain = render(GlowSettings.State(selectionGlow = false), shape = shape)
            val glowing = render(GlowSettings.State(), shape = shape)
            assertEquals(selection.rgb, glowing.getRGB(60, 40))
            assertEquals(0, plain.getRGB(39, 40))
            assertTrue("Rounded paths must receive a selection halo", glowing.getRGB(39, 40) ushr 24 > 0)
            assertArrayEquals(pixels(plain), pixels(render(GlowSettings.State(enabled = false), shape = shape)))
            assertArrayEquals(pixels(plain), pixels(render(GlowSettings.State(uiGlowStrength = 0f), shape = shape)))
            assertArrayEquals(pixels(plain), pixels(render(GlowSettings.State(), powerSave = true, shape = shape)))
        }
    }

    @Test
    fun `multiline selection paths follow the contour rather than their bounding rectangle`() {
        val shape = Path2D.Double().apply {
            moveTo(40.0, 30.0)
            lineTo(160.0, 30.0)
            lineTo(160.0, 50.0)
            lineTo(80.0, 50.0)
            lineTo(80.0, 80.0)
            lineTo(40.0, 80.0)
            closePath()
        }
        val glowing = render(GlowSettings.State(), shape = shape)
        assertTrue("The inset edge receives a halo", glowing.getRGB(82, 65) ushr 24 > 0)
        assertEquals("The empty part of a multiline selection is not filled", 0, glowing.getRGB(140, 70))
        val unrelated = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val painter = SelectionGlowPainter()
        val g = graphics(unrelated, GlowSettings.State(), painter)
        try {
            g.color = Color.RED
            g.fill(shape)
            assertEquals("Unrelated path fills do not allocate selection masks", 0, painter.size)
        } finally { g.dispose() }
    }

    @Test
    fun `selection glow respects master brightness strength power save and paint rules`() {
        for (shape in listOf(null, Path2D.Double(RoundRectangle2D.Double(40.0, 30.0, 80.0, 20.0, 10.0, 10.0)))) {
            val plain = pixels(render(GlowSettings.State(selectionGlow = false), shape = shape))
            for (state in listOf(
                GlowSettings.State(selectionGlow = true, enabled = false),
                GlowSettings.State(selectionGlow = true, brightness = 0f),
                GlowSettings.State(selectionGlow = true, uiGlowStrength = 0f),
            )) assertArrayEquals(plain, pixels(render(state, shape = shape)))
            assertArrayEquals(plain, pixels(render(GlowSettings.State(selectionGlow = true), powerSave = true, shape = shape)))
            assertArrayEquals(pixels(render(GlowSettings.State(selectionGlow = false), source = true, shape = shape)),
                pixels(render(GlowSettings.State(selectionGlow = true), source = true, shape = shape)))
        }
    }

    @Test
    fun `ordinary fills and strict clips do not gain selection glow`() {
        val painter = SelectionGlowPainter()
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val g = graphics(image, GlowSettings.State(selectionGlow = true), painter)
        try {
            g.color = Color.RED
            g.fillRect(40, 30, 80, 20)
            assertEquals(0, painter.size)
            g.color = selection
            g.clip = Rectangle(40, 30, 80, 20)
            g.fill(Rectangle(40, 30, 80, 20))
            assertEquals(0, image.getRGB(39, 40))
        } finally { g.dispose() }
    }

    @Test
    fun `graphics copies keep selection policy and large selections reuse bounded masks`() {
        val painter = SelectionGlowPainter()
        val state = GlowSettings.State(selectionGlow = true, uiText = false, editorText = false,
            regularText = false, synthwaveStyle = false)
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val g = graphics(image, state, painter)
        try {
            g.color = selection
            g.fillRect(40, 30, 1000, 20)
            val bytes = painter.bytes
            val child = g.create() as Graphics2D
            try { child.fillRect(40, 60, 100_000, 20) } finally { child.dispose() }
            assertEquals(1, painter.size)
            assertEquals(bytes, painter.bytes)
            assertTrue(bytes < 64 * 1024)
            assertTrue(image.getRGB(39, 70) ushr 24 > 0)
        } finally { g.dispose() }
    }

    @Test
    fun `selection glow is aligned at fractional and retina scales`() {
        for (scale in listOf(1.25, 2.0)) for (rounded in listOf(false, true)) {
            val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB)
            val raw = image.createGraphics()
            raw.scale(scale, scale)
            val g = SelectionGlowGraphics2D(raw, SelectionGlowPainter(),
                { GlowSettings.State(selectionGlow = true) }, { false }, selection, Color.BLACK, true)
            try {
                g.color = selection
                if (rounded) g.fill(Path2D.Double(RoundRectangle2D.Double(40.0, 30.0, 80.0, 20.0, 10.0, 10.0)))
                else g.fillRect(40, 30, 80, 20)
                assertEquals(selection.rgb, image.getRGB((60 * scale).toInt(), (40 * scale).toInt()))
                assertTrue(image.getRGB((39 * scale).toInt(), (40 * scale).toInt()) ushr 24 > 0)
                assertEquals(0, image.getRGB((10 * scale).toInt(), (40 * scale).toInt()))
            } finally { g.dispose() }
        }
    }

    @Test
    fun `path copies preserve selection policy and clipped large selections keep bounded masks`() {
        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB)
        val painter = SelectionGlowPainter()
        val g = graphics(image, GlowSettings.State(), painter)
        try {
            g.clip = Rectangle(0, 0, image.width, image.height)
            g.color = selection
            val child = g.create(10, 10, 380, 180) as Graphics2D
            try {
                child.fill(Path2D.Double(RoundRectangle2D.Double(40.0, 30.0, 100_000.0, 100_000.0, 10.0, 10.0)))
            } finally { child.dispose() }
            assertTrue(image.getRGB(49, 70) ushr 24 > 0)
            assertEquals("The child clip is not expanded", 0, image.getRGB(390, 70))
            assertTrue(painter.size in 1..32)
            assertTrue(painter.bytes <= 4L * 1024 * 1024)
        } finally { g.dispose() }
    }

    private fun render(state: GlowSettings.State, powerSave: Boolean = false, source: Boolean = false,
        shape: Shape? = null): BufferedImage {
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val g = graphics(image, state, powerSave = powerSave)
        try {
            if (source) g.composite = AlphaComposite.Src
            g.color = selection
            if (shape == null) g.fillRect(40, 30, 80, 20) else g.fill(shape)
        } finally { g.dispose() }
        return image
    }

    private fun graphics(image: BufferedImage, state: GlowSettings.State,
        painter: SelectionGlowPainter = SelectionGlowPainter(), powerSave: Boolean = false): Graphics2D =
        SelectionGlowGraphics2D(image.createGraphics(), painter, { state }, { powerSave }, selection, Color.BLACK, false)

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}