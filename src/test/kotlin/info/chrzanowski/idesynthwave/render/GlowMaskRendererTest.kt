package info.chrzanowski.idesynthwave.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage

class GlowMaskRendererTest {

    private val magenta = 0xFF2BD1

    @Test
    fun `mask covers the outline bounds plus the blur padding on every side`() {
        val sigma = 2f
        val pad = GaussianBlur.radius(sigma)
        val mask = GlowMaskRenderer.render(Rectangle(3, -20, 10, 20), sigma, magenta, 1f, 1f)

        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, mask.image.type)
        assertEquals(10 + 2 * pad, mask.width)
        assertEquals(20 + 2 * pad, mask.height)
        assertEquals(3 - pad, mask.offsetX)
        assertEquals(-20 - pad, mask.offsetY)
        assertEquals(4L * mask.width * mask.height, mask.bytes)
    }

    @Test
    fun `alpha is strongest inside the shape, present just outside it and gone at the border`() {
        val sigma = 2f
        val pad = GaussianBlur.radius(sigma)
        val mask = GlowMaskRenderer.render(Rectangle(0, 0, 10, 20), sigma, magenta, 1f, 1f)

        val centre = alphaAt(mask, pad + 5, pad + 10)
        val justOutside = alphaAt(mask, pad - 1, pad + 10)
        val corner = alphaAt(mask, 0, 0)
        assertTrue("centre $centre", centre > 200)
        assertTrue("just outside $justOutside", justOutside in 20..200)
        assertTrue("faint at the corner $corner", corner <= 1)
        assertTrue(centre > justOutside && justOutside > corner)
    }

    @Test
    fun `pixels are premultiplied and carry the tint`() {
        val mask = GlowMaskRenderer.render(Ellipse2D.Float(0f, 0f, 24f, 24f), 1.5f, magenta, 1f, 1f)
        val pixel = IntArray(4)
        var tinted = 0
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            mask.image.raster.getPixel(x, y, pixel)
            val (r, g, b, a) = pixel
            assertTrue("r=$r a=$a", r <= a)
            assertTrue("g=$g a=$a", g <= a)
            assertTrue("b=$b a=$a", b <= a)
            if (a == 255) {
                tinted++
                assertEquals(0xFF, r)
                assertEquals(0x2B, g)
                assertEquals(0xD1, b)
                assertEquals(magenta, mask.image.getRGB(x, y) and 0xFFFFFF)
            }
        }
        assertTrue("some pixels reach full alpha inside the ellipse", tinted > 0)
    }

    @Test
    fun `intensity scales the alpha and is clamped`() {
        val outline = Rectangle(0, 0, 2, 20)
        val faint = GlowMaskRenderer.render(outline, 2f, magenta, 1f, 1f)
        val strong = GlowMaskRenderer.render(outline, 2f, magenta, 1f, 3f)
        val x = faint.width / 2
        val y = faint.height / 2
        val a1 = alphaAt(faint, x, y)
        val a3 = alphaAt(strong, x, y)
        assertTrue("$a1 < $a3", a1 < a3)
        assertTrue(a3 <= 255)
        // At the edge of the halo the relation is roughly linear.
        val ex = 2
        assertEquals(alphaAt(faint, ex, y) * 3.0, alphaAt(strong, ex, y).toDouble(), 3.0)
    }

    @Test
    fun `device scale two doubles the raster`() {
        val outline = Rectangle(0, -10, 8, 10)
        val at1x = GlowMaskRenderer.render(outline, 2f, magenta, 1f, 1f)
        val at2x = GlowMaskRenderer.render(outline, 4f, magenta, 2f, 1f)
        assertEquals(2 * at1x.width, at2x.width)
        assertEquals(2 * at1x.height, at2x.height)
        assertEquals(2 * at1x.offsetX, at2x.offsetX)
        assertEquals(2 * at1x.offsetY, at2x.offsetY)
    }

    @Test
    fun `empty outline yields a padding-only mask without errors`() {
        val mask = GlowMaskRenderer.render(Rectangle(0, 0, 0, 0), 1f, magenta, 1f, 1f)
        val pad = GaussianBlur.radius(1f)
        assertEquals(2 * pad, mask.width)
        assertEquals(2 * pad, mask.height)
        for (y in 0 until mask.height) for (x in 0 until mask.width) assertEquals(0, alphaAt(mask, x, y))
    }

    private fun alphaAt(mask: GlowMask, x: Int, y: Int): Int = mask.image.getRGB(x, y) ushr 24
}
