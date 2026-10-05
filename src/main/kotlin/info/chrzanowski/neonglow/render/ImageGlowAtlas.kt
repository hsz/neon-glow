package info.chrzanowski.neonglow.render

import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/** Bounded cache of coloured image halos. Pixel keys also detect mutable or animated icon images. EDT only. */
class ImageGlowAtlas(
    val capacity: Int = 256,
    val maxBytes: Long = 16L * 1024 * 1024,
) {

    init {
        require(capacity > 0)
        require(maxBytes > 0)
    }

    private class Key(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val transform: AffineTransform,
        val rasterWidth: Int,
        val rasterHeight: Int,
        val sigma: Float,
        val intensity: Float,
    ) {
        private val hash = pixels.contentHashCode()

        override fun hashCode(): Int = 31 * hash + transform.hashCode()

        override fun equals(other: Any?): Boolean = other is Key && hash == other.hash &&
            width == other.width && height == other.height && transform == other.transform &&
            rasterWidth == other.rasterWidth && rasterHeight == other.rasterHeight &&
            sigma == other.sigma && intensity == other.intensity && pixels.contentEquals(other.pixels)
    }

    private val entries = LinkedHashMap<Key, GlowMask>(capacity, 0.75f, true)
    var bytes: Long = 0
        private set
    var hits: Long = 0
        private set
    var misses: Long = 0
        private set
    val size: Int get() = entries.size

    /** [transform] maps the cropped image into a device-space raster with bounds starting at zero. */
    fun get(
        image: BufferedImage,
        source: Rectangle,
        transform: AffineTransform,
        width: Int,
        height: Int,
        radiusPx: Float,
        sysScale: Float,
        intensity: Float,
    ): GlowMask = checkNotNull(getIfAllowed(image, source, transform, width, height, radiusPx, sysScale, intensity) { true })

    fun getIfAllowed(
        image: BufferedImage, source: Rectangle, transform: AffineTransform, width: Int, height: Int,
        radiusPx: Float, sysScale: Float, intensity: Float, allowRender: () -> Boolean,
    ): GlowMask? {
        val key = Key(
            image.getRGB(source.x, source.y, source.width, source.height, null, 0, source.width),
            source.width, source.height, AffineTransform(transform), width, height,
            GlowMaskRenderer.sigmaFor(radiusPx, sysScale), intensity,
        )
        entries[key]?.let { hits++; return it }
        misses++
        if (!allowRender()) return null
        val mask = render(key)
        val cost = mask.bytes + 4L * key.pixels.size
        if (cost <= maxBytes) {
            entries[key] = mask
            bytes += cost
            val iterator = entries.entries.iterator()
            while (entries.size > capacity || bytes > maxBytes) {
                val eldest = iterator.next()
                bytes -= eldest.value.bytes + 4L * eldest.key.pixels.size
                iterator.remove()
            }
        }
        return mask
    }

    fun clear() {
        entries.clear()
        bytes = 0
        hits = 0
        misses = 0
    }

    private fun render(key: Key): GlowMask {
        val pad = GaussianBlur.radius(key.sigma)
        val width = key.rasterWidth + 2 * pad
        val height = key.rasterHeight + 2 * pad
        val source = BufferedImage(key.width, key.height, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, key.width, key.height, key.pixels, 0, key.width)
        val raster = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
        val g = raster.createGraphics()
        try {
            g.translate(pad.toDouble(), pad.toDouble())
            g.transform(key.transform)
            g.drawImage(source, 0, 0, null)
        } finally {
            g.dispose()
        }
        val pixels = raster.getRGB(0, 0, width, height, null, 0, width)
        val alpha = FloatArray(pixels.size) { (pixels[it] ushr 24) / 255f }
        val channels = Array(3) { channel ->
            val shift = 16 - channel * 8
            FloatArray(pixels.size) { ((pixels[it] ushr shift) and 255) / 255f * alpha[it] }
        }
        val scratch = FloatArray(pixels.size)
        if (key.sigma > 0f) {
            GaussianBlur.separable(alpha, width, height, key.sigma, scratch)
            for (channel in channels) GaussianBlur.separable(channel, width, height, key.sigma, scratch)
        }
        // Blur premultiplied colour, not transparent RGB, so multicolour icons retain their own hues.
        for (i in pixels.indices) {
            val a = (alpha[i] * key.intensity).coerceIn(0f, 1f)
            val alphaByte = (a * 255f + 0.5f).toInt()
            var pixel = alphaByte shl 24
            if (alphaByte > 0) {
                for (channel in channels.indices) {
                    val colour = (channels[channel][i] / alpha[i] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    pixel = pixel or (colour shl (16 - channel * 8))
                }
            }
            pixels[i] = pixel
        }
        raster.setRGB(0, 0, width, height, pixels, 0, width)
        return GlowMask(raster, -pad, -pad)
    }
}
