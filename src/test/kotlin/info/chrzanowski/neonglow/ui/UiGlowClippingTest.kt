package info.chrzanowski.neonglow.ui

import com.intellij.ui.components.JBPanel
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Area
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.SwingUtilities

class UiGlowClippingTest {

    @Test
    fun `tightly fitted labels have the same smooth halo as padded labels at both scales`() = onEdt {
        for (scale in listOf(1.0, 2.0)) for (styled in listOf(false, true)) {
            val state = GlowSettings.State(synthwaveStyle = styled, regularText = !styled, icons = false)
            val glow = UiGlow(GlyphGlowAtlas(), { state }, { false })
            try {
                state.enabled = false
                val plain = paintLabel(scale)
                assertPixelsEqual(plain, paintLabel(scale, padded = true))
                state.enabled = true
                val tight = paintLabel(scale)
                val padded = paintLabel(scale, padded = true)
                assertTrue("the reference halo extends above the label", (60 * scale).toInt().until((70 * scale).toInt()).any { y ->
                    (80 * scale).toInt().until((350 * scale).toInt()).any { x -> padded.getRGB(x, y) != plain.getRGB(x, y) }
                })
                assertPixelsEqual(padded, tight)
            } finally { glow.dispose() }
        }
    }

    @Test
    fun `label halos respect rectangular and shaped repaint clips at both scales`() = onEdt {
        for (scale in listOf(1.0, 2.0)) for (styled in listOf(false, true)) {
            val state = GlowSettings.State(synthwaveStyle = styled, regularText = !styled, icons = false)
            val glow = UiGlow(GlyphGlowAtlas(), { state }, { false })
            try {
                val full = paintLabel(scale)
                val clips = listOf(Rectangle(80, 72, 300, 45), Area(Rectangle(0, 0, 500, 180)).also {
                    it.subtract(Area(Rectangle(90, 66, 250, 4)))
                })
                for (clip in clips) {
                    val clipped = paintLabel(scale, clip = clip)
                    for (y in 0 until clipped.height) for (x in 0 until clipped.width) {
                        val expected = if (clip.contains((x + 0.5) / scale, (y + 0.5) / scale)) full.getRGB(x, y) else 0
                        assertEquals("repaint clip at ($x, $y), scale $scale, styled $styled", expected, clipped.getRGB(x, y))
                    }
                }
            } finally { glow.dispose() }
        }
    }

    @Test
    fun `label halos stop at the enclosing panel rather than leaking into its siblings`() = onEdt {
        for (scale in listOf(1.0, 2.0)) for (styled in listOf(false, true)) {
            val state = GlowSettings.State(synthwaveStyle = styled, regularText = !styled, icons = false)
            val glow = UiGlow(GlyphGlowAtlas(), { state }, { false })
            try {
                state.enabled = false
                val plain = paintLabel(scale, nested = true)
                state.enabled = true
                val glowing = paintLabel(scale, nested = true)
                assertFalse("the label still glows inside its parent", plain.getRGB(0, 0, plain.width, plain.height, null, 0, plain.width)
                    .contentEquals(glowing.getRGB(0, 0, glowing.width, glowing.height, null, 0, glowing.width)))
                for (y in 0 until glowing.height) for (x in 0 until glowing.width) {
                    if (Rectangle(80, 70, 350, 90).contains(x / scale, y / scale)) continue
                    assertEquals("parent clip at ($x, $y), scale $scale, styled $styled", plain.getRGB(x, y), glowing.getRGB(x, y))
                }
            } finally { glow.dispose() }
        }
    }

    private fun paintLabel(scale: Double, padded: Boolean = false, clip: Shape? = null, nested: Boolean = false): BufferedImage {
        val label = JLabel("Couldn't load session settings").also {
            it.foreground = Color(0xcc7832)
            it.font = Font("Dialog", Font.PLAIN, 14)
        }
        val size = label.preferredSize
        val padding = if (padded) 60 else 0
        label.border = BorderFactory.createEmptyBorder(padding, padding, padding, padding)
        label.setBounds(90 - padding, 70 - padding, size.width + padding * 2, size.height + padding * 2)
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
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            panel.paint(g)
        } finally { g.dispose() }
        return image
    }

    private fun assertPixelsEqual(expected: BufferedImage, actual: BufferedImage) {
        assertArrayEquals(expected.getRGB(0, 0, expected.width, expected.height, null, 0, expected.width),
            actual.getRGB(0, 0, actual.width, actual.height, null, 0, actual.width))
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }
}