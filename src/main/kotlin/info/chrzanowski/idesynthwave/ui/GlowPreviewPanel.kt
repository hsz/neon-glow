package info.chrzanowski.idesynthwave.ui

import com.intellij.ui.scale.JBUIScale
import info.chrzanowski.idesynthwave.render.GlyphGlowAtlas
import info.chrzanowski.idesynthwave.render.ImageGlowAtlas
import info.chrzanowski.idesynthwave.settings.GlowSettings
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel

/** An isolated, static sample. Draft settings and masks never touch the application services or IDE scheme. */
class GlowPreviewPanel(private val settings: () -> GlowSettings.State) : JPanel() {
    private val glyphs = GlyphGlowAtlas(capacity = 256, maxBytes = 4L * 1024 * 1024)
    private val icons = ImageGlowAtlas(capacity = 8, maxBytes = 1024 * 1024)
    private var previous: GlowSettings.State? = null
    private var raster: BufferedImage? = null
    private val sampleIcon = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        try {
            g.color = Color(0x03edf9)
            g.fillRoundRect(2, 5, 16, 12, 3, 3)
            g.fillRect(3, 3, 7, 5)
        } finally { g.dispose() }
    }

    init {
        preferredSize = Dimension(540, 160)
        minimumSize = Dimension(320, 160)
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val raw = graphics as? Graphics2D ?: return
        if (width <= 0 || height <= 0) return
        val state = settings().normalized()
        val scale = JBUIScale.sysScale(raw).coerceIn(1f, 4f)
        val imageWidth = (width.coerceAtMost(1000) * scale).toInt()
        val imageHeight = (height.coerceAtMost(200) * scale).toInt()
        raster?.takeIf { previous == state && it.width == imageWidth && it.height == imageHeight }?.let {
            raw.drawImage(it, 0, 0, width.coerceAtMost(1000), height.coerceAtMost(200), null)
            return
        }
        if (previous != state) {
            glyphs.clear()
            icons.clear()
            previous = state.copy()
        }
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        val base = image.createGraphics()
        base.scale(scale.toDouble(), scale.toDouble())
        base.setClip(0, 0, width, height)
        base.color = Color(0x262335)
        base.fillRect(0, 0, width, height)
        base.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val appearance = state.copy(performanceMode = false)
        val g = GlowGraphics2D(base, glyphs, { appearance }, { false }, imageAtlas = icons, textBackground = Color(0x262335))
        try {
            g.font = Font(Font.DIALOG, Font.PLAIN, 14)
            g.color = Color(0xf0eaf7)
            g.drawString("Project   Search   Terminal", 24, 35)
            g.drawImage(sampleIcon, 24, 115, null)
            g.color = Color(0xf0eaf7)
            g.drawString("SynthWave", 56, 131)
            val editor = GlowGraphics2D.withTextTarget(g, true).create() as Graphics2D
            try {
                editor.font = Font(Font.MONOSPACED, Font.PLAIN, 18)
                editor.color = Color(0xfede5d)
                editor.drawString("fun", 24, 85)
                editor.color = Color(0x36f9f6)
                editor.drawString(" neon", 58, 85)
                editor.color = Color(0xff7edb)
                editor.drawString("(night)", 113, 85)
                editor.color = Color(0x72f1b8)
                editor.drawString(" = dreams", 190, 85)
            } finally { editor.dispose() }
        } finally { g.dispose() }
        raster = image
        raw.drawImage(image, 0, 0, width.coerceAtMost(1000), height.coerceAtMost(200), null)
    }

    override fun removeNotify() {
        glyphs.clear()
        icons.clear()
        previous = null
        raster = null
        super.removeNotify()
    }
}