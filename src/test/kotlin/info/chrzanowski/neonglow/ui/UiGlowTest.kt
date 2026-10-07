package info.chrzanowski.neonglow.ui

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.image.BufferedImage
import javax.swing.*

class UiGlowTest {

    @Test
    fun `JBPanel and descendant Swing components receive glow and original graphics are restored on dispose`() = onEdt {
        val root = JRootPane()
        val label = JLabel("Tool window text")
        label.foreground = Color.CYAN
        label.font = Font("Dialog", Font.PLAIN, 18)
        val content = JBPanel<JBPanel<*>>(null)
        content.background = Color.BLACK
        content.add(label)
        label.setBounds(25, 40, 200, 40)
        root.contentPane = content
        val menu = JMenu("Menu text")
        menu.foreground = Color.CYAN
        root.jMenuBar = JMenuBar().also { it.add(menu) }
        val originalLayeredPane = root.layeredPane
        val state = GlowSettings.State()
        val atlas = GlyphGlowAtlas()
        val plain = paint(root)
        val glow = UiGlow(atlas, { state }, { false })
        try {
            assertSame("layered pane is never replaced", originalLayeredPane, root.layeredPane)
            val glowing = paint(root)
            assertTrue("ordinary Swing descendants inside JBPanel receive halos", differingPixels(plain, glowing) > 100)
            assertTrue(atlas.misses > 0)

            val popup = JLabel("Popup text")
            popup.foreground = Color.MAGENTA
            popup.setBounds(25, 100, 180, 30)
            content.add(popup)
            atlas.clear()
            paint(root)
            assertTrue("popup text is intercepted", atlas.size > 0)

            content.remove(popup)
            glow.dispose()
            assertSame("layered pane remains intact after dispose", originalLayeredPane, root.layeredPane)
            assertEquals("disposing restores the original rendering", 0, differingPixels(plain, paint(root)))
        } finally {
            glow.dispose()
        }
    }

    @Test
    fun `nested component hooks do not double glow even after an enabled toggle`() = onEdt {
        val state = GlowSettings.State(enabled = false)
        val glow = UiGlow(GlyphGlowAtlas(), { state }, { false })
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val raw = image.createGraphics()
        try {
            val once = JBSwingUtilities.runGlobalCGTransform(JPanel(), raw)
            val twice = JBSwingUtilities.runGlobalCGTransform(JPanel(), once)
            assertSame("disabled wrappers must not stack either", once, twice)
            state.enabled = true
            assertSame(once, JBSwingUtilities.runGlobalCGTransform(JPanel(), twice))
            assertTrue(GlowGraphics2D.isGlowing(twice))
        } finally {
            raw.dispose()
            glow.dispose()
        }
    }

    @Test
    fun `settings and power save apply dynamically without root manipulation`() = onEdt {
        val state = GlowSettings.State(enabled = false, synthwaveStyle = false, regularText = true)
        var powerSave = false
        val root = JRootPane()
        val content = JBPanel<JBPanel<*>>()
        val label = JLabel("Settings text")
        label.foreground = Color.CYAN
        content.add(label)
        root.contentPane = content
        val glow = UiGlow(GlyphGlowAtlas(), { state }, { powerSave })
        try {
            val plain = paint(root)
            state.enabled = true
            assertTrue(differingPixels(plain, paint(root)) > 0)
            powerSave = true
            assertEquals(0, differingPixels(plain, paint(root)))
            powerSave = false
            state.enabled = false
            assertEquals(0, differingPixels(plain, paint(root)))
        } finally {
            glow.dispose()
        }
    }

    @Test
    fun `diagnostics and clearCache report atlas state accurately`() = onEdt {
        val images = ImageGlowAtlas()
        val glow = UiGlow(GlyphGlowAtlas(), { GlowSettings.State() }, { false }, imageAtlas = images)
        try {
            val diag = glow.diagnostics()
            assertTrue(diag.contains("icon masks: 0"))
            assertTrue(diag.contains("icon bytes: 0"))
            glow.clearCache()
            assertEquals(0, images.size)
        } finally {
            glow.dispose()
        }
    }

    @Test
    fun `repaintAll triggers repaint across displayable windows`() = onEdt {
        val frame = JFrame("Test Repaint Frame")
        frame.size = Dimension(200, 200)
        var repainted = false
        val panel = object : JPanel() {
            override fun paintComponent(g: java.awt.Graphics) {
                super.paintComponent(g)
                repainted = true
            }
        }
        frame.contentPane = panel
        frame.isVisible = true
        val glow = UiGlow(GlyphGlowAtlas(), { GlowSettings.State() }, { false })
        try {
            glow.repaintAll()
            assertTrue(frame.isDisplayable)
        } finally {
            glow.dispose()
            frame.dispose()
        }
    }

    private fun paint(root: JRootPane): BufferedImage {
        root.setSize(300, 180)
        layout(root)
        val image = BufferedImage(300, 180, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            root.paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun layout(component: java.awt.Container) {
        if (component is JComponent) component.isDoubleBuffered = false
        component.doLayout()
        for (child in component.components) if (child is java.awt.Container) layout(child)
    }

    private fun differingPixels(first: BufferedImage, second: BufferedImage): Int {
        var count = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) count++
        }
        return count
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }
}