package info.chrzanowski.neonglow.render

import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

class ImageGlowAtlasTest {

    @Test
    fun `unchanged images reuse masks but changing pixels radius scale or intensity does not`() {
        val atlas = ImageGlowAtlas()
        val image = icon(Color.CYAN)
        val first = get(atlas, image)
        assertSame(first, get(atlas, image))
        assertEquals(1L, atlas.misses)
        assertEquals(1L, atlas.hits)
        image.setRGB(4, 4, Color.MAGENTA.rgb)
        assertNotSame(first, get(atlas, image))
        assertNotSame(get(atlas, image), get(atlas, image, radius = 8f))
        assertNotSame(get(atlas, image), get(atlas, image, scale = 2f))
        assertNotSame(get(atlas, image), get(atlas, image, intensity = 1f))
        atlas.clear()
        assertEquals(0, atlas.size)
        assertEquals(0L, atlas.bytes)
        assertEquals(0L, atlas.hits)
        assertEquals(0L, atlas.misses)
    }

    @Test
    fun `cache evicts least recently used masks within both count and byte budgets`() {
        val atlas = ImageGlowAtlas(capacity = 2)
        val cyan = icon(Color.CYAN)
        val magenta = icon(Color.MAGENTA)
        val yellow = icon(Color.YELLOW)
        val first = get(atlas, cyan)
        get(atlas, magenta)
        assertSame(first, get(atlas, cyan))
        get(atlas, yellow)
        assertEquals(2, atlas.size)
        assertSame(first, get(atlas, cyan))
        val misses = atlas.misses
        get(atlas, magenta)
        assertEquals(misses + 1, atlas.misses)
        val bounded = ImageGlowAtlas(maxBytes = 8000)
        for (image in listOf(cyan, magenta, yellow)) {
            get(bounded, image)
            assertTrue(bounded.bytes <= bounded.maxBytes)
        }
        val tooSmall = ImageGlowAtlas(maxBytes = 1)
        get(tooSmall, cyan)
        assertEquals(0, tooSmall.size)
        assertEquals(0L, tooSmall.bytes)
    }

    @Test
    fun `transparent RGB does not pollute glow colour and source alpha is retained`() {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) image.setRGB(x, y, 0x00FF0000)
        image.setRGB(8, 8, 0x8000FFFF.toInt())
        val mask = get(ImageGlowAtlas(), image, intensity = 1f)
        var count = 0
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            val pixel = mask.image.getRGB(x, y)
            if (pixel ushr 24 == 0) continue
            count++
            assertEquals(0, (pixel ushr 16) and 255)
            assertTrue(pixel ushr 24 < 128)
        }
        assertTrue(count > 0)
    }

    @Test
    fun `cold render permission does not block cached icons`() {
        val atlas = ImageGlowAtlas()
        val image = icon(Color.CYAN)
        fun limited(allowed: Boolean): GlowMask? = atlas.getIfAllowed(image, Rectangle(0, 0, 16, 16),
            AffineTransform(), 16, 16, 6f, 1f, 3f) { allowed }
        assertNull(limited(false))
        assertEquals(0, atlas.size)
        val mask = limited(true)
        assertNotNull(mask)
        assertSame(mask, limited(false))
        image.setRGB(4, 4, Color.YELLOW.rgb)
        assertNull(limited(false))
        assertEquals(1, atlas.size)
    }

    private fun get(
        atlas: ImageGlowAtlas, image: BufferedImage,
        radius: Float = 6f, scale: Float = 1f, intensity: Float = 3f,
    ): GlowMask = atlas.get(
        image, Rectangle(0, 0, image.width, image.height),
        AffineTransform.getScaleInstance(scale.toDouble(), scale.toDouble()),
        (image.width * scale).toInt(), (image.height * scale).toInt(), radius, scale, intensity,
    )

    private fun icon(colour: Color): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.color = colour
            g.fillRect(4, 4, 8, 8)
        } finally {
            g.dispose()
        }
        return image
    }
}
