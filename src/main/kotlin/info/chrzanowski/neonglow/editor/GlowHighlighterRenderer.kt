package info.chrzanowski.neonglow.editor

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.scale.JBUIScale
import info.chrzanowski.neonglow.render.GaussianBlur
import info.chrzanowski.neonglow.render.GlowMaskRenderer
import info.chrzanowski.neonglow.render.GlowStats
import info.chrzanowski.neonglow.render.GlowWorkBudget
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.GlyphKey
import info.chrzanowski.neonglow.render.SynthwaveTextStyle
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.ui.GlowGraphics2D
import java.awt.AlphaComposite
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints a blurred halo under every glyph visible in the current clip, in the editor's background pass
 * ([CustomHighlighterOrder.AFTER_BACKGROUND]) so the glow lands under the text and over the line background.
 *
 * Per paint: the clip is inflated by the blur padding (halos of neighbouring lines spill into it), mapped to a
 * logical line range, and the lexer tokens of those lines are walked with the editor's [EditorEx.getHighlighter].
 * Each non-blank, unfolded token is split into single-line, tab-free segments, laid out by [TokenLayout] and
 * blitted glyph by glyph from the [GlyphGlowAtlas] at integer device-pixel positions. Everything outside the
 * real clip is discarded by the graphics clip, so over-iteration is harmless.
 *
 * HiDPI: masks are device-pixel images. On a Retina surface `Graphics2D.getTransform()` hides the device scale
 * (it lives in the surface), on an offscreen image it is an explicit `scale(2)`; [JBUIScale.sysScale] covers both.
 * Each mask is drawn through a `1 / sysScale` transform positioned at `devicePixel / sysScale`, so the composite
 * transform is a pure integer device translation and the blit is 1:1 — no resampling, no mushy halo.
 */
class GlowHighlighterRenderer(
    private val atlas: GlyphGlowAtlas,
    private val settings: GlowSettings,
    private val stats: GlowStats = GlowStats.DISABLED,
) : CustomHighlighterRenderer {

    private val clip = Rectangle()
    private val point = Point()
    private val layout = TokenLayout()
    private val blit = AffineTransform()
    private var graphicsContext: FontRenderContext? = null
    private var editorContext: FontRenderContext? = null
    private var workBudget = GlowWorkBudget()

    /** Glyphs blitted by the last [paint] (tests, statistics). */
    var lastGlyphCount: Int = 0
        private set

    /** Segments that took the per-character layout path during the last [paint] (statistics). */
    var lastFallbackSegments: Int = 0
        private set

    override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_BACKGROUND

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        lastGlyphCount = 0
        lastFallbackSegments = 0
        workBudget = GlowWorkBudget()
        val state = settings.state
        if (!state.enabled || !state.editorText || state.editorGlowStrength <= 0f || state.brightness <= 0f || PowerSaveMode.isEnabled()) return
        if (!state.regularText && !state.synthwaveStyle) return
        if (g is Graphics2D && GlowGraphics2D.isGlowing(g)) return
        val source = g as? Graphics2D ?: return
        val alpha = source.composite as? AlphaComposite ?: return
        if (alpha.rule != AlphaComposite.SRC_OVER || alpha.alpha == 0f) return
        val halo = source.create() as Graphics2D
        try {
            halo.composite = alpha.derive(alpha.alpha * state.editorGlowStrength)
            if (!stats.enabled) {
                paintGlow(editor, state, halo)
                return
            }
            val start = System.nanoTime()
            paintGlow(editor, state, halo)
            stats.recordPaint((System.nanoTime() - start) / 1_000, lastGlyphCount, lastFallbackSegments, atlas)
        } finally {
            halo.dispose()
        }
    }

    private fun paintGlow(editor: Editor, state: GlowSettings.State, g: Graphics) {
        val editorEx = editor as? EditorEx ?: return
        val document = editor.document
        if (document.textLength == 0) return
        val g2 = g as Graphics2D
        g2.getClipBounds(clip)
        if (clip.isEmpty) return

        val radius = state.radiusPx
        val sysScale = JBUIScale.sysScale(g2).takeIf { it > 0f } ?: 1f
        val padUser = repaintInflation(radius, state.synthwaveStyle)
        clip.grow(padUser, padUser)

        val lineCount = document.lineCount
        point.setLocation(0, clip.y)
        val startLine = editor.xyToLogicalPosition(point).line
        if (startLine >= lineCount) return
        point.setLocation(0, clip.y + clip.height)
        val endLine = min(editor.xyToLogicalPosition(point).line, lineCount - 1)
        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)
        if (endOffset <= startOffset) return

        atlas.intensity = state.intensity
        val scheme = editor.colorsScheme
        val backgroundRgb = scheme.defaultBackground.rgb
        val effectiveBrightness = state.effectiveBrightness(backgroundRgb)
        val defaultForeground = scheme.defaultForeground.rgb
        val text = document.immutableCharSequence
        val frc = editorFontRenderContext(g2)
        val foldingModel = editor.foldingModel

        val oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
        val alpha = g2.composite as AlphaComposite
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        try {
            val iterator = editorEx.highlighter.createIterator(startOffset)
            while (!iterator.atEnd() && iterator.start < endOffset) {
                val tokenStart = maxOf(iterator.start, startOffset)
                val tokenEnd = min(iterator.end, endOffset)
                if (tokenEnd > tokenStart && !foldingModel.isOffsetCollapsed(tokenStart)) {
                    val attributes = iterator.textAttributes
                    val argb = (attributes?.foregroundColor?.rgb ?: defaultForeground) or ALPHA_MASK
                    val style = attributes?.fontType ?: Font.PLAIN
                    val font = scheme.getFont(EditorFontType.forJavaStyle(style))
                    val layered = state.synthwaveStyle && SynthwaveTextStyle.rule(argb, backgroundRgb) != null
                    if (layered || state.regularText) {
                        g2.composite = alpha.derive(alpha.alpha * if (layered) 1f else effectiveBrightness)
                        paintSegments(editor, text, tokenStart, tokenEnd, font, style, argb, frc, sysScale, radius, g2)
                    }
                }
                iterator.advance()
            }
        } finally {
            g2.composite = alpha
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation ?: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        }
    }

    /** [TokenLayout.editorFontRenderContext] of the graphics' context, cached while the context stays the same. */
    private fun editorFontRenderContext(g2: Graphics2D): FontRenderContext {
        val context = g2.fontRenderContext
        val cached = editorContext
        if (cached != null && context == graphicsContext) return cached
        val editorFrc = TokenLayout.editorFontRenderContext(context)
        graphicsContext = context
        editorContext = editorFrc
        return editorFrc
    }

    /** Splits `text[start, end)` at line breaks, tabs and surrounding whitespace and paints each piece. */
    private fun paintSegments(
        editor: Editor, text: CharSequence, start: Int, end: Int, font: Font, style: Int, argb: Int,
        frc: FontRenderContext, sysScale: Float, radius: Float, g2: Graphics2D,
    ) {
        var i = start
        while (i < end) {
            while (i < end && isSeparator(text[i])) i++
            if (i >= end) return
            val segmentStart = i
            while (i < end && !isSeparator(text[i])) i++
            paintSegment(editor, text, segmentStart, i, font, style, argb, frc, sysScale, radius, g2)
        }
    }

    private fun isSeparator(c: Char): Boolean = c == '\n' || c == '\t' || c == '\r'

    private fun paintSegment(
        editor: Editor, text: CharSequence, start: Int, end: Int, font: Font, style: Int, argb: Int,
        frc: FontRenderContext, sysScale: Float, radius: Float, g2: Graphics2D,
    ) {
        val count = layout.layout(editor, text, start, end, font, style, frc)
        if (layout.usedFallback) lastFallbackSegments++
        if (count == 0) return
        val xs = layout.xs
        val ys = layout.ys
        val codes = layout.codes
        val fonts = layout.fonts
        val blank = layout.blank
        val state = settings.state
        val backgroundRgb = editor.colorsScheme.defaultBackground.rgb
        val effectiveBrightness = state.effectiveBrightness(backgroundRgb)
        val rule = if (state.synthwaveStyle) SynthwaveTextStyle.rule(argb, backgroundRgb) else null
        val layered = rule != null
        val inverseScale = 1.0 / sysScale
        var keyFont: Font? = null
        var family = ""
        for (i in 0 until count) {
            if (blank[i]) continue
            val glyphFont = fonts[i]!!
            if (glyphFont !== keyFont) {
                keyFont = glyphFont
                family = glyphFont.family
            }
            val key = GlyphKey(codes[i], family, glyphFont.style, glyphFont.size2D, argb, sysScale, radius,
                synthwaveStyle = layered, brightness = if (layered) effectiveBrightness else 1f, textStyleRule = rule)
            var mask = atlas.find(key)
            if (mask == null) {
                if (!workBudget.allowGlyph(state.performanceMode)) continue
                val outline = layout.outline(i)
                val maxRadius = if (layered) SynthwaveTextStyle.maxRadius(radius) else radius
                if (!GlowWorkBudget.safeRaster(outline.bounds2D, sysScale, maxRadius)) continue
                mask = atlas.render(key, outline)
            }
            val deviceX = (xs[i] * sysScale).roundToInt() + mask.offsetX
            val deviceY = (ys[i] * sysScale).roundToInt() + mask.offsetY
            blit.setTransform(inverseScale, 0.0, 0.0, inverseScale, deviceX * inverseScale, deviceY * inverseScale)
            g2.drawImage(mask.image, blit, null)
            lastGlyphCount++
        }
    }

    companion object {
        private const val ALPHA_MASK = 0xFF shl 24

        /**
         * User-space pixels a halo may spill beyond its glyph for a given glow radius: the raster padding `3σ`
         * converted back to user space (`σ = radius · scale / 2`, so `3σ / scale = 1.5 · radius`), rounded up.
         * SynthWave-style text uses the widest shadow layer. Clips are inflated and edited lines repainted by
         * this amount.
         */
        fun repaintInflation(radiusPx: Float, synthwaveStyle: Boolean = false): Int =
            GaussianBlur.radius(GlowMaskRenderer.sigmaFor(
                if (synthwaveStyle) SynthwaveTextStyle.maxRadius(radiusPx) else radiusPx, 1f)) + 1
    }
}
