package info.chrzanowski.neonglow.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.editor.GlowHighlighterRenderer
import info.chrzanowski.neonglow.render.GlowStats
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.*
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.math.max

/** Owns the global paint hook for Swing components and IDE windows. */
class UiGlow(
    atlas: GlyphGlowAtlas,
    settings: () -> GlowSettings.State,
    powerSave: () -> Boolean,
    stats: GlowStats = GlowStats.DISABLED,
    private val imageAtlas: ImageGlowAtlas = ImageGlowAtlas(),
) : Disposable {

    private var disposed = false
    private val previousRepaintManager: RepaintManager?
    private val repaintManager: GlowRepaintManager?

    init {
        val current = RepaintManager.currentManager(null)
        if (current !is GlowRepaintManager) {
            previousRepaintManager = current
            repaintManager = GlowRepaintManager(current, settings)
            RepaintManager.setCurrentManager(repaintManager)
        } else {
            previousRepaintManager = null
            repaintManager = null
        }
    }

    private val transform = JBSwingUtilities.addGlobalCGTransform { component, graphics ->
        val editorText = isEditorText(component)
        val background = textBackground(component)
        when {
            disposed -> graphics
            GlowGraphics2D.isWrapped(graphics) -> GlowGraphics2D.withTextTarget(graphics, editorText, background)
            else -> GlowGraphics2D(graphics, atlas, settings, { disposed || powerSave() }, stats, imageAtlas,
                editorText = editorText, textBackground = background)
        }
    }

    fun repaintAll() {
        for (window in Window.getWindows()) {
            if (window.isDisplayable) window.repaint()
        }
    }

    fun clearCache() {
        imageAtlas.clear()
    }

    internal fun diagnostics(): String =
        "icon masks: ${imageAtlas.size}; icon bytes: ${imageAtlas.bytes}; " +
            "icon hits/misses: ${imageAtlas.hits}/${imageAtlas.misses}"

    private fun isEditorText(component: Component): Boolean {
        var current: Component? = component
        while (current != null) {
            if (current is EditorComponentImpl || current is EditorGutterComponentEx) return true
            current = current.parent
        }
        return false
    }

    private fun textBackground(component: Component): Color? {
        var current: Component? = component
        while (current != null) {
            if (current is EditorComponentImpl) return current.editor.colorsScheme.defaultBackground
            if (current is EditorGutterComponentEx) return current.editor.colorsScheme.defaultBackground
            if (current is JComponent && current.isOpaque) return current.background?.takeIf { it.alpha == 255 }
            current = current.parent
        }
        return null
    }

    override fun dispose() {
        disposed = true
        if (repaintManager != null && RepaintManager.currentManager(null) === repaintManager) {
            RepaintManager.setCurrentManager(previousRepaintManager)
        }
        transform.dispose()
        clearCache()
    }
}

class GlowRepaintManager(
    private val delegate: RepaintManager,
    private val settings: () -> GlowSettings.State,
) : RepaintManager() {

    override fun addDirtyRegion(c: JComponent, x: Int, y: Int, w: Int, h: Int) {
        if (c is EditorComponentImpl && w > 0 && h > 0) {
            val state = settings()
            if (state.enabled && state.editorText && state.brightness > 0f && state.editorGlowStrength > 0f) {
                val editor = c.editor
                if (!editor.isDisposed) {
                    val pad = GlowHighlighterRenderer.repaintInflation(state.radiusPx, state.synthwaveStyle)
                    val top = max(0, y - pad)
                    val bottom = max(top + h, y + h + pad)
                    val width = max(c.width, x + w)
                    delegate.addDirtyRegion(c, 0, top, max(1, width), max(1, bottom - top))
                    return
                }
            }
        }
        delegate.addDirtyRegion(c, x, y, w, h)
    }

    override fun addDirtyRegion(window: Window, x: Int, y: Int, w: Int, h: Int) {
        delegate.addDirtyRegion(window, x, y, w, h)
    }

    @Deprecated("Deprecated in Java")
    override fun addDirtyRegion(applet: java.applet.Applet, x: Int, y: Int, w: Int, h: Int) {
        @Suppress("DEPRECATION")
        delegate.addDirtyRegion(applet, x, y, w, h)
    }

    override fun getDirtyRegion(aComponent: JComponent): Rectangle = delegate.getDirtyRegion(aComponent)
    override fun markCompletelyDirty(aComponent: JComponent) = delegate.markCompletelyDirty(aComponent)
    override fun markCompletelyClean(aComponent: JComponent) = delegate.markCompletelyClean(aComponent)
    override fun isCompletelyDirty(aComponent: JComponent): Boolean = delegate.isCompletelyDirty(aComponent)
    override fun validateInvalidComponents() = delegate.validateInvalidComponents()
    override fun addInvalidComponent(invalidComponent: JComponent) = delegate.addInvalidComponent(invalidComponent)
    override fun removeInvalidComponent(component: JComponent) = delegate.removeInvalidComponent(component)
    override fun paintDirtyRegions() = delegate.paintDirtyRegions()
    override fun setDoubleBufferingEnabled(aFlag: Boolean) { delegate.isDoubleBufferingEnabled = aFlag }
    override fun isDoubleBufferingEnabled(): Boolean = delegate.isDoubleBufferingEnabled
    override fun setDoubleBufferMaximumSize(d: Dimension) { delegate.doubleBufferMaximumSize = d }
    override fun getDoubleBufferMaximumSize(): Dimension = delegate.doubleBufferMaximumSize
    override fun getOffscreenBuffer(c: Component, proposedWidth: Int, proposedHeight: Int): Image =
        delegate.getOffscreenBuffer(c, proposedWidth, proposedHeight)
    override fun getVolatileOffscreenBuffer(c: Component, proposedWidth: Int, proposedHeight: Int): Image =
        delegate.getVolatileOffscreenBuffer(c, proposedWidth, proposedHeight)
}