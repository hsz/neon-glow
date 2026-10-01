package info.chrzanowski.idesynthwave.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GaussianBlurTest {

    @Test
    fun `kernel is odd, symmetric, peaks in the middle and sums to one`() {
        for (sigma in floatArrayOf(0.5f, 1f, 2.5f, 4f)) {
            val kernel = GaussianBlur.kernel(sigma)
            assertEquals(2 * GaussianBlur.radius(sigma) + 1, kernel.size)
            assertEquals(1, kernel.size % 2)
            val mid = kernel.size / 2
            for (i in 0..mid) {
                assertEquals(kernel[mid - i], kernel[mid + i], 1e-7f)
                assertTrue(kernel[mid + i] <= kernel[mid])
            }
            assertEquals(1f, kernel.sum(), 1e-5f)
        }
    }

    @Test
    fun `radius is three sigma rounded up`() {
        assertEquals(3, GaussianBlur.radius(1f))
        assertEquals(5, GaussianBlur.radius(1.5f))
        assertEquals(6, GaussianBlur.radius(2f))
    }

    @Test
    fun `blurring a single pixel yields a symmetric bell with its energy inside three sigma`() {
        val sigma = 2f
        val r = GaussianBlur.radius(sigma)
        val w = 4 * r + 1
        val h = w
        val cx = w / 2
        val cy = h / 2
        val src = FloatArray(w * h)
        src[cy * w + cx] = 1f

        GaussianBlur.separable(src, w, h, sigma, FloatArray(w * h))

        assertEquals("energy is preserved", 1f, src.sum(), 1e-4f)
        var inside = 0f
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx
            val dy = y - cy
            if (abs(dx) <= r && abs(dy) <= r) inside += src[y * w + x]
            assertEquals("symmetric in x", src[y * w + (cx - dx)], src[y * w + x], 1e-6f)
            assertEquals("symmetric in y", src[(cy - dy) * w + x], src[y * w + x], 1e-6f)
            assertTrue("peak is at the centre", src[y * w + x] <= src[cy * w + cx] + 1e-7f)
        }
        assertTrue("≥ 99 % of the energy inside 3σ, got $inside", inside >= 0.99f)
        assertTrue("the centre spreads out", src[cy * w + cx] < 0.1f)
    }

    @Test
    fun `blur of a padded block preserves energy and spills outside the block`() {
        val sigma = 1.5f
        val pad = GaussianBlur.radius(sigma)
        val block = 4
        val w = block + 2 * pad
        val h = block + 2 * pad
        val src = FloatArray(w * h)
        for (y in pad until pad + block) for (x in pad until pad + block) src[y * w + x] = 1f
        val before = src.sum()

        GaussianBlur.separable(src, w, h, sigma, FloatArray(w * h))

        assertEquals(before, src.sum(), 1e-3f)
        assertTrue("halo just outside the block", src[pad * w + pad - 1] > 0.1f)
        assertTrue("faint at the raster border", src[0] < 1e-3f)
    }

    @Test
    fun `empty raster is a no-op`() {
        GaussianBlur.separable(FloatArray(0), 0, 0, 1f, FloatArray(0))
    }

    @Test
    fun `kernel rejects non-positive or NaN sigma`() {
        for (sigma in floatArrayOf(0f, -1f, Float.NaN)) {
            assertThrows(IllegalArgumentException::class.java) { GaussianBlur.kernel(sigma) }
        }
    }

    @Test
    fun `blur rejects undersized raster buffers`() {
        assertThrows(IllegalArgumentException::class.java) {
            GaussianBlur.separable(FloatArray(1), 2, 2, 1f, FloatArray(4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GaussianBlur.separable(FloatArray(4), 2, 2, 1f, FloatArray(1))
        }
    }

    @Test
    fun `single row and column use zero padding`() {
        val kernel = GaussianBlur.kernel(1f)
        val mid = kernel.size / 2
        val row = FloatArray(kernel.size).also { it[mid] = 1f }
        val column = row.copyOf()

        GaussianBlur.separable(row, row.size, 1, 1f, FloatArray(row.size))
        GaussianBlur.separable(column, 1, column.size, 1f, FloatArray(column.size))

        for (i in kernel.indices) {
            assertEquals(kernel[i] * kernel[mid], row[i], 1e-7f)
            assertEquals(row[i], column[i], 1e-7f)
        }
    }
}
