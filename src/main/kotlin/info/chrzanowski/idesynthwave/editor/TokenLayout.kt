package info.chrzanowski.idesynthwave.editor

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import kotlin.math.abs

/**
 * Lays out the glyphs of one single-line, tab-free text segment in editor (user-space) coordinates.
 *
 * Fast path: anchor the segment at `editor.offsetToPoint2D(start)`, let a single [GlyphVector] supply the glyph
 * advances and verify the total against `offsetToPoint2D(end)`. Whenever the editor disagrees (soft wrap inside the
 * segment, inline inlays, characters the font cannot display, ligatures) the slow path positions every code point
 * with its own `offsetToPoint2D` call and the font the editor itself would pick for it.
 *
 * The instance is reused across calls; results are valid until the next [layout].
 */
class TokenLayout {

    /** Number of laid-out glyphs. */
    var count: Int = 0
        private set

    /** Whether the last [layout] took the per-character slow path. */
    var usedFallback: Boolean = false
        private set

    /** User-space x of glyph `i`'s origin. */
    var xs: FloatArray = FloatArray(INITIAL_CAPACITY)
        private set

    /** User-space baseline y of glyph `i`. */
    var ys: FloatArray = FloatArray(INITIAL_CAPACITY)
        private set

    /** Font-specific glyph code of glyph `i`. */
    var codes: IntArray = IntArray(INITIAL_CAPACITY)
        private set

    /** The font glyph `i` is drawn with. */
    var fonts: Array<Font?> = arrayOfNulls(INITIAL_CAPACITY)
        private set

    /** Whether glyph `i` is whitespace (no visible outline, nothing to glow). */
    var blank: BooleanArray = BooleanArray(INITIAL_CAPACITY)
        private set

    private var fastVector: GlyphVector? = null
    private var slowVectors: Array<GlyphVector?> = arrayOfNulls(INITIAL_CAPACITY)
    private var positions = FloatArray(2 * (INITIAL_CAPACITY + 1))
    private var chars = CharArray(INITIAL_CAPACITY)
    private val singleChar = CharArray(2)

    /**
     * Lays out `text[start, end)` (no line breaks or tabs) drawn in [font] with Java [style]. Returns [count].
     */
    fun layout(editor: Editor, text: CharSequence, start: Int, end: Int, font: Font, style: Int, frc: FontRenderContext): Int {
        val length = end - start
        count = 0
        usedFallback = false
        fastVector = null
        if (length <= 0) return 0
        ensureCapacity(length)
        for (i in 0 until length) chars[i] = text[start + i]

        if (font.canDisplayUpTo(chars, 0, length) == -1) {
            val anchor = editor.offsetToPoint2D(start, true, false)
            val tail = editor.offsetToPoint2D(end, false, false)
            if (anchor.y == tail.y) {
                val vector = font.createGlyphVector(frc, String(chars, 0, length))
                val glyphs = vector.numGlyphs
                if (glyphs == length) {
                    vector.getGlyphPositions(0, glyphs + 1, positions)
                    val advance = positions[2 * glyphs]
                    val expected = (tail.x - anchor.x).toFloat()
                    if (abs(advance - expected) <= ADVANCE_TOLERANCE) {
                        vector.getGlyphCodes(0, glyphs, codes)
                        val baseline = (anchor.y + editor.ascent).toFloat()
                        val x0 = anchor.x.toFloat()
                        for (i in 0 until glyphs) {
                            xs[i] = x0 + positions[2 * i]
                            ys[i] = baseline + positions[2 * i + 1]
                            fonts[i] = font
                            // A low surrogate maps to an invisible zero-advance glyph; the pair's glyph sits at the high one.
                            blank[i] = Character.isWhitespace(chars[i]) || Character.isLowSurrogate(chars[i])
                        }
                        fastVector = vector
                        count = glyphs
                        return count
                    }
                }
            }
        }
        return layoutPerCharacter(editor, start, length, style, frc)
    }

    private fun layoutPerCharacter(editor: Editor, start: Int, length: Int, style: Int, frc: FontRenderContext): Int {
        usedFallback = true
        val preferences = editor.colorsScheme.fontPreferences
        val ascent = editor.ascent
        var i = 0
        var n = 0
        while (i < length) {
            val c = chars[i]
            val codePoint: Int
            val charCount: Int
            if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(chars[i + 1])) {
                codePoint = Character.toCodePoint(c, chars[i + 1])
                charCount = 2
            } else {
                codePoint = c.code
                charCount = 1
            }
            if (!Character.isWhitespace(codePoint)) {
                val font = ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, style, preferences, frc).font
                val vector = font.createGlyphVector(frc, singleChar.also { Character.toChars(codePoint, it, 0) }.copyOf(charCount))
                if (vector.numGlyphs > 0) {
                    val point = editor.offsetToPoint2D(start + i, true, false)
                    xs[n] = point.x.toFloat()
                    ys[n] = (point.y + ascent).toFloat()
                    codes[n] = vector.getGlyphCode(0)
                    fonts[n] = font
                    blank[n] = false
                    slowVectors[n] = vector
                    n++
                }
            }
            i += charCount
        }
        count = n
        return n
    }

    /** Outline of glyph `i` relative to its own origin (baseline, left side bearing at `0`). */
    fun outline(i: Int): Shape {
        val fast = fastVector
        if (fast != null) {
            val position = fast.getGlyphPosition(i)
            return fast.getGlyphOutline(i, -position.x.toFloat(), -position.y.toFloat())
        }
        return slowVectors[i]!!.getGlyphOutline(0)
    }

    private fun ensureCapacity(length: Int) {
        if (xs.size >= length) return
        var capacity = xs.size
        while (capacity < length) capacity *= 2
        xs = FloatArray(capacity)
        ys = FloatArray(capacity)
        codes = IntArray(capacity)
        fonts = arrayOfNulls(capacity)
        blank = BooleanArray(capacity)
        slowVectors = arrayOfNulls(capacity)
        positions = FloatArray(2 * (capacity + 1))
        chars = CharArray(capacity)
    }

    companion object {
        private const val INITIAL_CAPACITY = 256

        /** Maximum user-space mismatch between the glyph-vector advance and the editor's layout for the fast path. */
        const val ADVANCE_TOLERANCE: Float = 0.5f

        /**
         * The font render context the editor lays text out with for a given graphics / component context: the same
         * transform and text antialiasing, with the IDE's editor fractional-metrics preference applied, as
         * `EditorView.setFontRenderContext` does.
         */
        fun editorFontRenderContext(context: FontRenderContext): FontRenderContext {
            val fractionalMetrics = UISettings.editorFractionalMetricsHint
            return if (context.fractionalMetricsHint == fractionalMetrics) context
            else FontRenderContext(context.transform, context.antiAliasingHint, fractionalMetrics)
        }
    }
}
