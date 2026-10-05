package info.chrzanowski.neonglow.ui

import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.text.AttributedString

class GlowGraphics2DTest {

    private val state = GlowSettings.State()
    private val atlas = GlyphGlowAtlas()
    private var powerSave = false

    @Test
    fun `text has a tinted halo outside the original glyphs at both scales`() {
        for (scale in listOf(1.0, 2.0)) {
            val plain = paint(scale, glow = false) { it.drawString("Glow", 30, 50) }
            val glowing = paint(scale) { it.drawString("Glow", 30, 50) }
            assertHalo(plain, glowing)
            assertEquals("no pixels far from the text", 0, glowing.getRGB(5, 5))
        }
        assertTrue(atlas.size >= 8)
    }

    @Test
    fun `child graphics retain glow without changing the parent state`() {
        val image = paint { g ->
            val transform = g.transform
            val hints = g.renderingHints
            val composite = g.composite
            val child = g.create(10, 10, 150, 90) as Graphics2D
            try {
                child.drawString("Child", 20f, 40f)
            } finally {
                child.dispose()
            }
            assertEquals(transform, g.transform)
            assertEquals(hints, g.renderingHints)
            assertEquals(composite, g.composite)
            assertEquals(Color.CYAN, g.color)
        }
        val plain = paint(glow = false) { it.drawString("Child", 30f, 50f) }
        assertHalo(plain, image)
    }

    @Test
    fun `glyph vectors chars bytes and attributed text glow`() {
        val attributed = AttributedString("Colour")
        attributed.addAttribute(TextAttribute.FONT, Font("Dialog", Font.BOLD, 18))
        attributed.addAttribute(TextAttribute.FOREGROUND, Color.MAGENTA)
        val operations: List<(Graphics2D) -> Unit> = listOf(
            { it.drawGlyphVector(it.font.createGlyphVector(it.fontRenderContext, "Glyph"), 30f, 50f) },
            { it.drawChars("Chars".toCharArray(), 0, 5, 30, 50) },
            { it.drawBytes("Bytes".toByteArray(), 0, 5, 30, 50) },
            { it.drawString(attributed.iterator, 30f, 50f) },
        )
        for (operation in operations) {
            assertHalo(paint(glow = false, draw = operation), paint(draw = operation))
        }
    }

    @Test
    fun `disabled power save and blank text do not populate the atlas`() {
        state.enabled = false
        val plain = paint(glow = false) { it.drawString("Disabled", 30, 50) }
        assertPixelsEqual(plain, paint { it.drawString("Disabled", 30, 50) })
        state.enabled = true
        powerSave = true
        assertPixelsEqual(plain, paint { it.drawString("Disabled", 30, 50) })
        powerSave = false
        paint { it.drawString("  ", 30, 50); it.drawString("", 30, 50) }
        assertEquals(0, atlas.size)
    }

    @Test
    fun `repeated text reuses masks and non-text drawing stays unchanged`() {
        paint { it.drawString("Cached", 30, 50) }
        val misses = atlas.misses
        paint { it.drawString("Cached", 30, 50) }
        assertEquals(misses, atlas.misses)
        assertTrue(atlas.hits > 0)
        val shape: (Graphics2D) -> Unit = { it.fillRect(10, 10, 40, 20); it.drawLine(5, 70, 90, 70) }
        assertPixelsEqual(paint(glow = false, draw = shape), paint(draw = shape))
    }

    @Test
    fun `brightness fades only the halo after intensity without rebuilding masks`() {
        val draw: (Graphics2D) -> Unit = { g ->
            val composite = g.composite
            g.drawString("Glow", 30, 50)
            assertEquals(composite, g.composite)
        }
        for (scale in listOf(1.0, 2.0)) {
            state.brightness = 1f
            val plain = paint(scale, false, draw)
            val full = paint(scale, true, draw)
            val misses = atlas.misses
            state.brightness = 0.45f
            val dim = paint(scale, true, draw)
            assertEquals("opacity is a blit parameter, not a mask parameter", misses, atlas.misses)
            var fullAlpha = 0L
            var dimAlpha = 0L
            for (y in 0 until plain.height) for (x in 0 until plain.width) {
                if (plain.getRGB(x, y) ushr 24 == 255) assertEquals(plain.getRGB(x, y), dim.getRGB(x, y))
                if (plain.getRGB(x, y) ushr 24 != 0) continue
                fullAlpha += full.getRGB(x, y) ushr 24
                dimAlpha += dim.getRGB(x, y) ushr 24
            }
            assertTrue(fullAlpha > 0)
            assertEquals(0.45, dimAlpha.toDouble() / fullAlpha, 0.02)
            state.brightness = 0f
            atlas.clear()
            assertPixelsEqual(plain, paint(scale, true, draw))
            assertEquals(0, atlas.size)
        }
    }

    private fun paint(scale: Double = 1.0, glow: Boolean = true, draw: (Graphics2D) -> Unit): BufferedImage {
        val image = BufferedImage((200 * scale).toInt(), (100 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val raw = image.createGraphics()
        raw.scale(scale, scale)
        raw.setClip(0, 0, 200, 100)
        raw.font = Font("Dialog", Font.PLAIN, 18)
        raw.color = Color.CYAN
        raw.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val g = if (glow) GlowGraphics2D(raw, atlas, { state }, { powerSave }) else raw
        try {
            draw(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun assertHalo(plain: BufferedImage, glowing: BufferedImage) {
        var halo = 0
        for (y in 0 until plain.height) for (x in 0 until plain.width) {
            if (plain.getRGB(x, y) ushr 24 != 0) continue
            val pixel = glowing.getRGB(x, y)
            if (pixel ushr 24 == 0) continue
            halo++
            assertEquals("halo preserves the cyan or magenta foreground", 0, (pixel shr 16) and (pixel shr 8) and 0xFF)
        }
        assertTrue("blurred pixels must extend outside the original text", halo > 50)
    }

    private fun assertPixelsEqual(expected: BufferedImage, actual: BufferedImage) {
        assertArrayEquals(
            expected.getRGB(0, 0, expected.width, expected.height, null, 0, expected.width),
            actual.getRGB(0, 0, actual.width, actual.height, null, 0, actual.width),
        )
    }
}