package info.chrzanowski.neonglow.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.neonglow.editor.EditorGlow
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.SynthwaveTextStyle
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRootPane

class UiGlowTargetsTest : BasePlatformTestCase() {

    fun `test real editor and UI text select independent policies beneath the same root`() {
        val settings = GlowSettings.getInstance()
        settings.loadState(GlowSettings.State(enabled = false))
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("Editor text"), project, EditorKind.MAIN_EDITOR) as EditorEx
        val root = JRootPane()
        val label = JLabel("UI text").also {
            it.foreground = Color.CYAN
            it.preferredSize = Dimension(800, 60)
        }
        root.contentPane = JPanel(BorderLayout()).also {
            it.add(label, BorderLayout.NORTH)
            it.add(editor.component, BorderLayout.CENTER)
        }
        root.setSize(800, 400)
        val glow = UiGlow(GlyphGlowAtlas(), { settings.state }, { false })
        try {
            glow.installRoot(root)
            val plain = paint(root)
            for (editorText in listOf(false, true)) for (uiText in listOf(false, true)) {
                settings.loadState(GlowSettings.State(editorText = editorText, uiText = uiText, icons = false))
                val image = paint(root)
                assertEquals("UI text selection", uiText, differs(plain, image, 0, 60))
                assertEquals("editor text selection", editorText, differs(plain, image, 60, 400))
                assertEquals("the fallback must not double-paint intercepted editor glyphs", 0,
                    EditorGlow.of(editor)!!.renderer.lastGlyphCount)
            }
        } finally {
            glow.dispose()
            factory.releaseEditor(editor)
            settings.loadState(GlowSettings.State())
        }
    }

    private fun paint(root: JRootPane): BufferedImage {
        fun layout(component: java.awt.Container) {
            if (component is JComponent) component.isDoubleBuffered = false
            component.doLayout()
            for (child in component.components) if (child is java.awt.Container) layout(child)
        }
        layout(root)
        val image = BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try { root.paint(g) } finally { g.dispose() }
        return image
    }

    fun `test SynthWave replaces real editor and label cores without modifying the editor scheme`() {
        val settings = GlowSettings.getInstance()
        settings.loadState(GlowSettings.State(enabled = false))
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("Neon editor"), project, EditorKind.MAIN_EDITOR) as EditorEx
        val source = Color(0x36f9f6)
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(source, Color(0x262335), null, null, 0))
        editor.colorsScheme = scheme
        val root = JRootPane()
        root.contentPane = JPanel(BorderLayout()).also {
            it.background = Color(0x262335)
            it.add(JLabel("Neon label").also { label ->
                label.foreground = source
                label.preferredSize = Dimension(800, 60)
            }, BorderLayout.NORTH)
            it.add(editor.component, BorderLayout.CENTER)
        }
        root.setSize(800, 400)
        val glow = UiGlow(GlyphGlowAtlas(), { settings.state }, { false })
        try {
            glow.installRoot(root)
            settings.loadState(GlowSettings.State(synthwaveStyle = true, brightness = 0.45f, intensity = 1f, icons = false))
            val image = paint(root)
            val core = Color(0xfdfdfd).rgb
            assertTrue("label core replacement", (0 until 60).any { y -> (0 until 800).any { x -> image.getRGB(x, y) == core } })
            assertTrue("editor core replacement", (60 until 400).any { y -> (0 until 800).any { x -> image.getRGB(x, y) == core } })
            assertEquals(source, scheme.defaultForeground)
            assertEquals("intercepted editor must not paint fallback shadows", 0, EditorGlow.of(editor)!!.renderer.lastGlyphCount)
        } finally {
            glow.dispose()
            factory.releaseEditor(editor)
            settings.loadState(GlowSettings.State())
        }
    }

    fun `test adaptive style respects dark and light editor and UI backdrops independently`() {
        val settings = GlowSettings.getInstance()
        val factory = EditorFactory.getInstance()
        val source = Color(0xcc7832)
        for (editorDark in listOf(false, true)) for (uiDark in listOf(false, true)) {
            settings.loadState(GlowSettings.State(synthwaveStyle = true, brightness = 0.45f, intensity = 1f, icons = false))
            val editor = factory.createEditor(factory.createDocument("Adaptive editor"), project, EditorKind.MAIN_EDITOR) as EditorEx
            val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
            scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(source, if (editorDark) Color(0x2b2b2b) else Color.WHITE, null, null, 0))
            editor.colorsScheme = scheme
            val root = JRootPane()
            root.contentPane = JPanel(BorderLayout()).also {
                it.background = if (uiDark) Color(0x2b2b2b) else Color.WHITE
                it.add(JLabel("Adaptive label").also { label ->
                    label.foreground = source
                    label.preferredSize = Dimension(800, 60)
                }, BorderLayout.NORTH)
                it.add(editor.component, BorderLayout.CENTER)
            }
            root.setSize(800, 400)
            val glow = UiGlow(GlyphGlowAtlas(), { settings.state }, { false })
            try {
                glow.installRoot(root)
                val image = paint(root)
                val bright = Color(SynthwaveTextStyle.rule(source.rgb, 0x2b2b2b)!!.foregroundRgb).rgb
                fun hasCore(core: Int, from: Int, to: Int): Boolean =
                    (from until to).any { y -> (0 until 800).any { x -> image.getRGB(x, y) == core } }
                assertTrue("label backdrop", hasCore(if (uiDark) bright else source.rgb, 0, 60))
                assertTrue("editor backdrop", hasCore(if (editorDark) bright else source.rgb, 60, 400))
                assertFalse("no bright core on a light label", !uiDark && hasCore(bright, 0, 60))
                assertFalse("no bright core in a light editor", !editorDark && hasCore(bright, 60, 400))
                assertEquals(source, scheme.defaultForeground)
                assertEquals(0, EditorGlow.of(editor)!!.renderer.lastGlyphCount)
            } finally {
                glow.dispose()
                factory.releaseEditor(editor)
                settings.loadState(GlowSettings.State())
            }
        }
    }

    private fun differs(plain: BufferedImage, image: BufferedImage, fromY: Int, toY: Int): Boolean =
        (fromY until toY).any { y -> (0 until image.width).any { x -> plain.getRGB(x, y) != image.getRGB(x, y) } }
}