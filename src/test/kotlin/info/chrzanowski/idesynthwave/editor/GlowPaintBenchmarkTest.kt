package info.chrzanowski.idesynthwave.editor

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.idesynthwave.GlowManager
import info.chrzanowski.idesynthwave.settings.GlowSettings
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Not a correctness test: paints a realistic viewport (40 lines, ~2 300 glyphs) through the real editor component
 * into an offscreen image (software pipeline) at 1x and 2x, with the glow disabled and enabled, and prints the
 * paint costs. The numbers feed `docs/glow-investigation.md`; the only assertions are generous upper bounds that
 * catch pathological regressions.
 */
class GlowPaintBenchmarkTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        GlowSettings.getInstance().loadState(GlowSettings.State())
        GlowManager.getInstance().atlas.clear()
    }

    override fun tearDown() {
        try {
            GlowSettings.getInstance().loadState(GlowSettings.State())
            GlowManager.getInstance().atlas.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test full viewport paint cost at 1x and 2x`() {
        val text = buildString {
            val lines = listOf(
                "class GlowHighlighterRenderer(private val atlas: GlyphGlowAtlas, private val settings: GlowSettings) {",
                "    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {",
                "        val state = settings.state // lexer colours only, semantic highlighting is a follow-up",
                "        if (!state.enabled || PowerSaveMode.isEnabled()) return",
                "        val mask = atlas.find(key) ?: atlas.render(key, layout.outline(i))",
                "        g2.drawImage(mask.image, deviceX, deviceY, null) // 0x1F2937 \"neon\" 42 3.14f",
                "    }",
                "}",
            )
            repeat(30) { for (line in lines) append(line).append('\n') }
        }
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(text), project, EditorKind.MAIN_EDITOR) as EditorEx
        try {
            val width = 1200
            val height = 900
            editor.component.setSize(width, height)
            editor.contentComponent.setSize(width, height)
            val glow = EditorGlow.of(editor)!!
            val settings = GlowSettings.getInstance()
            println(
                "synthwave benchmark: ${editor.lineHeight}px lines, ${height / editor.lineHeight} visible lines, " +
                    "font ${editor.colorsScheme.editorFontName} ${editor.colorsScheme.editorFontSize2D}, " +
                    "radius ${settings.state.radiusPx}px, intensity ${settings.state.intensity}",
            )

            for (scale in doubleArrayOf(1.0, 2.0)) {
                val image = BufferedImage((width * scale).toInt(), (height * scale).toInt(), BufferedImage.TYPE_INT_ARGB_PRE)
                fun paintEditor(): Long = withGraphics(image, scale, width, height) { g ->
                    val start = System.nanoTime()
                    editor.contentComponent.paint(g)
                    (System.nanoTime() - start) / 1_000
                }
                fun paintGlowOnly(): Long = withGraphics(image, scale, width, height) { g ->
                    val start = System.nanoTime()
                    glow.renderer.paint(editor, glow.highlighter!!, g)
                    (System.nanoTime() - start) / 1_000
                }

                settings.state.enabled = false
                paintEditor() // lets the editor adopt the graphics' font render context, as in the IDE
                val editorOnly = LongArray(5) { paintEditor() }.sorted()

                settings.state.enabled = true
                GlowManager.getInstance().atlas.clear()
                val coldGlow = paintGlowOnly()
                val warmGlow = LongArray(5) { paintGlowOnly() }.sorted()
                val fullGlyphs = glow.renderer.lastGlyphCount
                val fullFallback = glow.renderer.lastFallbackSegments
                val editorWithGlow = LongArray(5) { paintEditor() }.sorted()
                val atlas = GlowManager.getInstance().atlas
                // A typing repaint: the editor repaints the edited line, we add the bleed padding above and below.
                val pad = GlowHighlighterRenderer.repaintInflation(settings.state.radiusPx)
                val lineClip = Rectangle(0, 10 * editor.lineHeight - pad, width, editor.lineHeight + 2 * pad)
                val lineGlow = LongArray(5) {
                    withGraphics(image, scale, width, height, lineClip) { g ->
                        val start = System.nanoTime()
                        glow.renderer.paint(editor, glow.highlighter!!, g)
                        (System.nanoTime() - start) / 1_000
                    }
                }.sorted()
                val lineGlyphs = glow.renderer.lastGlyphCount

                println(
                    "synthwave benchmark @${scale}x: glyphs=$fullGlyphs fallback segments=$fullFallback | " +
                        "editor only median=${editorOnly[2]}µs | glow only cold=${coldGlow}µs warm min=${warmGlow.first()}µs median=${warmGlow[2]}µs | " +
                        "editor+glow median=${editorWithGlow[2]}µs | one line ($lineGlyphs glyphs) glow median=${lineGlow[2]}µs | " +
                        "atlas size=${atlas.size} ${atlas.bytes / 1024}KB misses=${atlas.misses}",
                )
                assertTrue(fullGlyphs > 1000)
                assertTrue("warm glow paint took ${warmGlow.first()}µs", warmGlow.first() < 200_000)
            }
        } finally {
            factory.releaseEditor(editor)
        }
    }

    private fun <T> withGraphics(
        image: BufferedImage, scale: Double, width: Int, height: Int, clip: Rectangle = Rectangle(0, 0, width, height), block: (Graphics2D) -> T,
    ): T {
        val g = image.createGraphics()
        try {
            g.scale(scale, scale)
            g.clip = clip
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.editorFractionalMetricsHint)
            return block(g)
        } finally {
            g.dispose()
        }
    }
}
