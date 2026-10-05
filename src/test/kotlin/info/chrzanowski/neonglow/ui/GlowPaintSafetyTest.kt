package info.chrzanowski.neonglow.ui

import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.GlowWorkBudget
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class GlowPaintSafetyTest {

    @Test
    fun `fully clipped glyphs and icons do not generate masks`() {
        val glyphs = GlyphGlowAtlas()
        val images = ImageGlowAtlas()
        val state = GlowSettings.State()
        val raw = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics()
        raw.setClip(0, 0, 20, 20)
        val g = GlowGraphics2D(raw, glyphs, { state }, { false }, imageAtlas = images)
        try {
            g.color = Color.CYAN
            g.font = Font("Dialog", Font.PLAIN, 18)
            g.drawString("Outside", 100, 80)
            g.drawImage(icon(), 100, 60, null)
            assertEquals(0, glyphs.size)
            assertEquals(0, images.size)
        } finally { g.dispose() }
    }

    @Test
    fun `zero strengths restore original cores and avoid caches even with mapped style`() {
        val state = GlowSettings.State(synthwaveStyle = true, editorGlowStrength = 0f,
            uiGlowStrength = 0f, iconGlowStrength = 0f)
        val glyphs = GlyphGlowAtlas()
        val images = ImageGlowAtlas()
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val g = GlowGraphics2D(image.createGraphics(), glyphs, { state }, { false }, imageAtlas = images)
        try {
            g.color = Color(0x36f9f6)
            g.drawString("Original", 20, 30)
            GlowGraphics2D.withTextTarget(g, true).drawString("Editor", 20, 60)
            g.drawImage(icon(), 130, 30, null)
            assertEquals(0, glyphs.size)
            assertEquals(0, images.size)
            assertFalse(GlowGraphics2D.isGlowing(g))
            assertTrue(image.getRGB(0, 0, 200, 100, null, 0, 200).any { it == Color(0x36f9f6).rgb })
        } finally { g.dispose() }
    }

    @Test
    fun `graphics copies share cold mask limits but retain original painting and cached masks`() {
        val state = GlowSettings.State(performanceMode = true)
        val atlas = GlyphGlowAtlas()
        val image = BufferedImage(2000, 100, BufferedImage.TYPE_INT_ARGB)
        val g = GlowGraphics2D(image.createGraphics(), atlas, { state }, { false }, budget = GlowWorkBudget { 0L })
        try {
            g.font = Font("Dialog", Font.PLAIN, 18)
            g.color = Color.CYAN
            g.drawString("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 10, 30)
            assertEquals(GlowWorkBudget.MAX_GLYPHS, atlas.size)
            val child = g.create() as Graphics2D
            try { child.drawString("abcdefghijklmnopqrstuvwxyz", 10, 60) } finally { child.dispose() }
            assertEquals(GlowWorkBudget.MAX_GLYPHS, atlas.size)
            val hits = atlas.hits
            g.drawString("ABC", 10, 90)
            assertTrue(atlas.hits > hits)
            assertTrue(image.getRGB(0, 40, 2000, 30, null, 0, 2000).any { it == Color.CYAN.rgb })
        } finally { g.dispose() }
    }

    @Test
    fun `reduced strengths change only halos without changing mask keys`() {
        val atlas = GlyphGlowAtlas()
        val state = GlowSettings.State()
        fun render(): BufferedImage {
            val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
            val g = GlowGraphics2D(image.createGraphics(), atlas, { state }, { false })
            try {
                g.color = Color.CYAN
                g.font = Font("Dialog", Font.PLAIN, 18)
                g.drawString("Glow", 40, 60)
            } finally { g.dispose() }
            return image
        }
        val bright = render()
        val size = atlas.size
        state.uiGlowStrength = 0.25f
        val dim = render()
        assertEquals(size, atlas.size)
        assertTrue(atlas.hits > 0)
        val a = bright.getRGB(0, 0, 200, 100, null, 0, 200)
        val b = dim.getRGB(0, 0, 200, 100, null, 0, 200)
        assertFalse(a.contentEquals(b))
        assertTrue(a.indices.any { a[it] == Color.CYAN.rgb && a[it] == b[it] })
        assertTrue(a.indices.all { (b[it] ushr 24) <= (a[it] ushr 24) })
    }

    @Test
    fun `unreasonable glyph transforms skip halo allocation`() {
        val atlas = GlyphGlowAtlas()
        val state = GlowSettings.State()
        val g = GlowGraphics2D(BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics(), atlas,
            { state }, { false })
        try {
            g.font = Font("Dialog", Font.PLAIN, 18)
            val vector = g.font.createGlyphVector(g.fontRenderContext, "A")
            vector.setGlyphTransform(0, java.awt.geom.AffineTransform.getScaleInstance(10_000.0, 10_000.0))
            g.drawGlyphVector(vector, 20f, 30f)
            assertEquals(0, atlas.size)
        } finally { g.dispose() }
    }

    private fun icon(): BufferedImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).also {
        val g: Graphics2D = it.createGraphics()
        try { g.color = Color.MAGENTA; g.fillRect(4, 4, 8, 8) } finally { g.dispose() }
    }
}