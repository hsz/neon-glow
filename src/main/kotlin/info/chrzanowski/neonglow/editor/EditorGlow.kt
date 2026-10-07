package info.chrzanowski.neonglow.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

/**
 * Per-editor glow lifecycle: owns one document-wide [RangeHighlighter] in the editor's own markup model whose
 * [GlowHighlighterRenderer] paints the halo under the text, keeps the highlighter covering the whole document,
 * repaints the blur bleed around edited lines and removes everything on [dispose].
 */
class EditorGlow(
    private val editor: EditorEx,
    private val manager: GlowManager = GlowManager.getInstance(),
    private val settings: GlowSettings = GlowSettings.getInstance(),
    val renderer: GlowHighlighterRenderer = GlowHighlighterRenderer(manager.atlas, settings, manager.stats),
) : Disposable {

    var highlighter: RangeHighlighter? = null
        private set

    /** Inflated line-range repaints issued so far (tests). */
    var bleedRepaints: Int = 0
        private set

    private var beforeEditBounds: Rectangle? = null

    private val documentListener = object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            beforeEditBounds = if (!event.document.isInBulkUpdate && canRepaintBleed()) {
                repaintBounds(event.offset, event.oldLength)
            } else null
        }

        override fun documentChanged(event: DocumentEvent) {
            val previousBounds = beforeEditBounds
            beforeEditBounds = null
            ensureHighlighter()
            if (event.document.isInBulkUpdate) return
            repaintBleed(event, previousBounds)
        }

        override fun bulkUpdateFinished(document: Document) {
            ensureHighlighter()
            repaint()
        }
    }

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            if (!canRepaintBleed()) return
            repaintLogicalLine(event.oldPosition.line)
            repaintLogicalLine(event.newPosition.line)
        }

        override fun caretAdded(event: CaretEvent) {
            if (!canRepaintBleed()) return
            repaintLogicalLine(event.newPosition.line)
        }

        override fun caretRemoved(event: CaretEvent) {
            if (!canRepaintBleed()) return
            repaintLogicalLine(event.oldPosition.line)
        }
    }

    private val selectionListener = object : SelectionListener {
        override fun selectionChanged(event: SelectionEvent) {
            if (!canRepaintBleed()) return
            val oldRange = event.oldRange
            val newRange = event.newRange
            if (oldRange.length > 0) {
                val bounds = repaintBounds(oldRange.startOffset, oldRange.length)
                editor.contentComponent.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
            }
            if (newRange.length > 0) {
                val bounds = repaintBounds(newRange.startOffset, newRange.length)
                editor.contentComponent.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
            }
        }
    }

    init {
        ensureHighlighter()
        editor.document.addDocumentListener(documentListener, this)
        editor.caretModel.addCaretListener(caretListener, this)
        editor.selectionModel.addSelectionListener(selectionListener, this)
        manager.register(this)
    }

    /**
     * Recreates the highlighter unless it is still valid and spans the whole document. A greedy marker survives
     * ordinary edits, so this is a cheap sanity check that only acts after unusual document replacements.
     */
    private fun ensureHighlighter() {
        if (editor.isDisposed) return
        val textLength = editor.document.textLength
        highlighter?.let {
            if (it.isValid && it.startOffset == 0 && it.endOffset == textLength) return
            editor.markupModel.removeHighlighter(it)
        }
        highlighter = editor.markupModel.addRangeHighlighter(
            null, 0, textLength, HighlighterLayer.FIRST, HighlighterTargetArea.EXACT_RANGE,
        ).also {
            it.isGreedyToLeft = true
            it.isGreedyToRight = true
            it.customRenderer = renderer
        }
    }

    /**
     * The editor repaints exactly the edited lines; halos spill [GlowHighlighterRenderer.repaintInflation] pixels
     * into the neighbours, so the changed visual-line range is repainted inflated by that amount, full width.
     */
    private fun repaintBleed(event: DocumentEvent, previousBounds: Rectangle?) {
        if (!canRepaintBleed()) return
        val bounds = repaintBounds(event.offset, event.newLength)
        previousBounds?.let { bounds.add(it) }
        editor.contentComponent.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        bleedRepaints++
    }

    private fun repaintLogicalLine(logicalLine: Int) {
        val document = editor.document
        if (logicalLine < 0 || logicalLine >= document.lineCount) return
        val startOffset = document.getLineStartOffset(logicalLine)
        val endOffset = document.getLineEndOffset(logicalLine)
        val bounds = repaintBounds(startOffset, endOffset - startOffset)
        editor.contentComponent.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
    }

    internal fun canRepaintBleed(): Boolean = !editor.isDisposed && settings.state.enabled &&
        settings.state.editorText && settings.state.brightness > 0f && settings.state.editorGlowStrength > 0f

    private fun repaintBounds(offset: Int, length: Int): Rectangle {
        val document = editor.document
        val firstLine = document.getLineNumber(offset)
        val lastLine = document.getLineNumber(min(offset + length, document.textLength))
        val pad = GlowHighlighterRenderer.repaintInflation(settings.state.radiusPx, settings.state.synthwaveStyle)
        val startVisual = editor.offsetToVisualPosition(document.getLineStartOffset(firstLine)).line
        val endVisual = editor.offsetToVisualPosition(document.getLineEndOffset(lastLine)).line
        val top = max(editor.visualLineToY(startVisual) - pad, 0)
        val bottom = editor.visualLineToY(endVisual + 1) + pad
        return Rectangle(0, top, editor.contentComponent.width, bottom - top)
    }

    /** Repaints the whole visible editor content. */
    fun repaint() {
        if (!editor.isDisposed) editor.contentComponent.repaint()
    }

    /** Removes editor ownership as well as the highlighter and document listener, including during plugin unload. */
    fun detach() {
        if (of(editor) === this) editor.putUserData(KEY, null)
        Disposer.dispose(this)
    }

    override fun dispose() {
        manager.unregister(this)
        highlighter?.let { if (!editor.isDisposed) editor.markupModel.removeHighlighter(it) }
        highlighter = null
    }

    companion object {
        private val KEY = Key.create<EditorGlow>("info.chrzanowski.neonglow.EditorGlow")

        /** The glow attached to [editor], if any. */
        @JvmStatic
        fun of(editor: Editor): EditorGlow? = editor.getUserData(KEY)

        /** Attaches a glow to [editor] (idempotent); it is disposed together with the editor at the latest. */
        @JvmStatic
        fun attach(editor: EditorEx): EditorGlow {
            of(editor)?.let { return it }
            val glow = EditorGlow(editor)
            editor.putUserData(KEY, glow)
            EditorUtil.disposeWithEditor(editor, glow)
            return glow
        }

        /** Removes the glow from [editor], if one is attached. */
        @JvmStatic
        fun detach(editor: Editor) {
            val glow = of(editor) ?: return
            glow.detach()
        }
    }
}
