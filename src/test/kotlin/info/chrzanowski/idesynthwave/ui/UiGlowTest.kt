package info.chrzanowski.idesynthwave.ui

import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.idesynthwave.render.GlyphGlowAtlas
import info.chrzanowski.idesynthwave.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import javax.swing.*

class UiGlowTest {

    @Test
    fun `plain Swing labels menus and lightweight popups glow and original hierarchy is restored`() = onEdt {
        val root = JRootPane()
        val label = JLabel("Tool window text")
        label.foreground = Color.CYAN
        label.font = Font("Dialog", Font.PLAIN, 18)
        val content = JPanel(null)
        content.background = Color.BLACK
        content.add(label)
        label.setBounds(25, 40, 200, 40)
        root.contentPane = content
        val menu = JMenu("Menu text")
        menu.foreground = Color.CYAN
        root.jMenuBar = JMenuBar().also { it.add(menu) }
        val original = root.layeredPane
        val state = GlowSettings.State()
        val atlas = GlyphGlowAtlas()
        val plain = paint(root)
        val glow = UiGlow(atlas, { state }, { false })
        try {
            glow.installRoot(root)
            val installed = root.layeredPane
            glow.installRoot(root)
            assertSame("installation is idempotent", installed, root.layeredPane)
            assertSame(content, root.contentPane)
            assertSame(original, content.parent)
            assertSame(original, root.jMenuBar.parent)
            val glowing = paint(root)
            assertTrue("ordinary Swing descendants receive halos", differingPixels(plain, glowing) > 100)
            assertTrue(atlas.misses > 0)

            val popup = JLabel("Popup text")
            popup.foreground = Color.MAGENTA
            popup.setBounds(25, 100, 180, 30)
            root.layeredPane.add(popup, JLayeredPane.POPUP_LAYER as Any)
            assertEquals(JLayeredPane.POPUP_LAYER.toInt(), root.layeredPane.getLayer(popup))
            atlas.clear()
            paint(root)
            assertTrue("lightweight popup text is intercepted", atlas.size > 0)

            glow.dispose()
            assertSame(original, root.layeredPane)
            assertSame(original, popup.parent)
            assertEquals(JLayeredPane.POPUP_LAYER.toInt(), original.getLayer(popup))
            original.remove(popup)
            assertEquals("disposing restores the original rendering", 0, differingPixels(plain, paint(root)))
        } finally {
            glow.dispose()
        }
    }

    @Test
    fun `unload unwraps a nested layered pane without replacing another plugins pane`() = onEdt {
        val root = JRootPane()
        val original = root.layeredPane
        val content = JPanel()
        root.contentPane = content
        val glow = UiGlow(GlyphGlowAtlas(), { GlowSettings.State() }, { false })
        try {
            glow.installRoot(root)
            val installed = root.layeredPane
            val popup = JLabel("Popup")
            installed.add(popup, JLayeredPane.POPUP_LAYER as Any)
            val outer = JLayeredPane()
            val other = JLabel("Other plugin")
            root.layeredPane = outer
            outer.add(installed, JLayeredPane.DEFAULT_LAYER as Any)
            outer.add(other, JLayeredPane.DRAG_LAYER as Any)
            installed.setBounds(10, 20, 300, 180)
            val bounds = installed.bounds
            val layer = outer.getLayer(installed)
            val position = outer.getPosition(installed)

            glow.dispose()

            assertSame(outer, root.layeredPane)
            assertNull("unload must remove our plugin-owned wrapper", installed.parent)
            assertSame(outer, original.parent)
            assertEquals(bounds, original.bounds)
            assertEquals(layer, outer.getLayer(original))
            assertEquals(position, outer.getPosition(original))
            assertSame(original, content.parent)
            assertSame(original, popup.parent)
            assertEquals(JLayeredPane.POPUP_LAYER.toInt(), original.getLayer(popup))
            assertSame(outer, other.parent)
        } finally {
            glow.dispose()
        }
    }

    @Test
    fun `unload does not overwrite an unrelated replacement root pane`() = onEdt {
        val root = JRootPane()
        val glow = UiGlow(GlyphGlowAtlas(), { GlowSettings.State() }, { false })
        try {
            glow.installRoot(root)
            val replacement = JLayeredPane()
            root.layeredPane = replacement
            glow.dispose()
            assertSame(replacement, root.layeredPane)
            assertEquals(0, replacement.componentCount)
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
    fun `settings and power save apply to an already installed root`() = onEdt {
        val state = GlowSettings.State(enabled = false)
        var powerSave = false
        val root = JRootPane()
        root.contentPane = JLabel("Settings text")
        val glow = UiGlow(GlyphGlowAtlas(), { state }, { powerSave })
        try {
            glow.installRoot(root)
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