package info.chrzanowski.idesynthwave.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.idesynthwave.GlowManager
import info.chrzanowski.idesynthwave.settings.GlowSettings
import java.awt.Rectangle
import java.awt.image.BufferedImage
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
}
