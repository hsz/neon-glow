package info.chrzanowski.neonglow.render

import java.awt.Font
import java.awt.Shape
import java.awt.geom.AffineTransform

/**
 * Everything a glow mask depends on. Two glyphs with equal keys look identical on screen, so they share one mask.
 *
 * @param glyphCode font-specific glyph id (`GlyphVector.getGlyphCode`)
 * @param argb tint colour (alpha byte ignored)
 * @param sysScale device pixels per user-space unit
 * @param radiusPx user-space glow radius
 * @param synthwaveStyle whether to use layered colour and shadow rules
 * @param brightness variable shadow alpha baked into layered masks; same-colour masks use blit opacity instead
 * @param textStyleRule resolved upstream/adaptive rule; different halo palettes must not share masks
 */
data class GlyphKey(
    val glyphCode: Int,
    val fontFamily: String,
    val fontStyle: Int,
    val fontSize2D: Float,
    val argb: Int,
    val sysScale: Float,
    val radiusPx: Float,
    val font: Font? = null,
    val glyphTransform: AffineTransform? = null,
    val synthwaveStyle: Boolean = false,
    val brightness: Float = 1f,
    val textStyleRule: SynthwaveTextStyle.Rule? = null,
)

/**
 * Access-ordered LRU cache of [GlowMask]s keyed by [GlyphKey]. Edits and scrolling never invalidate entries; only
 * [clear] (theme / settings change) or eviction at [capacity] / [maxBytes] drops them. Not thread-safe: paint runs on
 * the EDT. Masks larger than the byte budget are returned without being retained.
 */
class GlyphGlowAtlas(
    val capacity: Int = DEFAULT_CAPACITY,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val renderer: (GlyphKey, Shape, Float) -> GlowMask = { key, outline, intensity ->
        if (key.synthwaveStyle) SynthwaveTextStyle.render(key, outline, intensity)
        else GlowMaskRenderer.render(
            outline, GlowMaskRenderer.sigmaFor(key.radiusPx, key.sysScale), key.argb, key.sysScale, intensity,
        )
    },
) {

    init {
        require(capacity > 0) { "capacity must be positive, got $capacity" }
        require(maxBytes > 0) { "maxBytes must be positive, got $maxBytes" }
    }

    private val entries = object : LinkedHashMap<GlyphKey, GlowMask>(capacity * 4 / 3 + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GlyphKey, GlowMask>): Boolean {
            if (size <= capacity) return false
            bytes -= eldest.value.bytes
            evictions++
            return true
        }
    }

    /** Alpha multiplier baked into every mask; changing it discards all cached masks. */
    var intensity: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                clear()
            }
        }

    var hits: Long = 0
        private set
    var misses: Long = 0
        private set
    var evictions: Long = 0
        private set

    /** Approximate pixel-data footprint of all cached masks. */
    var bytes: Long = 0
        private set

    val size: Int get() = entries.size

    /** The cached mask for [key], rendering it from [outline] on a miss. [outline] is only invoked on a miss. */
    fun get(key: GlyphKey, outline: () -> Shape): GlowMask = find(key) ?: render(key, outline())

    /**
     * The cached mask for [key] or `null` on a miss (counted). Together with [render] this is the allocation-free
     * form of [get] for the paint hot path, where the outline is only computed when actually needed.
     */
    fun find(key: GlyphKey): GlowMask? {
        val mask = entries[key]
        if (mask != null) hits++ else misses++
        return mask
    }

    /** Renders and caches the mask for [key] from [outline], replacing any previous entry. */
    fun render(key: GlyphKey, outline: Shape): GlowMask {
        val mask = renderer(key, outline, intensity)
        entries.put(key, mask)?.let { bytes -= it.bytes }
        bytes += mask.bytes
        val iterator = entries.entries.iterator()
        while (bytes > maxBytes && iterator.hasNext()) {
            bytes -= iterator.next().value.bytes
            iterator.remove()
            evictions++
        }
        return mask
    }

    /** Drops every mask and resets the counters. */
    fun clear() {
        entries.clear()
        bytes = 0
        hits = 0
        misses = 0
        evictions = 0
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 1500
        const val DEFAULT_MAX_BYTES: Long = 32L * 1024 * 1024
    }
}
