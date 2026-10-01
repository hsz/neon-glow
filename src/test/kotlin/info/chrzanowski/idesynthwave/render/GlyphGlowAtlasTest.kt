package info.chrzanowski.idesynthwave.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage

class GlyphGlowAtlasTest {

    private val outline: Shape = Rectangle(0, -8, 6, 8)
    private var renders = 0
    private val atlas = GlyphGlowAtlas(capacity = 3) { _, _, _ ->
        renders++
        GlowMask(BufferedImage(4, 5, BufferedImage.TYPE_INT_ARGB_PRE), -1, -2)
    }

    private fun key(glyph: Int = 65, argb: Int = 0xFF2BD1, scale: Float = 1f, radius: Float = 6f) =
        GlyphKey(glyph, "JetBrains Mono", 0, 13f, argb, scale, radius)

    @Test
    fun `identical keys hit and the outline is only computed on a miss`() {
        val first = atlas.get(key()) { outline }
        val second = atlas.get(key()) { fail("outline must not be requested on a hit"); outline }

        assertSame(first, second)
        assertEquals(1, renders)
        assertEquals(1L, atlas.misses)
        assertEquals(1L, atlas.hits)
        assertEquals(1, atlas.size)
        assertEquals(4L * 4 * 5, atlas.bytes)
    }

    @Test
    fun `colour, scale and radius are part of the key`() {
        val base = atlas.get(key()) { outline }
        assertNotSame(base, atlas.get(key(argb = 0x00FFFF)) { outline })
        assertNotSame(base, atlas.get(key(scale = 2f)) { outline })
        assertNotSame(base, atlas.get(key(radius = 3f)) { outline })
        assertEquals(4L, atlas.misses)
        assertEquals(0L, atlas.hits)
    }

    @Test
    fun `least recently used entry is evicted at capacity`() {
        val a = atlas.get(key(glyph = 1)) { outline }
        atlas.get(key(glyph = 2)) { outline }
        atlas.get(key(glyph = 3)) { outline }
        assertSame(a, atlas.get(key(glyph = 1)) { outline }) // touch 1 → 2 is now the least recently used

        atlas.get(key(glyph = 4)) { outline }
        assertEquals(3, atlas.size)
        assertEquals(1L, atlas.evictions)
        assertEquals(3 * 4L * 4 * 5, atlas.bytes)

        assertSame(a, atlas.find(key(glyph = 1)))
        assertEquals(null, atlas.find(key(glyph = 2)))
        assertTrue(atlas.find(key(glyph = 3)) != null)
        assertTrue(atlas.find(key(glyph = 4)) != null)
    }

    @Test
    fun `clear drops the entries and resets the counters`() {
        atlas.get(key()) { outline }
        atlas.get(key()) { outline }
        atlas.clear()

        assertEquals(0, atlas.size)
        assertEquals(0L, atlas.bytes)
        assertEquals(0L, atlas.hits)
        assertEquals(0L, atlas.misses)
        assertEquals(0L, atlas.evictions)
        atlas.get(key()) { outline }
        assertEquals(2, renders)
    }

    @Test
    fun `changing the intensity invalidates the cache`() {
        atlas.get(key()) { outline }
        atlas.intensity = 1f // unchanged → keeps the entry
        assertEquals(1, atlas.size)
        atlas.intensity = 2f
        assertEquals(0, atlas.size)
    }

    @Test
    fun `find and render form an allocation-light alternative to get`() {
        assertEquals(null, atlas.find(key()))
        val rendered = atlas.render(key(), outline)
        assertSame(rendered, atlas.find(key()))
        assertEquals(1L, atlas.misses)
        assertEquals(1L, atlas.hits)
        atlas.render(key(), outline) // replacing keeps the byte count consistent
        assertEquals(4L * 4 * 5, atlas.bytes)
    }

    @Test
    fun `default renderer produces real masks`() {
        val real = GlyphGlowAtlas(capacity = 10)
        val mask = real.get(key(radius = 4f)) { outline }
        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, mask.image.type)
        val pad = GaussianBlur.radius(GlowMaskRenderer.sigmaFor(4f, 1f))
        assertEquals(6 + 2 * pad, mask.width)
        assertEquals(8 + 2 * pad, mask.height)
        assertTrue(real.bytes > 0)
    }
}
