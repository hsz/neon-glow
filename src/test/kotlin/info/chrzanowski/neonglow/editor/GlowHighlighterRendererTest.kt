package info.chrzanowski.neonglow.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.Graphics2DDelegate
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import kotlin.math.abs

class GlowHighlighterRendererTest : BasePlatformTestCase() {

    private lateinit var settings: GlowSettings
    private lateinit var manager: GlowManager

    override fun setUp() {
        super.setUp()
        settings = GlowSettings.getInstance()
        manager = GlowManager.getInstance()
        settings.loadState(GlowSettings.State())
        manager.atlas.clear()
    }

    override fun tearDown() {
        try {
            settings.loadState(GlowSettings.State())
            manager.atlas.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun withEditor(text: String, block: (EditorEx, EditorGlow) -> Unit) {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(text), project, EditorKind.MAIN_EDITOR) as EditorEx
        try {
            editor.component.setSize(800, 400)
            editor.contentComponent.setSize(800, 400)
            block(editor, EditorGlow.of(editor)!!)
        } finally {
            factory.releaseEditor(editor)
        }
    }

    /** Paints the glow into a fresh image through the given user-space scale and returns the image. */
    private fun paint(editor: EditorEx, glow: EditorGlow, scale: Double = 1.0, width: Int = 400, height: Int = 160): BufferedImage {
        val image = BufferedImage((width * scale).toInt(), (height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(scale, scale)
            g.clip = Rectangle(0, 0, width, height)
            glow.renderer.paint(editor, glow.highlighter!!, g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun paintedBounds(image: BufferedImage): Rectangle? {
        var bounds: Rectangle? = null
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (image.getRGB(x, y) ushr 24 == 0) continue
            val pixel = Rectangle(x, y, 1, 1)
            bounds = bounds?.union(pixel) ?: pixel
        }
        return bounds
    }

    fun `test glyphs get a halo around their bounds and nothing far away`() = withEditor("glow me") { editor, glow ->
        val image = paint(editor, glow)
        val painted = paintedBounds(image)

        assertNotNull("something must be painted", painted)
        assertEquals(6, glow.renderer.lastGlyphCount)
        val pad = GlowHighlighterRenderer.repaintInflation(settings.state.radiusPx)
        val textStart = editor.offsetToXY(0).x
        val textEnd = editor.offsetToXY(editor.document.textLength).x
        assertTrue("halo starts near the text start: $painted vs $textStart", painted!!.x >= textStart - pad && painted.x <= textStart + 2)
        assertTrue("halo ends near the text end: $painted vs $textEnd", painted.maxX <= textEnd + pad && painted.maxX >= textEnd - pad)
        assertTrue("halo spills a little above the line: $painted", painted.y <= editor.ascent)
        assertTrue("halo stays within the padded line: $painted", painted.maxY <= editor.lineHeight + pad)
        assertTrue("the hot core is opaque-ish", (0 until image.width).any { x -> image.getRGB(x, editor.ascent - 2) ushr 24 > 100 })
        assertTrue("fresh atlas filled on first paint", manager.atlas.misses in 1..6)

        val again = paint(editor, glow)
        assertEquals(6, glow.renderer.lastGlyphCount)
        assertTrue("second paint hits the atlas", manager.atlas.hits >= 6)
        assertEquals(painted, paintedBounds(again))
    }

    fun `test fallback editor strength affects opacity and zero performs no work`() = withEditor("glow") { editor, glow ->
        val full = paint(editor, glow)
        val count = manager.atlas.size
        settings.state.editorGlowStrength = 0.25f
        val dim = paint(editor, glow)
        assertEquals(count, manager.atlas.size)
        var changed = false
        for (y in 0 until full.height) for (x in 0 until full.width) {
            val a = full.getRGB(x, y) ushr 24
            val b = dim.getRGB(x, y) ushr 24
            assertTrue(b <= a)
            if (a != b) changed = true
        }
        assertTrue(changed)
        manager.atlas.clear()
        settings.state.editorGlowStrength = 0f
        assertNull(paintedBounds(paint(editor, glow)))
        assertEquals(0, glow.renderer.lastGlyphCount)
        assertEquals(0L, manager.atlas.misses)
    }

    fun `test glow is painted in the token foreground colour`() = withEditor("colour") { editor, glow ->
        val image = paint(editor, glow)
        var best = 0
        var bestAlpha = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            val alpha = argb ushr 24
            if (alpha > bestAlpha) {
                bestAlpha = alpha
                best = argb
            }
        }
        assertTrue("some pixel is clearly visible: $bestAlpha", bestAlpha >= 60)
        // Un-premultiplying rounds by up to 255 / alpha / 2 per channel.
        val expected = editor.colorsScheme.defaultForeground
        assertTrue(abs(((best shr 16) and 0xFF) - expected.red) <= 4)
        assertTrue(abs(((best shr 8) and 0xFF) - expected.green) <= 4)
        assertTrue(abs((best and 0xFF) - expected.blue) <= 4)
    }

    fun `test device scale two paints device-pixel masks at doubled positions`() = withEditor("hidpi") { editor, glow ->
        val at1x = paintedBounds(paint(editor, glow))!!
        val at2x = paintedBounds(paint(editor, glow, scale = 2.0))!!

        assertTrue("$at1x vs $at2x", abs(at2x.x - 2 * at1x.x) <= 3)
        assertTrue("$at1x vs $at2x", abs(at2x.maxX - 2 * at1x.maxX) <= 3)
        assertTrue("$at1x vs $at2x", abs(at2x.y - 2 * at1x.y) <= 3)
        assertTrue("$at1x vs $at2x", abs(at2x.maxY - 2 * at1x.maxY) <= 3)
        assertTrue("masks for both scales are cached separately", manager.atlas.size >= 2 * 4)
    }

    fun `test disabled glow paints nothing`() = withEditor("dark") { editor, glow ->
        settings.state.enabled = false
        val image = paint(editor, glow)
        assertNull(paintedBounds(image))
        assertEquals(0, glow.renderer.lastGlyphCount)
        assertEquals(0L, manager.atlas.misses)
    }

    fun `test editor target controls the fallback independently of UI text and icons`() = withEditor("editor") { editor, glow ->
        settings.state.editorText = false
        assertNull(paintedBounds(paint(editor, glow)))
        assertEquals(0, glow.renderer.lastGlyphCount)
        WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "x") }
        assertEquals(0, glow.bleedRepaints)
        settings.state.editorText = true
        settings.state.uiText = false
        settings.state.icons = false
        assertNotNull(paintedBounds(paint(editor, glow)))
        assertEquals(7, glow.renderer.lastGlyphCount)
    }

    fun `test brightness fades fallback halos and zero avoids painting and bleed repaints`() = withEditor("Brightness") { editor, glow ->
        fun alphaSum(image: BufferedImage): Long = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            .sumOf { (it ushr 24).toLong() }
        val blits = mutableListOf<Pair<Image, AffineTransform>>()
        fun capture(graphics: Graphics2D): Graphics2D = object : Graphics2DDelegate(graphics) {
            override fun create(): Graphics = capture(myDelegate.create() as Graphics2D)
            override fun drawImage(image: Image?, transform: AffineTransform?, observer: ImageObserver?): Boolean {
                blits += checkNotNull(image) to AffineTransform(checkNotNull(transform))
                return myDelegate.drawImage(image, transform, observer)
            }
        }
        val full = BufferedImage(400, 160, BufferedImage.TYPE_INT_ARGB)
        val captured = capture(full.createGraphics())
        try {
            captured.clip = Rectangle(0, 0, 400, 160)
            glow.renderer.paint(editor, glow.highlighter!!, captured)
        } finally { captured.dispose() }
        assertEquals(10, blits.size)
        val misses = manager.atlas.misses
        settings.state.brightness = 0.45f
        val dim = paint(editor, glow)
        assertEquals(misses, manager.atlas.misses)
        val expected = BufferedImage(400, 160, BufferedImage.TYPE_INT_ARGB)
        val reference = expected.createGraphics()
        try {
            reference.composite = AlphaComposite.SrcOver.derive(0.45f)
            for ((mask, transform) in blits) reference.drawImage(mask, transform, null)
        } finally { reference.dispose() }
        assertTrue(alphaSum(dim) > 0 && alphaSum(dim) < alphaSum(full))
        // Overlapping glyphs compose separately, not as one finished layer with a linear alpha sum.
        assertTrue(expected.getRGB(0, 0, 400, 160, null, 0, 400)
            .contentEquals(dim.getRGB(0, 0, 400, 160, null, 0, 400)))
        val image = BufferedImage(400, 160, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.clip = Rectangle(0, 0, 400, 160)
            val composite = g.composite
            glow.renderer.paint(editor, glow.highlighter!!, g)
            assertEquals("painting must not fade the caller's subsequent text", composite, g.composite)
        } finally { g.dispose() }
        settings.state.brightness = 0f
        manager.atlas.clear()
        assertNull(paintedBounds(paint(editor, glow)))
        assertEquals(0, glow.renderer.lastGlyphCount)
        assertEquals(0, manager.atlas.size)
        WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "x") }
        assertEquals(0, glow.bleedRepaints)
    }

    fun `test empty and blank documents paint nothing without errors`() {
        withEditor("") { editor, glow ->
            assertNull(paintedBounds(paint(editor, glow)))
            assertEquals(0, glow.renderer.lastGlyphCount)
        }
        withEditor("   \n\t\n  ") { editor, glow ->
            assertNull(paintedBounds(paint(editor, glow)))
            assertEquals(0, glow.renderer.lastGlyphCount)
            assertEquals(0L, manager.atlas.misses)
        }
    }

    fun `test clip below the document paints nothing`() = withEditor("top") { editor, glow ->
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.clip = Rectangle(0, 300, 400, 100)
            glow.renderer.paint(editor, glow.highlighter!!, g)
        } finally {
            g.dispose()
        }
        assertNull(paintedBounds(image))
        assertEquals(0, glow.renderer.lastGlyphCount)
    }

    fun `test tabs and multi-line tokens split into painted segments`() = withEditor("a\tb\nc") { editor, glow ->
        paint(editor, glow)
        assertEquals(3, glow.renderer.lastGlyphCount)
        assertEquals(0, glow.renderer.lastFallbackSegments)
    }

    fun `test edits trigger an inflated bleed repaint`() = withEditor("edit me") { editor, glow ->
        assertEquals(0, glow.bleedRepaints)
        WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "x") }
        assertEquals(1, glow.bleedRepaints)
    }

    fun `test SynthWave fallback retains ordinary text halos and brightness semantics`() = withEditor("ordinary") { editor, glow ->
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color(0xf0eaf7), null, null, null, 0))
        editor.colorsScheme = scheme
        settings.state.brightness = 0.45f
        settings.state.editorGlowStrength = 0.65f
        val original = paint(editor, glow)
        assertNotNull(paintedBounds(original))
        manager.atlas.clear()
        settings.state.synthwaveStyle = true
        val styled = paint(editor, glow)
        assertNotNull("ordinary theme colours must retain glow", paintedBounds(styled))
        assertEquals(8, glow.renderer.lastGlyphCount)
        assertTrue(original.getRGB(0, 0, 400, 160, null, 0, 400)
            .contentEquals(styled.getRGB(0, 0, 400, 160, null, 0, 400)))
        settings.state.brightness = 0f
        assertNull(paintedBounds(paint(editor, glow)))
    }

    fun `test SynthWave fallback shares layered cached masks`() = withEditor("neon") { editor, glow ->
        settings.state.synthwaveStyle = true
        settings.state.brightness = 0.45f
        settings.state.intensity = 1f
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        editor.colorsScheme = scheme
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color(0x36f9f6), Color(0x262335), null, null, 0))
        assertNotNull(paintedBounds(paint(editor, glow)))
        assertEquals(4, glow.renderer.lastGlyphCount)
        val misses = manager.atlas.misses
        paint(editor, glow)
        assertEquals(misses, manager.atlas.misses)
        assertTrue(manager.atlas.hits >= 4)
        settings.state.brightness = 1f
        paint(editor, glow)
        assertTrue(manager.atlas.misses > misses)
        assertEquals(Color(0x36f9f6), scheme.defaultForeground)
        assertTrue(GlowHighlighterRenderer.repaintInflation(6f, true) > GlowHighlighterRenderer.repaintInflation(6f))
    }

    fun `test fallback regular text switch skips ordinary halos and retains eligible layered halos`() = withEditor("neon") { editor, glow ->
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        editor.colorsScheme = scheme
        settings.state.regularText = false
        for (style in listOf(false, true)) for (source in listOf(0xf0eaf7, 0x36f9f6, 0xcc7832)) {
            for (background in listOf(Color(0x262335), Color.WHITE)) {
                manager.atlas.clear()
                settings.state.synthwaveStyle = style
                scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color(source), background, null, null, 0))
                val eligible = style && (if (background == Color.WHITE) source == 0xcc7832 else source != 0xf0eaf7)
                assertEquals(eligible, paintedBounds(paint(editor, glow)) != null)
                assertEquals(if (eligible) 4 else 0, glow.renderer.lastGlyphCount)
                assertEquals(eligible, manager.atlas.size > 0)
                assertEquals(Color(source), scheme.defaultForeground)
            }
        }
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color(0xf0eaf7), Color(0x262335), null, null, 0))
        settings.state.regularText = true
        assertNotNull(paintedBounds(paint(editor, glow)))
        val misses = manager.atlas.misses
        settings.state.regularText = false
        assertNull(paintedBounds(paint(editor, glow)))
        assertEquals(0, glow.renderer.lastGlyphCount)
        assertEquals(misses, manager.atlas.misses)
    }

    fun `test light fallback keeps syntax halos with regular text off and preserves scheme colours`() = withEditor("code") { editor, glow ->
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        editor.colorsScheme = scheme
        settings.state.synthwaveStyle = true
        settings.state.regularText = false
        for (source in listOf(0x000080, 0x008000, 0x795e26, 0x7a3e9d, 0xaa0000)) {
            manager.atlas.clear()
            scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color(source), Color.WHITE, null, null, 0))
            assertNotNull("coloured syntax should glow on light schemes", paintedBounds(paint(editor, glow)))
            assertEquals(4, glow.renderer.lastGlyphCount)
            assertEquals(Color(source), scheme.defaultForeground)
            val misses = manager.atlas.misses
            paint(editor, glow)
            assertEquals(misses, manager.atlas.misses)
            settings.state.synthwaveStyle = false
            assertNull(paintedBounds(paint(editor, glow)))
            settings.state.synthwaveStyle = true
        }
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color.BLACK, Color.WHITE, null, null, 0))
        assertNull(paintedBounds(paint(editor, glow)))
    }

    fun `test fallback adapts ordinary theme halos on dark and light schemes without modifying source colours`() = withEditor("neon") { editor, glow ->
        settings.state.brightness = 0.45f
        settings.state.intensity = 1f
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        editor.colorsScheme = scheme
        for (background in listOf(Color(0x2b2b2b), Color.WHITE)) {
            scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color(0xcc7832), background, null, null, 0))
            settings.state.synthwaveStyle = false
            val original = paint(editor, glow)
            settings.state.synthwaveStyle = true
            val styled = paint(editor, glow)
            assertNotNull(paintedBounds(styled))
            assertEquals(4, glow.renderer.lastGlyphCount)
            assertFalse(original.getRGB(0, 0, 400, 160, null, 0, 400)
                .contentEquals(styled.getRGB(0, 0, 400, 160, null, 0, 400)))
            val misses = manager.atlas.misses
            paint(editor, glow)
            assertEquals(misses, manager.atlas.misses)
            assertEquals(Color(0xcc7832), scheme.defaultForeground)
        }
    }
}
