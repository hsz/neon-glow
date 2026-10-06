package info.chrzanowski.neonglow.ui

import com.intellij.ui.Graphics2DDelegate
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.render.SynthwaveTextStyle
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.font.GlyphVector
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.text.AttributedString

class SynthwaveGlowGraphics2DTest {

    private val state = GlowSettings.State(synthwaveStyle = true, brightness = 0.45f, intensity = 1f)
    private val atlas = GlyphGlowAtlas()
    private var powerSave = false
    private val cores = mutableListOf<Color>()

    @Test
    fun `mapped text cores are replaced without leaking colour or graphics state at both scales`() {
        val mappings = mapOf(0x36f9f6 to 0xfdfdfd, 0xfede5d to 0xf4eee4, 0xfe4450 to 0xfff5f6,
            0xff7edb to 0xf92aad, 0x72f1b8 to 0x72f1b8)
        for (scale in listOf(1.0, 2.0)) for ((source, core) in mappings) {
            cores.clear()
            paint(scale) { g ->
                val colour = Color((0x80 shl 24) or source, true)
                g.color = colour
                val composite = AlphaComposite.SrcOver.derive(0.5f)
                g.composite = composite
                val transform = g.transform
                val clip = g.clip.bounds
                g.drawGlyphVector(g.font.createGlyphVector(g.fontRenderContext, "Core"), 50f, 65f)
                assertEquals(colour, g.color)
                assertEquals(composite, g.composite)
                assertEquals(transform, g.transform)
                assertEquals(clip, g.clip.bounds)
            }
            assertEquals(listOf(Color((0x80 shl 24) or core, true)), cores)
        }
        assertTrue(atlas.size > 0)
    }

    @Test
    fun `SynthWave mode preserves same colour glow for ordinary editor and UI text`() {
        state.regularText = true
        for (scale in listOf(1.0, 2.0)) for (editor in listOf(false, true)) {
            for (colour in listOf(Color(0xf0eaf7), Color(0x9876aa), Color(0xabcdef))) {
                state.editorGlowStrength = 0.65f
                state.uiGlowStrength = 0.25f
                fun draw(g: Graphics2D) {
                    val scoped = GlowGraphics2D.withTextTarget(g, editor).create() as Graphics2D
                    try {
                        scoped.color = colour
                        scoped.drawString("Ordinary", 50, 65)
                    } finally { scoped.dispose() }
                }
                state.synthwaveStyle = false
                cores.clear()
                val sameColour = paint(scale, draw = ::draw)
                assertTrue(atlas.size > 0)
                atlas.clear()
                state.synthwaveStyle = true
                cores.clear()
                val styled = paint(scale, draw = ::draw)
                assertTrue("unmapped text must still generate halo masks", atlas.size > 0)
                assertEquals(listOf(colour), cores)
                assertArrayEquals(sameColour.getRGB(0, 0, sameColour.width, sameColour.height, null, 0, sameColour.width),
                    styled.getRGB(0, 0, styled.width, styled.height, null, 0, styled.width))
            }
        }
    }

    @Test
    fun `all text overloads and attributed runs use the mapping`() {
        val attributed = AttributedString("AB")
        attributed.addAttribute(TextAttribute.FOREGROUND, Color(0x36f9f6), 0, 1)
        attributed.addAttribute(TextAttribute.FOREGROUND, Color(0xfede5d), 1, 2)
        val draws: List<(Graphics2D) -> Unit> = listOf(
            { it.drawString("A", 50, 65) }, { it.drawString("A", 50f, 65f) },
            { it.drawChars(charArrayOf('A'), 0, 1, 50, 65) },
            { it.drawBytes(byteArrayOf(65), 0, 1, 50, 65) },
        )
        for (draw in draws) {
            cores.clear()
            paint { g -> g.color = Color(0x36f9f6); draw(g) }
            assertEquals(listOf(Color(0xfdfdfd)), cores)
        }
        for (integer in listOf(false, true)) {
            cores.clear()
            paint { g ->
                if (integer) g.drawString(attributed.iterator, 50, 65) else g.drawString(attributed.iterator, 50f, 65f)
            }
            assertTrue(cores.contains(Color(0xfdfdfd)))
            assertTrue(cores.contains(Color(0xf4eee4)))
        }
    }

    @Test
    fun `disabled paths restore the original mapped core`() {
        val gates: List<() -> Unit> = listOf(
            { state.enabled = false }, { state.uiText = false }, { state.brightness = 0f }, { powerSave = true },
        )
        for (gate in gates) {
            state.enabled = true; state.uiText = true; state.brightness = 0.45f; powerSave = false
            gate()
            cores.clear()
            paint { g -> g.color = Color(0x36f9f6); g.drawGlyphVector(g.font.createGlyphVector(g.fontRenderContext, "A"), 50f, 65f) }
            assertEquals(listOf(Color(0x36f9f6)), cores)
            assertEquals(0, atlas.size)
        }
    }

    @Test
    fun `unsupported paints and composites bypass the effect and icons are not recoloured`() {
        paint { g ->
            g.color = Color(0x36f9f6)
            g.composite = AlphaComposite.Src
            g.drawGlyphVector(g.font.createGlyphVector(g.fontRenderContext, "A"), 50f, 65f)
            assertEquals(Color(0x36f9f6), cores.single())
            g.composite = AlphaComposite.SrcOver
            g.paint = GradientPaint(0f, 0f, Color(0x36f9f6), 10f, 10f, Color(0xfede5d))
            g.drawString("Gradient", 50, 65)
        }
        assertEquals(0, atlas.size)
        val icon = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val colour = Color(0x36f9f6).rgb
        for (y in 4..11) for (x in 4..11) icon.setRGB(x, y, colour)
        val image = paint { it.drawImage(icon, 50, 50, null) }
        assertEquals(colour, image.getRGB(57, 57))
        state.synthwaveStyle = false
        val legacy = paint { it.drawImage(icon, 50, 50, null) }
        assertArrayEquals(image.getRGB(0, 0, 240, 140, null, 0, 240), legacy.getRGB(0, 0, 240, 140, null, 0, 240))
    }

    @Test
    fun `editor and UI targets independently control replacement through copied scoped graphics`() {
        for (editor in listOf(false, true)) for (ui in listOf(false, true)) {
            state.editorText = editor
            state.uiText = ui
            cores.clear()
            paint { g ->
                g.color = Color(0x36f9f6)
                val glyphs = g.font.createGlyphVector(g.fontRenderContext, "A")
                g.drawGlyphVector(glyphs, 50f, 40f)
                val scoped = GlowGraphics2D.withTextTarget(g, true).create() as Graphics2D
                try { scoped.drawGlyphVector(glyphs, 50f, 70f) } finally { scoped.dispose() }
                g.drawGlyphVector(glyphs, 50f, 100f)
            }
            assertEquals(listOf(Color(if (ui) 0xfdfdfd else 0x36f9f6),
                Color(if (editor) 0xfdfdfd else 0x36f9f6), Color(if (ui) 0xfdfdfd else 0x36f9f6)), cores)
        }
    }

    @Test
    fun `rendered cyan text has a white core and a cyan halo instead of a cyan foreground`() {
        for (scale in listOf(1.0, 2.0)) {
            val image = paint(scale) { g -> g.color = Color(0x36f9f6); g.drawString("Neon", 50, 65) }
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            assertTrue(pixels.any { it == Color(0xfdfdfd).rgb })
            assertTrue(pixels.any { pixel ->
                val alpha = pixel ushr 24
                val red = (pixel shr 16) and 255
                val green = (pixel shr 8) and 255
                val blue = pixel and 255
                alpha in 5..100 && green > red + 100 && blue > red + 100
            })
        }
    }

    @Test
    fun `adaptive text works in both targets across fractional and HiDPI scales without changing graphics`() {
        for (scale in listOf(1.0, 1.25, 2.0)) for (editor in listOf(false, true)) {
            cores.clear()
            val source = Color(0xcc7832)
            val rule = SynthwaveTextStyle.rule(source.rgb, 0x2b2b2b)!!
            paint(scale, textBackground = Color(0x2b2b2b)) { g ->
                val scoped = GlowGraphics2D.withTextTarget(g, editor).create() as Graphics2D
                try {
                    scoped.color = source
                    val transform = scoped.transform
                    val clip = scoped.clip.bounds
                    scoped.drawString("Adaptive", 50, 65)
                    assertEquals(source, scoped.color)
                    assertEquals(transform, scoped.transform)
                    assertEquals(clip, scoped.clip.bounds)
                } finally { scoped.dispose() }
            }
            assertEquals(listOf(Color(rule.foregroundRgb)), cores)
            assertTrue(atlas.size > 0)
        }
    }

    @Test
    fun `unknown backgrounds and ineligible light text keep original cores and same colour glow`() {
        for (background in listOf(null, Color.WHITE, Color(0xf2f2f2))) for (source in listOf(0x808080, 0x36f9f6)) {
            fun draw(g: Graphics2D) { g.color = Color(source); g.drawString("Safe", 50, 65) }
            cores.clear()
            val styled = paint(textBackground = background, draw = ::draw)
            assertEquals(listOf(Color(source)), cores)
            state.synthwaveStyle = false
            val plain = paint(textBackground = background, draw = ::draw)
            state.synthwaveStyle = true
            assertArrayEquals(pixels(plain), pixels(styled))
        }
    }

    @Test
    fun `background fills protect light selections and graphics copies do not contaminate siblings`() {
        for (scale in listOf(1.0, 2.0)) {
            cores.clear()
            paint(scale) { g ->
                g.color = Color(0xcc7832)
                val child = g.create(20, 20, 160, 90) as Graphics2D
                try {
                    child.color = Color.WHITE
                    child.fillRect(20, 15, 120, 50)
                    child.color = Color(0xcc7832)
                    child.drawString("Selected", 25, 45)
                } finally { child.dispose() }
                g.drawString("Dark", 50, 100)
            }
            assertEquals(listOf(Color(0xcc7832), Color(SynthwaveTextStyle.rule(0xcc7832, 0x262335)!!.foregroundRgb)), cores)
        }
        cores.clear()
        paint { g ->
            g.paint = GradientPaint(0f, 0f, Color.WHITE, 240f, 140f, Color.BLACK)
            g.fillRect(0, 0, 240, 140)
            g.color = Color(0xcc7832)
            g.drawString("Unknown gradient", 50, 65)
        }
        assertEquals(listOf(Color(0xcc7832)), cores)
    }

    @Test
    fun `same target background scopes and copied delegates retain their own policy`() {
        cores.clear()
        paint { g ->
            g.color = Color(0x36f9f6)
            val light = GlowGraphics2D.withTextTarget(g, false, Color.WHITE).create() as Graphics2D
            try { light.drawString("Light", 50, 40) } finally { light.dispose() }
            g.drawString("Dark", 50, 80)
        }
        assertEquals(listOf(Color(0x36f9f6), Color(0xfdfdfd)), cores)
    }

    @Test
    fun `adaptive style disabled gates restore original cores and avoid mask work`() {
        for (gate in listOf<(GlowSettings.State) -> Unit>(
            { it.enabled = false }, { it.uiText = false }, { it.uiGlowStrength = 0f }, { it.brightness = 0f },
        )) {
            state.enabled = true; state.uiText = true; state.uiGlowStrength = 1f; state.brightness = 0.45f
            gate(state)
            atlas.clear()
            cores.clear()
            paint { g -> g.color = Color(0xcc7832); g.drawGlyphVector(g.font.createGlyphVector(g.fontRenderContext, "A"), 50f, 65f) }
            assertEquals(listOf(Color(0xcc7832)), cores)
            assertEquals(0, atlas.size)
        }
    }

    @Test
    fun `regular text switch skips cold and warm ordinary halos but keeps styled text in both targets`() {
        for (scale in listOf(1.0, 1.25, 2.0)) for (editor in listOf(false, true)) {
            for (source in listOf(0xf0eaf7, 0x9876aa, 0x36f9f6, 0xcc7832)) {
                val rule = SynthwaveTextStyle.rule(source, 0x262335)
                fun draw(g: Graphics2D) {
                    val scoped = GlowGraphics2D.withTextTarget(g, editor).create() as Graphics2D
                    try {
                        scoped.color = Color(source)
                        scoped.drawGlyphVector(scoped.font.createGlyphVector(scoped.fontRenderContext, "Text"), 50f, 65f)
                    } finally { scoped.dispose() }
                }
                state.enabled = false
                val flat = paint(scale, draw = ::draw)
                state.enabled = true
                atlas.clear()
                state.regularText = false
                cores.clear()
                val off = paint(scale, draw = ::draw)
                assertEquals(listOf(Color(rule?.foregroundRgb ?: source)), cores)
                assertEquals(rule != null, atlas.size > 0)
                if (rule == null) assertArrayEquals(pixels(flat), pixels(off))
                else assertFalse(pixels(flat).contentEquals(pixels(off)))

                state.regularText = true
                val on = paint(scale, draw = ::draw)
                assertTrue(atlas.size > 0)
                if (rule != null) assertArrayEquals(pixels(on), pixels(off))
                else assertFalse(pixels(on).contentEquals(pixels(off)))
                val misses = atlas.misses
                state.regularText = false
                assertArrayEquals(pixels(off), pixels(paint(scale, draw = ::draw)))
                assertEquals("switching off must not generate masks, even with a warm cache", misses, atlas.misses)
            }
        }
    }

    @Test
    fun `regular text off also disables fallback glow on light and unknown backdrops`() {
        state.regularText = false
        for (background in listOf(null, Color.WHITE)) {
            atlas.clear()
            cores.clear()
            paint(textBackground = background) { g ->
                g.color = Color(0x36f9f6)
                g.drawString("Plain", 50, 65)
            }
            assertEquals(listOf(Color(0x36f9f6)), cores)
            assertEquals(0, atlas.size)
        }
    }

    @Test
    fun `regular text off preserves attributed styled runs and does not affect icon glow`() {
        state.regularText = false
        val mixed = AttributedString("Quiet Neon").apply {
            addAttribute(TextAttribute.FONT, Font("Dialog", Font.PLAIN, 18))
            addAttribute(TextAttribute.FOREGROUND, Color(0xf0eaf7), 0, 6)
            addAttribute(TextAttribute.FOREGROUND, Color(0x36f9f6), 6, 10)
        }
        paint { g -> g.drawString(mixed.iterator, 40, 65) }
        assertTrue(Color(0xf0eaf7) in cores)
        assertTrue(Color(0xfdfdfd) in cores)
        val misses = atlas.misses
        paint { g -> g.color = Color(0x36f9f6); g.drawString("Neon", 40, 65) }
        assertEquals("only the styled run should have populated the atlas", misses, atlas.misses)

        val icon = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().let { g ->
                try { g.color = Color.CYAN; g.fillRect(4, 4, 8, 8) } finally { g.dispose() }
            }
        }
        state.synthwaveStyle = false
        val off = paint { g -> g.drawImage(icon, 50, 65, null) }
        state.regularText = true
        val on = paint { g -> g.drawImage(icon, 50, 65, null) }
        assertArrayEquals(pixels(on), pixels(off))
        state.icons = false
        val flat = paint { g -> g.drawImage(icon, 50, 65, null) }
        assertFalse(pixels(flat).contentEquals(pixels(off)))
    }

    @Test
    fun `light syntax glows with regular text off while cores and disable gates stay intact`() {
        state.regularText = false
        for (scale in listOf(1.0, 1.25, 2.0)) for (editor in listOf(false, true)) {
            for (source in listOf(0x000080, 0x008000, 0x795e26, 0x7a3e9d, 0xaa0000)) {
                fun draw(g: Graphics2D) {
                    g.color = Color.WHITE
                    g.fillRect(0, 0, 240, 140)
                    val scoped = GlowGraphics2D.withTextTarget(g, editor, Color.WHITE).create() as Graphics2D
                    try { scoped.color = Color(source); scoped.drawString("Syntax", 50, 65) }
                    finally { scoped.dispose() }
                }
                atlas.clear()
                state.enabled = false
                val flat = paint(scale, Color.WHITE, ::draw)
                state.enabled = true
                cores.clear()
                val styled = paint(scale, Color.WHITE, ::draw)
                assertEquals(listOf(Color(source)), cores)
                assertTrue("syntax halos must not depend on Regular text glow", atlas.size > 0)
                assertFalse(pixels(flat).contentEquals(pixels(styled)))
                val misses = atlas.misses
                assertArrayEquals(pixels(styled), pixels(paint(scale, Color.WHITE, ::draw)))
                assertEquals(misses, atlas.misses)
                state.regularText = true
                assertArrayEquals(pixels(styled), pixels(paint(scale, Color.WHITE, ::draw)))
                state.regularText = false
                for (gate in listOf<(GlowSettings.State) -> Unit>(
                    { it.synthwaveStyle = false }, { it.brightness = 0f },
                    { if (editor) it.editorText = false else it.uiText = false },
                    { if (editor) it.editorGlowStrength = 0f else it.uiGlowStrength = 0f },
                )) {
                    gate(state)
                    assertArrayEquals(pixels(flat), pixels(paint(scale, Color.WHITE, ::draw)))
                    assertEquals(misses, atlas.misses)
                    state.synthwaveStyle = true
                    state.brightness = 0.45f
                    state.editorText = true; state.uiText = true
                    state.editorGlowStrength = 1f; state.uiGlowStrength = 1f
                }
                powerSave = true
                assertArrayEquals(pixels(flat), pixels(paint(scale, Color.WHITE, ::draw)))
                powerSave = false
            }
        }
    }

    @Test
    fun `default brightness uses 25 percent on light backgrounds and 50 percent on dark backgrounds`() {
        val icon = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().let { g ->
                try { g.color = Color.CYAN; g.fillRect(4, 4, 8, 8) } finally { g.dispose() }
            }
        }
        val drawIcon: (Graphics2D) -> Unit = { g -> g.drawImage(icon, 50, 65, null) }
        val drawText: (Graphics2D) -> Unit = { g -> g.color = Color(0x36f9f6); g.drawString("Neon", 50, 65) }

        for (draw in listOf(drawIcon, drawText)) {
            state.regularText = true
            state.brightness = 0.5f

            // Light background with default brightness (0.5f) matches explicit 0.25f
            val lightDefault = paint(textBackground = Color.WHITE, draw = draw)
            state.brightness = 0.25f
            val lightExplicit25 = paint(textBackground = Color.WHITE, draw = draw)
            assertArrayEquals(pixels(lightDefault), pixels(lightExplicit25))

            // Dark background with default brightness (0.5f) matches explicit 0.50f
            state.brightness = 0.5f
            val darkDefault = paint(textBackground = Color(0x262335), draw = draw)
            val darkExplicit50 = paint(textBackground = Color(0x262335), draw = draw)
            assertArrayEquals(pixels(darkDefault), pixels(darkExplicit50))
        }
    }

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    private fun paint(scale: Double = 1.0, textBackground: Color? = Color(0x262335), draw: (Graphics2D) -> Unit): BufferedImage {
        val image = BufferedImage((240 * scale).toInt(), (140 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        fun capture(raw: Graphics2D): Graphics2D = object : Graphics2DDelegate(raw) {
            override fun create(): Graphics = capture(myDelegate.create() as Graphics2D)
            override fun drawGlyphVector(glyphs: GlyphVector, x: Float, y: Float) {
                (paint as? Color)?.let { cores.add(it) }
                myDelegate.drawGlyphVector(glyphs, x, y)
            }
        }
        val raw = image.createGraphics()
        raw.scale(scale, scale)
        raw.setClip(0, 0, 240, 140)
        raw.font = Font("Dialog", Font.PLAIN, 18)
        val g = GlowGraphics2D(capture(raw), atlas, { state }, { powerSave }, imageAtlas = ImageGlowAtlas(), textBackground = textBackground)
        try { draw(g) } finally { g.dispose() }
        return image
    }
}