package info.chrzanowski.neonglow.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.editor.EditorGlow
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Container
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.RepaintManager
import javax.swing.SwingUtilities

class UiEditorGlowTest : BasePlatformTestCase() {

    fun `test actual editor text receives exactly one glow through the UI hook`() {
        val settings = GlowSettings.getInstance()
        val manager = GlowManager.getInstance()
        settings.loadState(GlowSettings.State(enabled = false, synthwaveStyle = false, regularText = true))
        val factory = EditorFactory.getInstance()
        val editor =
            factory.createEditor(factory.createDocument("Editor glow"), project, EditorKind.MAIN_EDITOR) as EditorEx
        val root = JRootPane()
        root.contentPane = editor.component
        val glow = UiGlow(manager.atlas, { settings.state }, { false })
        try {
            val plain = paint(root)
            settings.state.enabled = true
            manager.atlas.clear()
            val glowing = paint(root)
            assertTrue("editor text drawing populates the UI atlas", manager.atlas.size > 0)
            assertEquals(
                "the highlighter must not double the intercepted text glow",
                0,
                EditorGlow.of(editor)!!.renderer.lastGlyphCount,
            )
            assertFalse("the actual editor painting gains halos", pixels(plain).contentEquals(pixels(glowing)))
        } finally {
            glow.dispose()
            factory.releaseEditor(editor)
            settings.loadState(GlowSettings.State())
            manager.atlas.clear()
        }
    }

    fun `test auxiliary editors receive UI glow without a main editor highlighter`() {
        val settings = GlowSettings.getInstance()
        val manager = GlowManager.getInstance()
        settings.loadState(GlowSettings.State(synthwaveStyle = false, regularText = true))
        val factory = EditorFactory.getInstance()
        val editor =
            factory.createEditor(factory.createDocument("Console glow"), project, EditorKind.CONSOLE) as EditorEx
        val root = JRootPane()
        root.contentPane = editor.component
        val glow = UiGlow(manager.atlas, { settings.state }, { false })
        try {
            assertNull(EditorGlow.of(editor))
            manager.atlas.clear()
            paint(root)
            assertTrue("console text is no longer excluded", manager.atlas.size > 0)
        } finally {
            glow.dispose()
            factory.releaseEditor(editor)
            settings.loadState(GlowSettings.State())
            manager.atlas.clear()
        }
    }

    fun `test console and diff text obey the editor choice rather than UI text`() {
        val settings = GlowSettings.getInstance()
        val atlas = info.chrzanowski.neonglow.render.GlyphGlowAtlas()
        val factory = EditorFactory.getInstance()
        val glow = UiGlow(atlas, { settings.state }, { false })
        try {
            for (kind in listOf(EditorKind.CONSOLE, EditorKind.DIFF)) {
                val editor = factory.createEditor(factory.createDocument("Auxiliary glow"), project, kind) as EditorEx
                try {
                    val root = JRootPane()
                    root.contentPane = editor.component
                    settings.loadState(GlowSettings.State(enabled = false))
                    val plain = paint(root)
                    settings.loadState(GlowSettings.State(editorText = false, icons = false,
                        synthwaveStyle = false, regularText = true))
                    atlas.clear()
                    assertTrue(pixels(plain).contentEquals(pixels(paint(root))))
                    assertEquals(0, atlas.size)
                    settings.loadState(GlowSettings.State(uiText = false, icons = false,
                        synthwaveStyle = false, regularText = true))
                    assertFalse(pixels(plain).contentEquals(pixels(paint(root))))
                    assertTrue(atlas.size > 0)
                    assertNull(EditorGlow.of(editor))
                } finally { factory.releaseEditor(editor) }
            }
        } finally {
            glow.dispose()
            settings.loadState(GlowSettings.State())
        }
    }

    fun `test the UI hook leaves the Swing RepaintManager untouched`() {
        val settings = GlowSettings.getInstance()
        val manager = GlowManager.getInstance()
        val before = RepaintManager.currentManager(null)
        val glow = UiGlow(manager.atlas, { settings.state }, { false })
        try {
            assertSame("replacing the RepaintManager desynchronises window buffers and makes components flash",
                before, RepaintManager.currentManager(null))
        } finally {
            glow.dispose()
        }
        assertSame(before, RepaintManager.currentManager(null))
    }

    fun `test partial editor repaints get the neighbouring halos from the highlighter under the UI hook`() {
        val settings = GlowSettings.getInstance()
        val manager = GlowManager.getInstance()
        settings.loadState(GlowSettings.State(synthwaveStyle = false, regularText = true, radiusPx = 6f))
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("line one\nline two\nline three"), project,
            EditorKind.MAIN_EDITOR) as EditorEx
        val root = JRootPane()
        root.contentPane = editor.component
        val glow = UiGlow(manager.atlas, { settings.state }, { false })
        try {
            val renderer = EditorGlow.of(editor)!!.renderer
            root.setSize(500, 200)
            layout(root)
            val content = editor.contentComponent
            val caretColumn = SwingUtilities.convertRectangle(
                content, Rectangle(content.width / 2, editor.visualLineToY(1), 4, editor.lineHeight), root)

            paint(root, caretColumn)
            assertTrue("the hook keeps glowing the drawn line itself", manager.atlas.size > 0)
            assertEquals("the highlighter adds the halos of the two lines the editor did not draw",
                "line one".count { !it.isWhitespace() } + "line three".count { !it.isWhitespace() },
                renderer.lastGlyphCount)

            paint(root)
            assertEquals("a full repaint is glowed by the hook alone", 0, renderer.lastGlyphCount)
        } finally {
            glow.dispose()
            factory.releaseEditor(editor)
            settings.loadState(GlowSettings.State())
            manager.atlas.clear()
        }
    }

    /** Paints [root] at 500×200 into a fresh image, optionally clipped to [clip] (root coordinates). */
    private fun paint(root: JRootPane, clip: Rectangle? = null): BufferedImage {
        root.setSize(500, 200)
        layout(root)
        val image = BufferedImage(500, 200, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            if (clip != null) g.clip = clip
            root.paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun layout(container: Container) {
        if (container is JComponent) container.isDoubleBuffered = false
        container.doLayout()
        for (child in container.components) if (child is Container) layout(child)
    }

    private fun pixels(image: BufferedImage): IntArray =
        image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}