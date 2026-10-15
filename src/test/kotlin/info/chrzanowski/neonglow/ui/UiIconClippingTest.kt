package info.chrzanowski.neonglow.ui

import com.intellij.ui.components.JBPanel
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.SwingUtilities

class UiIconClippingTest {

    @Test
    fun `tightly fitted labels with icons have the same smooth halo as padded labels at both scales`() = onEdt {
        for (scale in listOf(1.0, 2.0)) {
            val state = GlowSettings.State(icons = true, iconGlowStrength = 1f)
            val glow = UiGlow(GlyphGlowAtlas(), { state }, { false })
            try {
                val tight = paintIconLabel(scale)
                val padded = paintIconLabel(scale, padded = true)
                assertTrue("the halo extends above the icon", (60 * scale).toInt().until((70 * scale).toInt()).any { y ->
                    (80 * scale).toInt().until((110 * scale).toInt()).any { x -> padded.getRGB(x, y) != Color(0x252525).rgb }
                })
                assertPixelsEqual(padded, tight)
            } finally { glow.dispose() }
        }
    }

    @Test
    fun `icon halos respect repaint and enclosing panel clips at both scales`() = onEdt {
        for (scale in listOf(1.0, 2.0)) {
            val state = GlowSettings.State(icons = true, iconGlowStrength = 1f)
            val glow = UiGlow(GlyphGlowAtlas(), { state }, { false })
            try {
                val clip = Rectangle(85, 72, 40, 30)
                val full = paintIconLabel(scale)
                val clipped = paintIconLabel(scale, clip = clip)
                for (y in 0 until clipped.height) for (x in 0 until clipped.width) {
                    val expected = if (clip.contains(x / scale, y / scale)) full.getRGB(x, y) else 0
                    assertEquals("icon repaint clip at ($x, $y), scale $scale", expected, clipped.getRGB(x, y))
                }
                state.enabled = false
                val plain = paintIconLabel(scale, nested = true)
                state.enabled = true
                val nested = paintIconLabel(scale, nested = true)
                assertTrue("the icon still glows inside its parent", (70 * scale).toInt().until((86 * scale).toInt()).any { y ->
                    (80 * scale).toInt().until((90 * scale).toInt()).any { x -> nested.getRGB(x, y) != plain.getRGB(x, y) }
                })
                for (y in 0 until nested.height) for (x in 0 until nested.width) {
                    if (Rectangle(80, 70, 350, 90).contains(x / scale, y / scale)) continue
                    assertEquals("icon parent clip at ($x, $y), scale $scale", plain.getRGB(x, y), nested.getRGB(x, y))
                }
            } finally { glow.dispose() }
        }
    }

    private fun paintIconLabel(scale: Double, padded: Boolean = false, clip: Rectangle? = null, nested: Boolean = false): BufferedImage {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        img.createGraphics().also {
            it.color = Color.ORANGE
            it.fillRect(0, 0, 16, 16)
            it.dispose()
        }
        val icon = javax.swing.ImageIcon(img)
        val label = JLabel(icon).also {
            it.preferredSize = Dimension(16, 16)
        }
        val padding = if (padded) 60 else 0
        label.setBounds(90 - padding, 70 - padding, 16 + padding * 2, 16 + padding * 2)
        val panel = JBPanel<JBPanel<*>>(null).also {
            it.background = Color(0x252525)
            it.isDoubleBuffered = false
            it.setSize(500, 180)
            if (nested) {
                it.add(JBPanel<JBPanel<*>>(null).also { parent ->
                    parent.background = it.background
                    parent.isDoubleBuffered = false
                    parent.setBounds(80, 70, 350, 90)
                    label.setLocation(label.x - parent.x, label.y - parent.y)
                    parent.add(label)
                })
            } else it.add(label)
        }
        val image = BufferedImage((500 * scale).toInt(), (180 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(scale, scale)
            g.clip = clip ?: Rectangle(0, 0, 500, 180)
            panel.paint(g)
        } finally { g.dispose() }
        return image
    }

    private fun assertPixelsEqual(expected: BufferedImage, actual: BufferedImage) {
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals("Pixel mismatch at ($x, $y)", expected.getRGB(x, y), actual.getRGB(x, y))
        }
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }
}
