package info.chrzanowski.idesynthwave.render

import org.junit.Assert.*
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.pow

class SynthwaveTextStyleTest {

    @Test
    fun `the five exact upstream mappings retain shadow order colours sizes and fixed alpha`() {
        fun assertRule(source: Int, core: Int, vararg shadows: SynthwaveTextStyle.Shadow) {
            assertEquals(SynthwaveTextStyle.Rule(core, shadows.toList()), SynthwaveTextStyle.rule(source))
            assertEquals(SynthwaveTextStyle.rule(source), SynthwaveTextStyle.rule(source or (0x80 shl 24)))
        }
        assertRule(0xfe4450, 0xfff5f6,
            shadow(0, 2f, 1f), shadow(0xfc1f2c, 10f), shadow(0xfc1f2c, 5f), shadow(0xfc1f2c, 25f))
        assertRule(0xff7edb, 0xf92aad,
            shadow(0x100c0f, 2f, 1f), shadow(0xdc078e, 5f, 0.2f), shadow(0xffffff, 10f, 0.2f))
        assertRule(0xfede5d, 0xf4eee4,
            shadow(0x393a33, 2f, 1f), shadow(0xf39f05, 8f), shadow(0xf39f05, 2f))
        assertRule(0x72f1b8, 0x72f1b8,
            shadow(0x100c0f, 2f, 1f), shadow(0x257c55, 10f), shadow(0x212724, 35f))
        assertRule(0x36f9f6, 0xfdfdfd,
            shadow(0x001716, 2f, 1f), shadow(0x03edf9, 3f), shadow(0x03edf9, 5f), shadow(0x03edf9, 8f))
        for (rgb in listOf(0x36f9f5, 0xf97e72, 0xff8b39, 0xffffff)) assertNull(SynthwaveTextStyle.rule(rgb))
    }

    @Test
    fun `layer composition matches a reverse CSS shadow stack at both scales`() {
        val outline = Rectangle(0, -12, 8, 12)
        for (scale in listOf(1f, 2f)) for (rgb in listOf(0x36f9f6, 0xfede5d, 0xff7edb, 0x72f1b8, 0xfe4450)) {
            val key = GlyphKey(65, "Dialog", 0, 18f, rgb, scale, 6f, synthwaveStyle = true, brightness = 0.45f)
            val mask = SynthwaveTextStyle.render(key, outline, 1f)
            val expected = BufferedImage(mask.width, mask.height, BufferedImage.TYPE_INT_ARGB_PRE)
            val g = expected.createGraphics()
            try {
                for (shadow in SynthwaveTextStyle.rule(rgb)!!.shadows.reversed()) {
                    val layer = GlowMaskRenderer.render(outline,
                        GlowMaskRenderer.sigmaFor(shadow.blurPx, scale), shadow.rgb, scale, 1f)
                    g.composite = AlphaComposite.SrcOver.derive(shadow.opacity ?: 0.45f)
                    g.drawImage(layer.image, layer.offsetX - mask.offsetX, layer.offsetY - mask.offsetY, null)
                }
            } finally { g.dispose() }
            assertArrayEquals(pixels(expected), pixels(mask.image))
        }
    }

    @Test
    fun `style and variable brightness separate cached masks while fixed pink stays identical`() {
        val atlas = GlyphGlowAtlas()
        val outline = Rectangle(0, -8, 6, 8)
        val key = GlyphKey(65, "Dialog", 0, 18f, 0x36f9f6, 1f, 6f)
        val plain = atlas.get(key) { outline }
        val neonKey = key.copy(synthwaveStyle = true, brightness = 0.45f)
        val neon = atlas.get(neonKey) { outline }
        assertNotSame(plain, neon)
        assertSame(neon, atlas.get(neonKey) { fail("cached outline"); outline })
        val bright = atlas.get(neonKey.copy(brightness = 1f)) { outline }
        assertFalse(pixels(neon.image).contentEquals(pixels(bright.image)))
        val pink = neonKey.copy(argb = 0xff7edb)
        assertArrayEquals(pixels(atlas.get(pink) { outline }.image),
            pixels(atlas.get(pink.copy(brightness = 1f)) { outline }.image))
        assertEquals(35f, SynthwaveTextStyle.maxRadius(6f), 0f)
    }

    @Test
    fun `ordinary theme colour families get pale cores and distinct layered neon halos`() {
        for (rgb in listOf(0xcc7832, 0x61afef, 0xc678dd, 0xe06c75, 0x98c379, 0xff70c5, 0x56b6c2, 0x36f9f5)) {
            val rule = checkNotNull(SynthwaveTextStyle.rule(rgb, 0x2b2b2b)) { "unmapped ${rgb.toString(16)}" }
            assertNotEquals(rgb, rule.foregroundRgb)
            assertEquals(listOf(2f, 3f, 7f, 12f), rule.shadows.map { it.blurPx })
            assertEquals(1f, rule.shadows.first().opacity)
            assertTrue(rule.shadows.drop(1).all { it.opacity == null && it.rgb != rule.foregroundRgb })
            assertTrue(contrast(rule.foregroundRgb, 0x2b2b2b) >= 4.5)
            assertEquals(rule, SynthwaveTextStyle.rule(rgb or (0x80 shl 24), 0x2b2b2b))
            val originalHue = Color.RGBtoHSB(rgb shr 16, (rgb shr 8) and 255, rgb and 255, null)[0]
            val neon = rule.shadows[1].rgb
            val neonHue = Color.RGBtoHSB(neon shr 16, (neon shr 8) and 255, neon and 255, null)[0]
            assertEquals(originalHue, neonHue, 0.005f)
        }
    }

    @Test
    fun `neutral muted low contrast and unknown surfaces stay regular while light cores are preserved`() {
        for (rgb in listOf(0xffffff, 0xf0eaf7, 0xabcdef, 0x808080, 0x9876aa, 0x334466, 0x008000)) {
            assertNull("restrained ${rgb.toString(16)}", SynthwaveTextStyle.rule(rgb, 0x262335))
        }
        for (rgb in listOf(0x36f9f6, 0xfede5d, 0xfe4450, 0xff7edb, 0x72f1b8, 0xcc7832, 0x61afef)) {
            assertNull(SynthwaveTextStyle.rule(rgb, null))
            for (background in listOf(0xffffff, 0xf2f2f2, 0x9a9a9a)) {
                val rule = SynthwaveTextStyle.rule(rgb, background)
                if (contrast(rgb, background) < 3.0) assertNull(rule)
                else assertEquals(rgb, checkNotNull(rule).foregroundRgb)
            }
            SynthwaveTextStyle.rule(rgb)?.let { assertEquals(it, SynthwaveTextStyle.rule(rgb, 0x262335)) }
        }
        val guardedPink = SynthwaveTextStyle.rule(0xff7edb, 0x555555)!!
        assertEquals(0xff7edb, guardedPink.foregroundRgb)
        assertEquals(SynthwaveTextStyle.rule(0xff7edb)!!.shadows, guardedPink.shadows)
    }

    @Test
    fun `sampled eligible adaptive cores meet flat contrast on supported dark surfaces`() {
        for (background in listOf(0x000000, 0x262335, 0x2b2b2b, 0x555555)) {
            for (hue in 0 until 360 step 15) for (saturation in listOf(0.35f, 0.65f, 1f)) {
                val rgb = Color.HSBtoRGB(hue / 360f, saturation, 0.8f) and 0xffffff
                val rule = SynthwaveTextStyle.rule(rgb, background) ?: continue
                assertTrue("core ${rule.foregroundRgb.toString(16)} on ${background.toString(16)}",
                    contrast(rule.foregroundRgb, background) >= 4.5)
            }
        }
    }

    @Test
    fun `adaptive masks cache resolved palettes and brightness without mixing with same colour masks`() {
        val atlas = GlyphGlowAtlas()
        val outline = Rectangle(0, -8, 6, 8)
        val rgb = 0xcc7832
        val rule = SynthwaveTextStyle.rule(rgb, 0x2b2b2b)!!
        val plain = GlyphKey(65, "Dialog", 0, 18f, rgb, 1f, 6f)
        val key = plain.copy(synthwaveStyle = true, brightness = 0.45f, textStyleRule = rule)
        val mask = atlas.get(key) { outline }
        assertSame(mask, atlas.get(key.copy(textStyleRule = SynthwaveTextStyle.rule(rgb, 0x262335))) {
            fail("equivalent adaptive palettes should share cached masks"); outline
        })
        assertNotSame(mask, atlas.get(plain) { outline })
        assertFalse(pixels(mask.image).contentEquals(pixels(atlas.get(key.copy(brightness = 1f)) { outline }.image)))
        assertFalse(pixels(mask.image).contentEquals(pixels(atlas.get(key.copy(
            textStyleRule = rule.copy(shadows = rule.shadows.map { it.copy(rgb = 0x00ffff) }))) { outline }.image)))
    }

    @Test
    fun `light theme syntax gets coloured layered halos without replacing readable cores`() {
        for (background in listOf(0xffffff, 0xf2f2f2)) {
            for (source in listOf(0x000080, 0x008000, 0x795e26, 0x7a3e9d, 0xaa0000, 0xcc7832, 0xfe4450)) {
                if (contrast(source, background) < 3.0) {
                    assertNull("low-contrast syntax stays regular", SynthwaveTextStyle.rule(source, background))
                    continue
                }
                val rule = checkNotNull(SynthwaveTextStyle.rule(source, background)) {
                    "missing light-theme syntax glow for ${source.toString(16)}"
                }
                assertEquals(source, rule.foregroundRgb)
                assertEquals(listOf(3f, 7f, 12f), rule.shadows.map { it.blurPx })
                assertTrue(rule.shadows.all { it.opacity == null && contrast(it.rgb, background) >= 3.0 })
                assertEquals(rule, SynthwaveTextStyle.rule(source or (0x80 shl 24), background))
            }
            for (source in listOf(0x000000, 0x808080, 0x9876aa, 0x36f9f6, 0xfede5d)) {
                assertNull("neutral, muted or low-contrast text remains regular", SynthwaveTextStyle.rule(source, background))
            }
        }
        for (background in listOf(0xffffff, 0xf2f2f2, 0x9a9a9a)) {
            for (hue in 0 until 360 step 15) for (value in listOf(0.3f, 0.5f, 0.8f)) {
                val source = Color.HSBtoRGB(hue / 360f, 0.65f, value) and 0xffffff
                val rule = SynthwaveTextStyle.rule(source, background)
                if (contrast(source, background) < 3.0) assertNull(rule)
                else {
                    assertEquals(source, checkNotNull(rule).foregroundRgb)
                    assertTrue(rule.shadows.all { contrast(it.rgb, background) >= 3.0 })
                }
            }
        }
        val source = 0xcc7832
        val atlas = GlyphGlowAtlas()
        val key = GlyphKey(65, "Dialog", 0, 18f, source, 1f, 6f, synthwaveStyle = true,
            brightness = 0.45f, textStyleRule = SynthwaveTextStyle.rule(source, 0xffffff))
        val mask = atlas.get(key) { Rectangle(0, -8, 6, 8) }
        assertSame(mask, atlas.get(key) { fail("warm light mask"); Rectangle() })
        assertNotSame(mask, atlas.get(key.copy(textStyleRule = SynthwaveTextStyle.rule(source, 0x262335))) {
            Rectangle(0, -8, 6, 8)
        })
        assertFalse(pixels(mask.image).contentEquals(pixels(atlas.get(key.copy(brightness = 0f)) {
            Rectangle(0, -8, 6, 8)
        }.image)))
    }

    private fun contrast(first: Int, second: Int): Double {
        fun luminance(rgb: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((rgb shr shift) and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        return (maxOf(luminance(first), luminance(second)) + 0.05) / (minOf(luminance(first), luminance(second)) + 0.05)
    }

    private fun shadow(rgb: Int, radius: Float, opacity: Float? = null) = SynthwaveTextStyle.Shadow(rgb, radius, opacity)
    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}