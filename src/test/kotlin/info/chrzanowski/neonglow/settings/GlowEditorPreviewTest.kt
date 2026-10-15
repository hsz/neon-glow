package info.chrzanowski.neonglow.settings

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.ui.GlowGraphics2D
import info.chrzanowski.neonglow.ui.UiGlow
import java.awt.Color
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JRootPane

class GlowEditorPreviewTest : BasePlatformTestCase() {

    private lateinit var preview: GlowEditorPreview

    override fun setUp() {
        super.setUp()
        preview = GlowEditorPreview(GlowSettings.State(enabled = false, icons = false, regularText = true, selectionGlow = false))
    }

    override fun tearDown() {
        try {
            Disposer.dispose(preview)
        } finally {
            super.tearDown()
        }
    }

    fun `test actual editor preview paints live halos and respects editor controls`() {
        val plain = paint(preview)
        val glowing = preview.previewState.copy(enabled = true, synthwaveStyle = false)
        preview.update(glowing)
        assertFalse(plain.contentEquals(paint(preview)))
        for (disabled in listOf(glowing.copy(enabled = false), glowing.copy(editorText = false),
            glowing.copy(brightness = 0f), glowing.copy(editorGlowStrength = 0f), glowing.copy(regularText = false))) {
            preview.update(disabled)
            assertTrue("Disabled editor glow must restore plain rendering: $disabled", plain.contentEquals(paint(preview)))
        }
        preview.update(glowing)
        val full = paint(preview)
        for (adjusted in listOf(glowing.copy(brightness = 0.15f), glowing.copy(editorGlowStrength = 0.2f),
            glowing.copy(radiusPx = 12f), glowing.copy(intensity = 0.5f))) {
            preview.update(adjusted)
            assertFalse("The sample must visibly reflect $adjusted", full.contentEquals(paint(preview)))
        }
        preview.update(glowing.copy(uiText = false, uiGlowStrength = 0f, iconGlowStrength = 0f))
        assertTrue("UI and icon strength must not change editor glyphs", full.contentEquals(paint(preview)))
    }

    fun `test styled sample uses scheme colours without changing global scheme`() {
        val global = EditorColorsManager.getInstance().globalScheme
        assertEquals(global.defaultBackground, preview.editor.colorsScheme.defaultBackground)
        assertEquals(global.defaultForeground, preview.editor.colorsScheme.defaultForeground)
        val scheme = global.clone() as EditorColorsScheme
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color.LIGHT_GRAY, Color(0x262335), null, null, 0))
        scheme.setAttributes(DefaultLanguageHighlighterColors.KEYWORD, TextAttributes(Color(0x36f9f6), null, null, null, 0))
        preview.editor.colorsScheme = scheme
        val plain = paint(preview)
        preview.update(GlowSettings.State(icons = false, regularText = false, selectionGlow = false))
        val styled = paint(preview)
        assertFalse("Coloured syntax receives neon styling", plain.contentEquals(styled))
        preview.update(preview.previewState.copy(synthwaveStyle = false))
        assertTrue("No styling and no regular glow restores original colours", plain.contentEquals(paint(preview)))
        assertEquals(Color(0x36f9f6), scheme.getAttributes(DefaultLanguageHighlighterColors.KEYWORD).foregroundColor)
        assertSame(global, EditorColorsManager.getInstance().globalScheme)
    }

    fun `test preview replaces inherited live glow and leaves unrelated graphics alone`() {
        for (previewFirst in listOf(true, false)) {
            val root = JRootPane()
            val live = GlowSettings.State(regularText = true)
            val liveAtlas = GlyphGlowAtlas()
            val glow = UiGlow(liveAtlas, { live }, { false })
            try {
                if (!previewFirst) {
                    Disposer.dispose(preview)
                    preview = GlowEditorPreview(GlowSettings.State(enabled = false, icons = false, regularText = true))
                }
                root.contentPane = preview
                live.enabled = false
                val draftOff = paint(root)
                live.enabled = true
                assertTrue("Enabled live glow cannot override disabled draft", draftOff.contentEquals(paint(root)))
                preview.update(preview.previewState.copy(enabled = true))
                val draftOn = paint(root)
                assertFalse(draftOff.contentEquals(draftOn))
                live.enabled = false
                assertTrue("Disabled live glow cannot override enabled draft", draftOn.contentEquals(paint(root)))
                live.enabled = true
                assertTrue("Inherited graphics must not double the preview halo", draftOn.contentEquals(paint(root)))
            } finally {
                glow.dispose()
            }
        }
    }

    fun `test child repaint hook stays local and is removed on disposal`() {
        val image = BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        val child = preview.editor.contentComponent
        try {
            val local = JBSwingUtilities.runGlobalCGTransform(child, graphics)
            assertTrue(GlowGraphics2D.isWrapped(local))
            assertFalse(GlowGraphics2D.isGlowing(local))
            preview.update(preview.previewState.copy(enabled = true))
            assertTrue(GlowGraphics2D.isGlowing(JBSwingUtilities.runGlobalCGTransform(child, graphics)))
            assertSame("Unrelated components must retain their live graphics", graphics,
                JBSwingUtilities.runGlobalCGTransform(JLabel("Outside the preview"), graphics))
            Disposer.dispose(preview)
            assertSame("The preview hook must be removed on close", graphics,
                JBSwingUtilities.runGlobalCGTransform(child, graphics))
        } finally {
            graphics.dispose()
        }
    }

    fun `test selection preview uses only draft settings regardless of live hook order`() {
        for (previewFirst in listOf(true, false)) {
            val live = GlowSettings.State(selectionGlow = true, editorText = false, uiText = false, icons = false)
            val glow = UiGlow(GlyphGlowAtlas(), { live }, { false })
            try {
                if (!previewFirst) {
                    Disposer.dispose(preview)
                    preview = GlowEditorPreview(GlowSettings.State())
                }
                preview.editor.selectionModel.setSelection(3, 20)
                preview.update(GlowSettings.State(editorText = false, uiText = false, icons = false, selectionGlow = false))
                val plain = paint(preview)
                live.selectionGlow = false
                assertTrue("live selection settings cannot affect the draft", plain.contentEquals(paint(preview)))
                preview.update(preview.previewState.copy(selectionGlow = true))
                val glowing = paint(preview)
                assertFalse("draft selection receives glow", plain.contentEquals(glowing))
                live.selectionGlow = true
                assertTrue("no second live selection halo", glowing.contentEquals(paint(preview)))
            } finally { glow.dispose() }
        }
    }

    fun `test selection glow toggle is visible in the preview without manually selecting text`() {
        val selection = preview.editor.selectionModel
        assertTrue("The preview must start with a native sample selection", selection.hasSelection())
        assertEquals("Hello, neon!", selection.selectedText)
        val selectionStart = selection.selectionStart
        val selectionEnd = selection.selectionEnd
        assertEquals(2, preview.editor.document.getLineNumber(selectionStart))
        preview.update(GlowSettings.State(selectionGlow = false, editorText = false, uiText = false, icons = false))
        val plain = paint(preview)
        for (enabled in listOf(false, true, false)) {
            preview.update(preview.previewState.copy(selectionGlow = enabled))
            assertEquals("Toggling glow must not move the selection start", selectionStart, selection.selectionStart)
            assertEquals("Toggling glow must not move the selection end", selectionEnd, selection.selectionEnd)
            assertEquals("Hello, neon!", selection.selectedText)
            val rendered = paint(preview)
            if (enabled) {
                assertFalse("Text selection must demonstrate its effect immediately", plain.contentEquals(rendered))
            } else {
                assertTrue("Disabling selection glow must restore exact rendering", plain.contentEquals(rendered))
            }
        }
    }

    fun `test draft updates preserve changed or cleared sample selection`() {
        val selection = preview.editor.selectionModel
        selection.setSelection(3, 20)
        for (enabled in listOf(true, false)) {
            preview.update(preview.previewState.copy(selectionGlow = enabled))
            assertEquals(3, selection.selectionStart)
            assertEquals(20, selection.selectionEnd)
        }
        selection.removeSelection()
        for (enabled in listOf(true, false)) {
            preview.update(preview.previewState.copy(selectionGlow = enabled))
            assertFalse("Draft updates must not restore a cleared sample selection", selection.hasSelection())
        }
    }

    fun `test power save restores original rendering and preview snapshots do not alias drafts`() {
        val plain = paint(preview)
        val draft = preview.previewState.copy(enabled = true)
        preview.update(draft)
        draft.enabled = false
        assertTrue(preview.previewState.enabled)
        assertFalse(plain.contentEquals(paint(preview)))
        val powerSave = PowerSaveMode.isEnabled()
        try {
            PowerSaveMode.setEnabled(true)
            assertTrue(plain.contentEquals(paint(preview)))
        } finally {
            PowerSaveMode.setEnabled(powerSave)
        }
    }

    private fun paint(component: JComponent): IntArray {
        component.setSize(700, 240)
        layout(component)
        val image = BufferedImage(700, 240, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try { component.paint(graphics) } finally { graphics.dispose() }
        return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }

    private fun layout(component: Container) {
        if (component is JComponent) component.isDoubleBuffered = false
        component.doLayout()
        for (child in component.components) if (child is Container) layout(child)
    }
}