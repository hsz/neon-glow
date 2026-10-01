package info.chrzanowski.idesynthwave.render

import kotlin.math.ceil
import kotlin.math.exp

/**
 * Separable Gaussian blur on a single-channel `FloatArray` raster (row-major, `w * h` samples).
 *
 * Samples outside the raster count as zero, so callers must pad their content by [radius] on every side if no
 * energy is to be lost at the borders; [GlowMaskRenderer] does exactly that.
 */
object GaussianBlur {

    /** Kernel half-width in pixels: `ceil(3σ)`, beyond which the Gaussian carries < 0.3 % of its energy. */
    fun radius(sigma: Float): Int = ceil(3.0 * sigma).toInt()

    /** Normalised 1-D Gaussian kernel of odd length `2 * radius(sigma) + 1`. */
    fun kernel(sigma: Float): FloatArray {
        require(sigma > 0f) { "sigma must be positive, got $sigma" }
        val r = radius(sigma)
        val kernel = FloatArray(2 * r + 1)
        val denominator = 2.0 * sigma * sigma
        var sum = 0.0
        for (i in -r..r) {
            val value = exp(-(i * i) / denominator)
            kernel[i + r] = value.toFloat()
            sum += value
        }
        val norm = (1.0 / sum).toFloat()
        for (i in kernel.indices) kernel[i] *= norm
        return kernel
    }

    /**
     * Blurs [src] in place: a horizontal pass into [scratch] followed by a vertical pass back into [src].
     * [scratch] must hold at least `w * h` samples.
     */
    fun separable(src: FloatArray, w: Int, h: Int, sigma: Float, scratch: FloatArray) {
        require(src.size >= w * h) { "src holds ${src.size} samples, need ${w * h}" }
        require(scratch.size >= w * h) { "scratch holds ${scratch.size} samples, need ${w * h}" }
        if (w == 0 || h == 0) return
        val kernel = kernel(sigma)
        val r = kernel.size / 2

        // Horizontal pass: src -> scratch.
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val from = maxOf(0, x - r)
                val to = minOf(w - 1, x + r)
                var acc = 0f
                var k = from - x + r
                for (sx in from..to) {
                    acc += src[row + sx] * kernel[k]
                    k++
                }
                scratch[row + x] = acc
            }
        }

        // Vertical pass: scratch -> src.
        for (x in 0 until w) {
            for (y in 0 until h) {
                val from = maxOf(0, y - r)
                val to = minOf(h - 1, y + r)
                var acc = 0f
                var k = from - y + r
                for (sy in from..to) {
                    acc += scratch[sy * w + x] * kernel[k]
                    k++
                }
                src[y * w + x] = acc
            }
        }
    }
}
