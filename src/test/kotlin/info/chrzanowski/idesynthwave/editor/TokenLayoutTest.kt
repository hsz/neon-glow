package info.chrzanowski.idesynthwave.editor

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font
import java.awt.font.FontRenderContext
import kotlin.math.abs

class TokenLayoutTest : BasePlatformTestCase() {

    private fun withEditor(text: String, block: (EditorEx, Font, FontRenderContext) -> Unit) {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(text), project, EditorKind.MAIN_EDITOR) as EditorEx
        try {
            editor.component.setSize(800, 600)
            val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            block(editor, font, TokenLayout.editorFontRenderContext(FontInfo.getFontRenderContext(editor.contentComponent)))
        } finally {
            factory.releaseEditor(editor)
        }
    }

    fun `test plain ascii token takes the fast path and matches the editor positions`() = withEditor("foo bar_baz;") { editor, font, frc ->
        val layout = TokenLayout()
        val text = editor.document.immutableCharSequence
        val count = layout.layout(editor, text, 0, text.length, font, Font.PLAIN, frc)

        assertEquals(text.length, count)
        assertFalse(layout.usedFallback)
        val baseline = editor.offsetToPoint2D(0).y + editor.ascent
        for (i in 0 until count) {
            val expected = editor.offsetToPoint2D(i)
            assertTrue("glyph $i x=${layout.xs[i]} expected ${expected.x}", abs(layout.xs[i] - expected.x) <= 0.5)
            assertEquals(baseline.toFloat(), layout.ys[i], 0.01f)
            assertSame(font, layout.fonts[i])
            assertEquals(text[i].isWhitespace(), layout.blank[i])
        }
        assertEquals(font.createGlyphVector(frc, "f").getGlyphCode(0), layout.codes[0])
        val outline = layout.outline(0)
        assertFalse(outline.bounds2D.isEmpty)
        assertTrue("outline is relative to the glyph origin (above the baseline)", outline.bounds2D.maxY <= 1.0)
        assertTrue(outline.bounds2D.minX >= -1.0 && outline.bounds2D.minX < 2.0)
    }

    fun `test surrogate pairs yield one visible glyph per code point`() = withEditor("a\uD83D\uDE00b") { editor, font, frc ->
        val layout = TokenLayout()
        val text = editor.document.immutableCharSequence

        val count = layout.layout(editor, text, 0, text.length, font, Font.PLAIN, frc)

        val visible = (0 until count).filter { !layout.blank[it] }
        assertEquals("a, the emoji and b", 3, visible.size)
        if (!layout.usedFallback) {
            // Fast path: one glyph per char, the low surrogate being an invisible zero-advance glyph.
            assertEquals(4, count)
            assertTrue(layout.blank[2])
        }
        assertEquals(editor.offsetToPoint2D(0).x.toFloat(), layout.xs[visible[0]], 0.5f)
        assertEquals(editor.offsetToPoint2D(1).x.toFloat(), layout.xs[visible[1]], 0.5f)
        assertEquals(editor.offsetToPoint2D(3).x.toFloat(), layout.xs[visible[2]], 0.5f)
        // Colour emoji are bitmap glyphs without an outline (no glow); the letters do have one.
        assertFalse(layout.outline(visible[0]).bounds2D.isEmpty)
        assertFalse(layout.outline(visible[2]).bounds2D.isEmpty)
    }

    fun `test soft wrap inside a segment forces the per-character path`() = withEditor("x".repeat(40)) { editor, font, frc ->
        EditorTestUtil.configureSoftWraps(editor, 10)
        val text = editor.document.immutableCharSequence
        val wrapped = editor.offsetToPoint2D(0).y != editor.offsetToPoint2D(text.length).y
        assertTrue("the test line must be soft-wrapped", wrapped)

        val layout = TokenLayout()
        val count = layout.layout(editor, text, 0, text.length, font, Font.PLAIN, frc)

        assertEquals(text.length, count)
        assertTrue(layout.usedFallback)
        assertTrue("later glyphs sit on a lower visual line", layout.ys[count - 1] > layout.ys[0])
        for (i in 0 until count) {
            val expected = editor.offsetToPoint2D(i, true, false)
            assertEquals(expected.x.toFloat(), layout.xs[i], 0.5f)
        }
    }

    fun `test empty segment yields nothing`() = withEditor("abc") { editor, font, frc ->
        val layout = TokenLayout()
        assertEquals(0, layout.layout(editor, editor.document.immutableCharSequence, 1, 1, font, Font.PLAIN, frc))
        assertEquals(0, layout.count)
    }
}
