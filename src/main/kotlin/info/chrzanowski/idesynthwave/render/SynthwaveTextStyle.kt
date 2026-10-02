package info.chrzanowski.idesynthwave.render

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Shape
import java.awt.image.BufferedImage
import kotlin.math.pow

/** Upstream RGB rules plus background-aware adaptations; CSS lists the topmost shadow first. */
object SynthwaveTextStyle {

    data class Shadow(val rgb: Int, val blurPx: Float, val opacity: Float? = null)
    data class Rule(val foregroundRgb: Int, val shadows: List<Shadow>)

    private val rules = mapOf(
        0xfe4450 to Rule(0xfff5f6, listOf(
            Shadow(0x000000, 2f, 1f), Shadow(0xfc1f2c, 10f), Shadow(0xfc1f2c, 5f), Shadow(0xfc1f2c, 25f),
        )),
        0xff7edb to Rule(0xf92aad, listOf(
            Shadow(0x100c0f, 2f, 1f), Shadow(0xdc078e, 5f, 0.2f), Shadow(0xffffff, 10f, 0.2f),
        )),
        0xfede5d to Rule(0xf4eee4, listOf(
            Shadow(0x393a33, 2f, 1f), Shadow(0xf39f05, 8f), Shadow(0xf39f05, 2f),
        )),
        0x72f1b8 to Rule(0x72f1b8, listOf(
            Shadow(0x100c0f, 2f, 1f), Shadow(0x257c55, 10f), Shadow(0x212724, 35f),
        )),
        0x36f9f6 to Rule(0xfdfdfd, listOf(
            Shadow(0x001716, 2f, 1f), Shadow(0x03edf9, 3f), Shadow(0x03edf9, 5f), Shadow(0x03edf9, 8f),
        )),
    )

    fun rule(argb: Int): Rule? = rules[argb and 0xffffff]

    /** Light surfaces retain original cores; unknown surfaces use regular glow. Eligibility is colour-based. */
    fun rule(argb: Int, backgroundRgb: Int?): Rule? {
        if (backgroundRgb == null) return null
        val rgb = argb and 0xffffff
        val hsb = Color.RGBtoHSB(rgb shr 16, (rgb shr 8) and 255, rgb and 255, null)
        if (luminance(backgroundRgb) > 0.12) {
            if (hsb[1] < 0.35f || contrast(rgb, backgroundRgb) < 3.0) return null
            // Light themes often use dark saturated syntax. Keep it intact and avoid a white core/dark outline.
            var value = minOf(hsb[2], 0.6f)
            var halo = Color.HSBtoRGB(hsb[0], maxOf(hsb[1], 0.85f), value) and 0xffffff
            while (contrast(halo, backgroundRgb) < 3.0 && value > 0.08f) {
                value *= 0.8f
                halo = Color.HSBtoRGB(hsb[0], maxOf(hsb[1], 0.85f), value) and 0xffffff
            }
            return Rule(rgb, listOf(Shadow(halo, 3f), Shadow(halo, 7f), Shadow(halo, 12f)))
        }
        rule(rgb)?.let { exact ->
            // Retain upstream's hot-pink core on its original dark palette; it is not an accessibility preset.
            return if (contrast(exact.foregroundRgb, backgroundRgb) >= 3.0) exact
            else exact.copy(foregroundRgb = rgb)
        }
        if (hsb[1] < 0.35f || hsb[2] < 0.5f || contrast(rgb, backgroundRgb) < 3.0) return null
        val core = Color.HSBtoRGB(hsb[0], hsb[1] * 0.12f, 1f) and 0xffffff
        val neon = Color.HSBtoRGB(hsb[0], maxOf(hsb[1], 0.85f), 1f) and 0xffffff
        val base = Color.HSBtoRGB(hsb[0], 0.6f, 0.08f) and 0xffffff
        return Rule(core, listOf(Shadow(base, 2f, 1f), Shadow(neon, 3f), Shadow(neon, 7f), Shadow(neon, 12f)))
    }

    private fun contrast(foreground: Int, background: Int): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((rgb shr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun maxRadius(radiusPx: Float): Float = 35f * radiusPx / 6f

    fun render(key: GlyphKey, outline: Shape, intensity: Float): GlowMask {
        val rule = checkNotNull(key.textStyleRule ?: rule(key.argb))
        val masks = rule.shadows.map { shadow ->
            GlowMaskRenderer.render(outline,
                GlowMaskRenderer.sigmaFor(shadow.blurPx * key.radiusPx / 6f, key.sysScale),
                shadow.rgb, key.sysScale, intensity)
        }
        val left = masks.minOf { it.offsetX }
        val top = masks.minOf { it.offsetY }
        val right = masks.maxOf { it.offsetX + it.width }
        val bottom = masks.maxOf { it.offsetY + it.height }
        val image = BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_ARGB_PRE)
        val g = image.createGraphics()
        try {
            for (i in masks.indices.reversed()) {
                g.composite = AlphaComposite.SrcOver.derive(rule.shadows[i].opacity ?: key.brightness)
                val mask = masks[i]
                g.drawImage(mask.image, mask.offsetX - left, mask.offsetY - top, null)
            }
        } finally { g.dispose() }
        return GlowMask(image, left, top)
    }
}