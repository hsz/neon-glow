package info.chrzanowski.neonglow.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRootPane

class UiIconGlowTest : BasePlatformTestCase() {

    fun `test actual IntelliJ SVG icons receive glow at both display scales`() {
        val state = GlowSettings.State(enabled = false)
        val images = ImageGlowAtlas()
        val root = JRootPane()
        val panel = JPanel(null)
        panel.background = Color.BLACK
        IconLoader.activate()
        val icon = IconLoader.getIcon("/nodes/folder.svg", AllIcons::class.java)
        assertTrue("load the real SVG, not the headless dummy icon", icon.iconWidth > 1)
        panel.add(JLabel(icon).also { it.setBounds(25, 25, 50, 50) })
        root.contentPane = panel
        root.setSize(100, 100)
        val glow = UiGlow(GlyphGlowAtlas(), { state }, { false }, imageAtlas = images)
        try {
            glow.installRoot(root)
            for (scale in listOf(1.0, 2.0)) {
                fun paintRoot(): BufferedImage {
                    fun layout(component: java.awt.Container) {
                        if (component is javax.swing.JComponent) component.isDoubleBuffered = false
                        component.doLayout()
                        for (child in component.components) if (child is java.awt.Container) layout(child)
                    }
                    layout(root)
                    val image = BufferedImage((100 * scale).toInt(), (100 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
                    val g = image.createGraphics()
                    try { g.scale(scale, scale); root.paint(g) } finally { g.dispose() }
                    return image
                }
                state.enabled = false
                val plainRoot = paintRoot()
                state.enabled = true
                images.clear()
                val glowing = paintRoot()
                assertTrue("a rasterised IntelliJ SVG populates the image halo cache", images.size > 0)
                assertFalse(pixels(plainRoot).contentEquals(pixels(glowing)))
                val misses = images.misses
                paintRoot()
                assertEquals("subsequent paints reuse icon halos", misses, images.misses)
                assertTrue(images.hits > 0)
            }
        } finally {
            glow.dispose()
            IconLoader.deactivate()
        }
    }

    private fun pixels(image: BufferedImage): IntArray =
        image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}