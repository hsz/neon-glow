package info.chrzanowski.idesynthwave.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import info.chrzanowski.idesynthwave.GlowManager
import info.chrzanowski.idesynthwave.settings.GlowSettings
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

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            ensureHighlighter()
            if (event.document.isInBulkUpdate) return
            repaintBleed(event)
        }

        override fun bulkUpdateFinished(document: Document) {
            ensureHighlighter()
            repaint()
        }
    }

    init {
        ensureHighlighter()
        editor.document.addDocumentListener(documentListener, this)
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
    private fun repaintBleed(event: DocumentEvent) {
        if (editor.isDisposed || !settings.state.enabled) return
        val document = event.document
        val firstLine = document.getLineNumber(event.offset)
        val lastLine = document.getLineNumber(min(event.offset + event.newLength, document.textLength))
        val pad = GlowHighlighterRenderer.repaintInflation(settings.state.radiusPx)
        val startVisual = editor.logicalToVisualPosition(LogicalPosition(firstLine, 0)).line
        val endVisual = editor.logicalToVisualPosition(LogicalPosition(lastLine, 0)).line
        val top = editor.visualLineToY(startVisual) - pad
        val bottom = editor.visualLineToY(endVisual + 1) + pad
        val component = editor.contentComponent
        component.repaint(0, max(top, 0), component.width, bottom - max(top, 0))
        bleedRepaints++
    }

    /** Repaints the whole visible editor content. */
    fun repaint() {
        if (!editor.isDisposed) editor.contentComponent.repaint()
    }

    override fun dispose() {
        manager.unregister(this)
        highlighter?.let { if (!editor.isDisposed) editor.markupModel.removeHighlighter(it) }
        highlighter = null
    }

    companion object {
        private val KEY = Key.create<EditorGlow>("info.chrzanowski.idesynthwave.EditorGlow")

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
            editor.putUserData(KEY, null)
            Disposer.dispose(glow)
        }
    }
}
