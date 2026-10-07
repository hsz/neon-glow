package info.chrzanowski.neonglow.ui

import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ui.UIUtil
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import org.junit.Assert.*
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.RescaleOp
import javax.swing.*

class IconGlowGraphics2DTest {

    private val state = GlowSettings.State()
    private val images = ImageGlowAtlas()
    private var powerSave = false

    @Test
    fun `platform HiDPI image painting and unscaled child graphics retain display sized halos`() {
        val low = icon()
        val high = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = high.createGraphics()
        try { g.drawImage(low, 0, 0, 32, 32, null) } finally { g.dispose() }
        val retina = JBHiDPIScaledImage(high, 2.0)
        val regular: (Graphics2D) -> Unit = { it.drawImage(low, 30, 30, null) }
        val platform: (Graphics2D) -> Unit = { UIUtil.drawImage(it, retina, 30, 30, null) }
        assertPixelsEqual(paint(2.0, false, regular), paint(2.0, false, platform))
        assertPixelsEqual(paint(2.0, true, regular), paint(2.0, true, platform))
        val child: (Graphics2D) -> Unit = {
            val unscaled = it.create() as Graphics2D
            try {
                unscaled.scale(0.5, 0.5)
                unscaled.drawImage(high, 60, 60, null)
            } finally { unscaled.dispose() }
        }
        assertPixelsEqual(paint(2.0, true, regular), paint(2.0, true, child))
    }

    @Test
    fun `image icons gain coloured halos at both display scales without changing their pixels`() {
        val icon = icon()
        for (scale in listOf(1.0, 2.0)) {
            val draw: (Graphics2D) -> Unit = { it.drawImage(icon, 30, 30, null) }
            val plain = paint(scale, false, draw)
            val glowing = paint(scale, true, draw)
            assertHalo(plain, glowing)
            assertEquals(plain.getRGB((35 * scale).toInt(), (37 * scale).toInt()),
                glowing.getRGB((35 * scale).toInt(), (37 * scale).toInt()))
            assertEquals("far from the icon stays transparent", 0, glowing.getRGB(5, 5))
        }
    }

    @Test
    fun `scaled transformed cropped and background image overloads glow`() {
        val icon = icon()
        val toolkitIcon = ImageIcon(icon.getScaledInstance(16, 16, Image.SCALE_SMOOTH))
        val operations: List<(Graphics2D) -> Unit> = listOf(
            { it.drawImage(icon, 30, 30, 32, 32, null) },
            { it.drawImage(icon, 30, 30, null as Color?, null) },
            { it.drawImage(icon, 30, 30, 32, 32, null as Color?, null) },
            { it.drawImage(icon, 30, 30, 62, 62, 0, 0, 16, 16, null) },
            { it.drawImage(icon, 62, 30, 30, 62, 0, 0, 16, 16, null as Color?, null) },
            { it.drawImage(icon, AffineTransform(1.0, 0.2, -0.2, 1.0, 35.0, 30.0), null) },
            { it.drawImage(icon, null, 30, 30) },
            { it.drawImage(icon, RescaleOp(floatArrayOf(0.5f, 1f, 1f, 1f), FloatArray(4), null), 30, 30) },
            { it.drawRenderedImage(icon, AffineTransform.getTranslateInstance(30.0, 30.0)) },
            { toolkitIcon.paintIcon(null, it, 30, 30) },
        )
        for (draw in operations) {
            val plain = paint(glow = false, draw = draw)
            assertHalo(plain, paint(draw = draw))
            state.icons = false
            images.clear()
            assertPixelsEqual(plain, paint(draw = draw))
            assertEquals(0, images.size)
            state.icons = true
        }
    }

    @Test
    fun `disabled power save clipping and non-source-over preserve ordinary image painting`() {
        val icon = icon()
        val draw: (Graphics2D) -> Unit = { it.drawImage(icon, 30, 30, null) }
        val plain = paint(glow = false, draw = draw)
        state.enabled = false
        assertPixelsEqual(plain, paint(draw = draw))
        state.enabled = true
        powerSave = true
        assertPixelsEqual(plain, paint(draw = draw))
        powerSave = false
        val src: (Graphics2D) -> Unit = {
            it.composite = AlphaComposite.Src
            draw(it)
        }
        assertPixelsEqual(paint(glow = false, draw = src), paint(draw = src))
        val clipped = paint {
            it.clipRect(30, 30, 16, 16)
            draw(it)
        }
        assertEquals(0, clipped.getRGB(29, 37))
    }

    @Test
    fun `cropping does not leak excluded icon colours and large UI buffers are not treated as icons`() {
        val cropped = paint { it.drawImage(icon(), 30, 30, 38, 46, 0, 0, 8, 16, null) }
        var halo = 0
        for (y in 0 until cropped.height) for (x in 0 until cropped.width) {
            val pixel = cropped.getRGB(x, y)
            if (pixel ushr 24 == 0) continue
            halo++
            assertEquals("the cropped-out magenta half must not glow", 0, (pixel ushr 16) and 255)
        }
        assertTrue(halo > 80)
        val large = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
        val g = large.createGraphics()
        try { g.color = Color.CYAN; g.fillRect(0, 0, 200, 200) } finally { g.dispose() }
        images.clear()
        val draw: (Graphics2D) -> Unit = { it.drawImage(large, 10, 10, null) }
        assertPixelsEqual(paint(glow = false, draw = draw), paint(draw = draw))
        assertEquals("large buffers must not enter the cache", 0, images.size)
        val unknown: (Graphics2D) -> Unit = { assertTrue(it.drawImage(null, 10, 10, null)) }
        assertPixelsEqual(paint(glow = false, draw = unknown), paint(draw = unknown))
    }

    @Test
    fun `label and menu icons receive glow through the installed UI hook`() = onEdt {
        val root = JRootPane()
        val panel = com.intellij.ui.components.JBPanel<com.intellij.ui.components.JBPanel<*>>(null)
        panel.background = Color.BLACK
        panel.add(JLabel(ImageIcon(icon())).also { it.setBounds(30, 30, 60, 40) })
        panel.add(JMenuItem(ImageIcon(icon())).also { it.setBounds(30, 90, 60, 40) })
        root.contentPane = panel
        root.setSize(150, 150)
        val images = ImageGlowAtlas()
        val glow = UiGlow(GlyphGlowAtlas(), { state }, { false }, imageAtlas = images)
        try {
            fun render(): BufferedImage {
                fun layout(component: java.awt.Container) {
                    if (component is javax.swing.JComponent) component.isDoubleBuffered = false
                    component.doLayout()
                    for (child in component.components) if (child is java.awt.Container) layout(child)
                }
                layout(root)
                val image = BufferedImage(150, 150, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try { root.paint(g) } finally { g.dispose() }
                return image
            }
            state.enabled = false
            val plain = render()
            state.enabled = true
            val glowing = render()
            assertTrue(images.size > 0)
            glow.clearCache()
            assertEquals(0, images.size)
            render()
            assertTrue(images.size > 0)
            for (range in listOf(30 until 70, 90 until 130)) {
                assertTrue("icons in both labels and menus glow", range.any { y ->
                    (30 until 90).any { x -> plain.getRGB(x, y) != glowing.getRGB(x, y) }
                })
            }
        } finally {
            glow.dispose()
            assertEquals(0, images.size)
        }
    }

    @Test
    fun `brightness fades icon halos while preserving original pixels and cache entries`() {
        val draw: (Graphics2D) -> Unit = { g ->
            g.composite = AlphaComposite.SrcOver.derive(0.75f)
            g.drawImage(icon(), 30, 30, null)
            assertEquals(AlphaComposite.SrcOver.derive(0.75f), g.composite)
        }
        for (scale in listOf(1.0, 2.0)) {
            state.brightness = 1f
            val plain = paint(scale, false, draw)
            val full = paint(scale, true, draw)
            val misses = images.misses
            state.brightness = 0.45f
            val dim = paint(scale, true, draw)
            assertEquals(misses, images.misses)
            var fullAlpha = 0L
            var dimAlpha = 0L
            for (y in 0 until plain.height) for (x in 0 until plain.width) {
                if (plain.getRGB(x, y) ushr 24 != 0) continue
                fullAlpha += full.getRGB(x, y) ushr 24
                dimAlpha += dim.getRGB(x, y) ushr 24
            }
            assertTrue(fullAlpha > 0)
            assertEquals(0.45, dimAlpha.toDouble() / fullAlpha, 0.03)
            state.brightness = 0f
            images.clear()
            assertPixelsEqual(plain, paint(scale, true, draw))
            assertEquals(0, images.size)
        }
        state.brightness = 0.45f
        val opaque: (Graphics2D) -> Unit = { it.drawImage(icon(), 30, 30, null) }
        assertEquals(paint(glow = false, draw = opaque).getRGB(35, 37), paint(draw = opaque).getRGB(35, 37))
        state.icons = false
        images.clear()
        assertPixelsEqual(paint(glow = false, draw = opaque), paint(draw = opaque))
        assertEquals(0, images.size)
    }

    private fun icon(): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.color = Color.CYAN
            g.fillRect(2, 3, 5, 10)
            g.color = Color.MAGENTA
            g.fillRect(9, 3, 5, 10)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun paint(scale: Double = 1.0, glow: Boolean = true, draw: (Graphics2D) -> Unit): BufferedImage {
        val image = BufferedImage((120 * scale).toInt(), (100 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val raw = image.createGraphics()
        raw.scale(scale, scale)
        raw.setClip(0, 0, 120, 100)
        val g = if (glow) GlowGraphics2D(raw, GlyphGlowAtlas(), { state }, { powerSave }, imageAtlas = images) else raw
        try { draw(g) } finally { g.dispose() }
        return image
    }

    private fun assertHalo(plain: BufferedImage, glowing: BufferedImage) {
        var halo = 0
        var cyan = false
        var magenta = false
        for (y in 0 until plain.height) for (x in 0 until plain.width) {
            if (plain.getRGB(x, y) ushr 24 != 0) continue
            val pixel = glowing.getRGB(x, y)
            if (pixel ushr 24 == 0) continue
            halo++
            cyan = cyan || ((pixel ushr 8) and 0xFF) > ((pixel ushr 16) and 0xFF)
            magenta = magenta || ((pixel ushr 16) and 0xFF) > ((pixel ushr 8) and 0xFF)
        }
        assertTrue("blurred pixels extend outside the icon", halo > 30)
        assertTrue("cyan is preserved", cyan)
        assertTrue("magenta is preserved", magenta)
    }

    private fun assertPixelsEqual(expected: BufferedImage, actual: BufferedImage) = assertArrayEquals(
        expected.getRGB(0, 0, expected.width, expected.height, null, 0, expected.width),
        actual.getRGB(0, 0, actual.width, actual.height, null, 0, actual.width),
    )

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }
}
