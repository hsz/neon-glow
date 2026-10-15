package info.chrzanowski.neonglow.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.render.GlowStats
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.render.SelectionGlowPainter
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Color
import java.awt.Component
import java.awt.Window
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Point2D
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.CaretListener
import javax.swing.text.DefaultHighlighter
import javax.swing.text.JTextComponent

/**
 * Owns the global paint hook for Swing components and IDE windows.
 *
 * The hook is the only global interception: Swing's `RepaintManager` is deliberately left alone. Replacing it with
 * a delegating wrapper detaches the IDE's per-window buffer strategy from the painting pipeline, so partial
 * repaints hit the screen unsynchronised and components (editor, headers, tabs) blank out for a frame.
 */
class UiGlow(
    atlas: GlyphGlowAtlas,
    private val settings: () -> GlowSettings.State,
    private val powerSave: () -> Boolean,
    stats: GlowStats = GlowStats.DISABLED,
    private val imageAtlas: ImageGlowAtlas = ImageGlowAtlas(),
) : Disposable {

    private var disposed = false
    private val selectionListeners = Disposer.newDisposable()
    private val selectionPainter = SelectionGlowPainter()
    private val textComponents = WeakHashMap<JTextComponent, Unit>()
    private val caretListener = CaretListener { event ->
        if (!disposed && settings().enabled && settings().selectionGlow && !powerSave()) {
            (event.source as? JTextComponent)?.repaint()
        }
    }
    private val selectionListener = object : SelectionListener {
        override fun selectionChanged(event: SelectionEvent) {
            if (!disposed && settings().enabled && settings().selectionGlow && !powerSave()) {
                event.editor.contentComponent.repaint()
            }
        }
    }

    init {
        if (ApplicationManager.getApplication() != null) {
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(selectionListener, selectionListeners)
        }
    }

    private val transform = JBSwingUtilities.addGlobalCGTransform { component, graphics ->
        if (graphics is SelectionGlowGraphics2D && graphics.localPolicy) return@addGlobalCGTransform graphics
        val editorText = isEditorText(component)
        val background = textBackground(component)
        val base = SelectionGlowGraphics2D.withoutSelection(graphics)
        val textGraphics = when {
            disposed -> base
            GlowGraphics2D.isWrapped(base) -> GlowGraphics2D.withTextTarget(base, editorText, background)
            else -> GlowGraphics2D(base, atlas, settings, { disposed || powerSave() }, stats, imageAtlas,
                editorText = editorText, textBackground = background)
        }
        if (disposed || !settings().selectionGlow) textGraphics else {
            val origin = try { textGraphics.transform.createInverse() } catch (_: NoninvertibleTransformException) { null }
            if (origin == null) textGraphics else SelectionGlowGraphics2D(textGraphics, selectionPainter, settings,
                { disposed || powerSave() }, null, background, editorText, selectionAt = { current, bounds ->
                    // Plain Swing children inherit their nearest IDE ancestor's graphics without another hook.
                    val visible = current.clipBounds?.let { bounds.createIntersection(it) } ?: bounds
                    val point = Point2D.Double(visible.centerX, visible.centerY)
                    current.transform.transform(point, point)
                    origin.transform(point, point)
                    val owner = SwingUtilities.getDeepestComponentAt(component, point.x.toInt(), point.y.toInt()) ?: component
                    selectionColour(owner)?.let { SelectionGlowTarget(it, textBackground(owner), isEditorText(owner)) }
                })
        }
    }

    fun repaintAll() {
        for (window in Window.getWindows()) {
            if (window.isDisplayable) window.repaint()
        }
    }

    fun clearCache() {
        imageAtlas.clear()
        selectionPainter.clear()
    }

    internal fun diagnostics(): String =
        "icon masks: ${imageAtlas.size}; icon bytes: ${imageAtlas.bytes}; " +
            "icon hits/misses: ${imageAtlas.hits}/${imageAtlas.misses}; " +
            "selection masks: ${selectionPainter.size}; selection bytes: ${selectionPainter.bytes}"

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

    private fun selectionColour(component: Component): Color? {
        var current: Component? = component
        while (current != null) {
            if (current is EditorComponentImpl) {
                val model = current.editor.selectionModel
                return if (model.hasSelection(true)) model.textAttributes.backgroundColor else null
            }
            if (current is EditorGutterComponentEx) return null
            if (current is JTextComponent) {
                if (textComponents.put(current, Unit) == null) current.addCaretListener(caretListener)
                if (current.selectionStart == current.selectionEnd || !current.caret.isSelectionVisible) return null
                val start = current.selectionStart
                val end = current.selectionEnd
                val painter = current.highlighter?.highlights?.firstOrNull {
                    it.startOffset == start && it.endOffset == end
                }?.painter as? DefaultHighlighter.DefaultHighlightPainter
                return painter?.color ?: current.selectionColor
            }
            current = current.parent
        }
        return null
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        transform.dispose()
        Disposer.dispose(selectionListeners)
        for (component in textComponents.keys) component.removeCaretListener(caretListener)
        textComponents.clear()
        clearCache()
    }
}