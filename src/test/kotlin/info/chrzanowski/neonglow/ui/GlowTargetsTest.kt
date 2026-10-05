package info.chrzanowski.neonglow.ui

import com.intellij.ui.Graphics2DDelegate
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.text.AttributedString

class GlowTargetsTest {

    @Test
    fun `each combination selects only its text and image caches`() {
        for (editor in listOf(false, true)) for (ui in listOf(false, true)) for (icons in listOf(false, true)) {
            val state = GlowSettings.State(editorText = editor, uiText = ui, icons = icons)
            val glyphs = GlyphGlowAtlas()
            val images = ImageGlowAtlas()
            val raw = BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB).createGraphics()
            val g = GlowGraphics2D(raw, glyphs, { state }, { false }, imageAtlas = images)
            try {
                g.color = Color.CYAN
                g.drawString("UI", 30, 30)
                assertEquals(ui, glyphs.size > 0)
                glyphs.clear()
                val scoped = GlowGraphics2D.withTextTarget(Graphics2DDelegate(g), true)
                assertEquals(editor, GlowGraphics2D.isGlowing(scoped))
                scoped.drawString("Editor", 30, 60)
                assertEquals(editor, glyphs.size > 0)
                glyphs.clear()
                g.drawString("Sibling", 30, 90)
                assertEquals("editor scopes must not leak to ordinary siblings", ui, glyphs.size > 0)
                scoped.drawImage(icon(), 90, 30, null)
                assertEquals(icons, images.size > 0)
            } finally { g.dispose() }
        }
    }

    @Test
    fun `scope copies and all text overloads honour the outermost component target`() {
        val state = GlowSettings.State(uiText = false)
        val atlas = GlyphGlowAtlas()
        val raw = BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val g = GlowGraphics2D(raw, atlas, { state }, { false })
        val editor = GlowGraphics2D.withTextTarget(g, true)
        val ui = GlowGraphics2D.withTextTarget(editor, false)
        try {
            val child = editor.create(0, 0, 200, 120) as Graphics2D
            try {
                val draws: List<(Graphics2D) -> Unit> = listOf(
                    { it.drawString("Text", 20, 40) },
                    { it.drawString("Text", 20f, 40f) },
                    { it.drawString(AttributedString("Text").iterator, 20, 40) },
                    { it.drawString(AttributedString("Text").iterator, 20f, 40f) },
                    { it.drawChars("Text".toCharArray(), 0, 4, 20, 40) },
                    { it.drawBytes("Text".toByteArray(), 0, 4, 20, 40) },
                    { it.drawGlyphVector(it.font.createGlyphVector(it.fontRenderContext, "Text"), 20f, 40f) },
                )
                for (draw in draws) {
                    atlas.clear()
                    draw(child)
                    assertTrue(atlas.size > 0)
                    atlas.clear()
                    draw(ui)
                    assertEquals(0, atlas.size)
                }
                state.editorText = false
                assertFalse(GlowGraphics2D.isGlowing(child))
                atlas.clear()
                child.drawString("Off", 20, 40)
                assertEquals(0, atlas.size)
            } finally { child.dispose() }
            state.editorText = true
            state.enabled = false
            assertFalse(GlowGraphics2D.isGlowing(editor))
        } finally { g.dispose() }
    }

    private fun icon(): BufferedImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).also {
        val g = it.createGraphics()
        try { g.color = Color.MAGENTA; g.fillRect(4, 4, 8, 8) } finally { g.dispose() }
    }
}