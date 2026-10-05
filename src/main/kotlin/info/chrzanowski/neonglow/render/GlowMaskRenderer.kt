package info.chrzanowski.neonglow.render

import java.awt.Color
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A blurred, tinted copy of one shape in device pixels. The image's top-left pixel sits at ([offsetX], [offsetY])
 * device pixels from the shape's origin (for a glyph: its baseline origin), so the mask is blitted at
 * `(originDeviceX + offsetX, originDeviceY + offsetY)`.
 */
class GlowMask(val image: BufferedImage, val offsetX: Int, val offsetY: Int) {

    val width: Int get() = image.width
    val height: Int get() = image.height

    /** Approximate heap footprint of the pixel data (4 bytes per `TYPE_INT_ARGB_PRE` pixel). */
    val bytes: Long get() = 4L * width * height
}

/**
 * Turns a shape outline into a [GlowMask]: an antialiased alpha raster of the outline scaled by the device scale,
 * blurred with a separable Gaussian and colourised into a premultiplied `TYPE_INT_ARGB_PRE` image.
 *
 * All sizes are device pixels; the raster is padded by `3σ` on every side so the blur never touches the border.
 */
object GlowMaskRenderer {

    /** Device-pixel σ for a user-space glow radius: the visible halo reaches about `2σ`, the raster `3σ`. */
    fun sigmaFor(radiusPx: Float, sysScale: Float): Float = radiusPx * sysScale / 2f

    /**
     * @param outline shape in user-space units relative to its origin
     * @param sigmaDevicePx Gaussian σ in device pixels
     * @param argb tint; the alpha byte is ignored
     * @param sysScale device pixels per user-space unit
     * @param intensity multiplier applied to the blurred alpha before clamping to `1`; values above `1` thicken the
     *        halo of thin strokes, whose blurred coverage alone would barely be visible
     */
    fun render(outline: Shape, sigmaDevicePx: Float, argb: Int, sysScale: Float, intensity: Float): GlowMask {
        require(sysScale > 0f) { "sysScale must be positive, got $sysScale" }
        val pad = GaussianBlur.radius(sigmaDevicePx)
        val bounds = outline.bounds2D
        val minX = floor(bounds.minX * sysScale).toInt()
        val minY = floor(bounds.minY * sysScale).toInt()
        val maxX = ceil(bounds.maxX * sysScale).toInt()
        val maxY = ceil(bounds.maxY * sysScale).toInt()
        val offsetX = minX - pad
        val offsetY = minY - pad
        val w = maxOf(maxX - minX, 0) + 2 * pad
        val h = maxOf(maxY - minY, 0) + 2 * pad

        val alpha = rasterizeAlpha(outline, sysScale, offsetX, offsetY, w, h)
        if (sigmaDevicePx > 0f) GaussianBlur.separable(alpha, w, h, sigmaDevicePx, FloatArray(w * h))

        val rgb = argb and 0xFFFFFF
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val a = (alpha[i] * intensity).coerceIn(0f, 1f)
            val alphaByte = (a * 255f + 0.5f).toInt()
            if (alphaByte > 0) pixels[i] = (alphaByte shl 24) or rgb
        }
        // setRGB premultiplies for TYPE_INT_ARGB_PRE and keeps the image manageable (hardware-cacheable),
        // unlike writing straight into the DataBuffer.
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
        image.setRGB(0, 0, w, h, pixels, 0, w)
        return GlowMask(image, offsetX, offsetY)
    }

    /** Antialiased coverage of [outline] (0..1 per device pixel) in a `w * h` raster whose origin is ([offsetX], [offsetY]). */
    private fun rasterizeAlpha(outline: Shape, sysScale: Float, offsetX: Int, offsetY: Int, w: Int, h: Int): FloatArray {
        val alpha = FloatArray(w * h)
        if (w == 0 || h == 0) return alpha
        val coverage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = coverage.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.translate(-offsetX.toDouble(), -offsetY.toDouble())
            g.scale(sysScale.toDouble(), sysScale.toDouble())
            g.color = Color.WHITE
            g.fill(outline)
        } finally {
            g.dispose()
        }
        val argb = coverage.getRGB(0, 0, w, h, null, 0, w)
        for (i in alpha.indices) alpha[i] = (argb[i] ushr 24) / 255f
        return alpha
    }
}
