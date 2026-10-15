package info.chrzanowski.neonglow.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBPanel
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.text.DefaultHighlighter

class UiSelectionGlowTest : BasePlatformTestCase() {
    fun `test single line editor selection has a visible halo on every edge with default glow settings`() = forSelectionStyles { rounded ->
        val state = GlowSettings.State(icons = false)
        val hook = UiGlow(GlyphGlowAtlas(), { state }, { false })
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("First line\nSelected editor text\nThird line"), project) as EditorEx
        try {
            editor.colorsScheme = checkNotNull(EditorColorsManager.getInstance().getScheme("Darcula"))
            editor.settings.isCaretRowShown = false
            val root = JRootPane().also { it.contentPane = editor.component }
            editor.selectionModel.setSelection(13, 25)
            if (rounded) assertEquals(true, editor.javaClass.getMethod("shouldUseNewSelection").invoke(editor))
            assertTrue("Selection glow must be enabled by default", state.selectionGlow)
            val glowing = paint(root)
            state.selectionGlow = false
            val plain = paint(root)
            val start = SwingUtilities.convertPoint(editor.contentComponent, editor.offsetToXY(13), root)
            val end = SwingUtilities.convertPoint(editor.contentComponent, editor.offsetToXY(25), root)
            val edges = mapOf(
                "top" to (start.x + 3 until end.x - 3).map { it to start.y - 2 },
                "bottom" to (start.x + 3 until end.x - 3).map { it to start.y + editor.lineHeight + 1 },
                "left" to (start.y + 3 until start.y + editor.lineHeight - 3).map { start.x - 2 to it },
                "right" to (start.y + 3 until start.y + editor.lineHeight - 3).map { end.x + 1 to it },
            )
            val contrast = edges.mapValues { (_, points) ->
                points.maxOf { (x, y) ->
                    val before = Color(plain.getRGB(x, y))
                    val after = Color(glowing.getRGB(x, y))
                    maxOf(kotlin.math.abs(after.red - before.red), kotlin.math.abs(after.green - before.green),
                        kotlin.math.abs(after.blue - before.blue))
                }
            }
            for ((edge, difference) in contrast) {
                assertTrue("Visible selection halo on $edge (RGB differences: $contrast)", difference >= 8)
            }
        } finally {
            factory.releaseEditor(editor)
            hook.dispose()
        }
    }

    fun `test actual editor selections glow independently of text and stop when cleared`() = forSelectionStyles {
        val settings = GlowSettings.getInstance()
        val factory = EditorFactory.getInstance()
        val hook = UiGlow(GlyphGlowAtlas(), { settings.state }, { false })
        try {
            for (kind in listOf(EditorKind.MAIN_EDITOR, EditorKind.CONSOLE, EditorKind.DIFF)) {
                val editor = factory.createEditor(factory.createDocument("Selected editor text\nSecond line"), project, kind) as EditorEx
                try {
                    val root = JRootPane().also { it.contentPane = editor.component }
                    editor.selectionModel.setSelection(2, 27)
                    settings.loadState(selectionState(false))
                    val plain = paint(root)
                    settings.loadState(selectionState(true))
                    val glowing = paint(root)
                    assertFalse("$kind selection has a halo", pixels(plain).contentEquals(pixels(glowing)))
                    settings.state.editorGlowStrength = 0f
                    assertTrue(pixels(plain).contentEquals(pixels(paint(root))))
                    settings.state.editorGlowStrength = GlowSettings.DEFAULT_EDITOR_STRENGTH
                    settings.state.uiGlowStrength = 0f
                    assertTrue("UI strength does not affect editor selections", pixels(glowing).contentEquals(pixels(paint(root))))
                    settings.state.selectionGlow = false
                    assertTrue("toggling off restores original painting", pixels(plain).contentEquals(pixels(paint(root))))
                    editor.selectionModel.removeSelection()
                    val cleared = paint(root)
                    settings.state.selectionGlow = true
                    assertTrue("no halo without a selection", pixels(cleared).contentEquals(pixels(paint(root))))
                } finally { factory.releaseEditor(editor) }
            }
        } finally {
            hook.dispose()
            settings.loadState(GlowSettings.State())
        }
    }

    fun `test Swing input selections glow and caret repaint listeners are removed on disposal`() {
        val state = selectionState(false)
        val hook = UiGlow(GlyphGlowAtlas(), { state }, { false })
        val fields = listOf(JTextField("Selected input text"), JTextArea("Selected input text\nAnother line"),
            JTextField("Long selected input ".repeat(100)))
        val listenerCounts = fields.map { it.caretListeners.size }
        try {
            for (field in fields) {
                field.background = Color(0x242335)
                field.foreground = Color.WHITE
                field.selectionColor = Color(0x7254b5)
                field.select(2, if (field.document.length > 100) field.document.length - 2 else 12)
                field.caret.isSelectionVisible = true
                val root = JRootPane().also { root ->
                    root.contentPane = JBPanel<JBPanel<*>>(BorderLayout()).also { it.add(field) }
                }
                val plain = paint(root)
                val painter = field.highlighter.highlights.single().painter as DefaultHighlighter.DefaultHighlightPainter
                val nativeColour = painter.color ?: field.selectionColor
                assertTrue("native selection is painted", pixels(plain).any { it == nativeColour.rgb })
                state.selectionGlow = true
                val glowing = paint(root)
                assertFalse("${field.javaClass.simpleName} selection has a halo: ${hook.diagnostics()}; listeners=${field.caretListeners.size}",
                    pixels(plain).contentEquals(pixels(glowing)))
                assertEquals(listenerCounts[fields.indexOf(field)] + 1, field.caretListeners.size)
                state.uiGlowStrength = 0f
                assertTrue("zero UI strength restores native selection", pixels(plain).contentEquals(pixels(paint(root))))
                state.uiGlowStrength = GlowSettings.DEFAULT_STRENGTH
                field.select(0, 0)
                val cleared = paint(root)
                state.selectionGlow = false
                assertTrue(pixels(cleared).contentEquals(pixels(paint(root))))
            }
        } finally { hook.dispose() }
        fields.forEachIndexed { index, field -> assertEquals(listenerCounts[index], field.caretListeners.size) }
    }

    private fun selectionState(enabled: Boolean) = GlowSettings.State(selectionGlow = enabled,
        editorText = false, uiText = false, icons = false, synthwaveStyle = false, regularText = false)

    private fun forSelectionStyles(check: (Boolean) -> Unit) {
        val islands = try { Class.forName("com.intellij.ui.IslandsState") } catch (_: ClassNotFoundException) {
            check(false)
            return
        }
        val companion = islands.getField("Companion").get(null)
        val enabled = companion.javaClass.getMethod("isEnabled").invoke(companion) as Boolean
        val custom = companion.javaClass.getMethod("isCustomEnabled").invoke(companion) as Boolean
        val setEnabled = companion.javaClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        @Suppress("UnresolvedPluginConfigReference") // Only present on platforms exposing IslandsState.
        val legacy = Registry.get("editor.old.full.horizontal.selection.enabled")
        val oldLegacy = legacy.asBoolean()
        try {
            legacy.setValue(false)
            for (rounded in listOf(false, true)) {
                setEnabled.invoke(companion, rounded, custom)
                check(rounded)
            }
        } finally {
            setEnabled.invoke(companion, enabled, custom)
            legacy.setValue(oldLegacy)
        }
    }

    private fun paint(root: JRootPane): BufferedImage {
        fun layout(container: Container) {
            if (container is JComponent) container.isDoubleBuffered = false
            container.doLayout()
            for (child in container.components) if (child is Container) layout(child)
        }
        root.setSize(500, 180)
        layout(root)
        val image = BufferedImage(500, 180, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try { root.paint(g) } finally { g.dispose() }
        return image
    }

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}