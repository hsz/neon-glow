package info.chrzanowski.idesynthwave.render

import java.awt.geom.Rectangle2D
import kotlin.math.ceil

/** Shared by graphics copies during one paint. Cache hits never consume the cold-render budget. */
class GlowWorkBudget(
    private val clock: () -> Long = System::nanoTime,
) {
    private var started: Long? = null
    private var glyphs = 0
    private var images = 0

    fun allowGlyph(limited: Boolean): Boolean = allow(limited, glyphs < MAX_GLYPHS).also { if (it && limited) glyphs++ }
    fun allowImage(limited: Boolean): Boolean = allow(limited, images < MAX_IMAGES).also { if (it && limited) images++ }

    private fun allow(limited: Boolean, available: Boolean): Boolean {
        if (!limited) return true
        val now = clock()
        val start = started ?: now.also { started = it }
        return available && now - start < MAX_NANOS
    }

    companion object {
        const val MAX_GLYPHS = 24
        const val MAX_IMAGES = 4
        const val MAX_NANOS = 2_000_000L

        /** Reject unreasonable zoom/transforms before allocating blur rasters, regardless of performance mode. */
        fun safeRaster(bounds: Rectangle2D, scale: Float, radiusPx: Float): Boolean {
            if (!scale.isFinite() || scale <= 0f || !radiusPx.isFinite() || radiusPx < 0f) return false
            if (!bounds.x.isFinite() || !bounds.y.isFinite() || !bounds.width.isFinite() || !bounds.height.isFinite()) return false
            val pad = ceil(radiusPx * scale * 1.5).toLong()
            val width = ceil(bounds.width * scale).toLong() + 2 * pad + 2
            val height = ceil(bounds.height * scale).toLong() + 2 * pad + 2
            return width in 1..2048 && height in 1..2048 && width * height <= 1_048_576
        }
    }
}