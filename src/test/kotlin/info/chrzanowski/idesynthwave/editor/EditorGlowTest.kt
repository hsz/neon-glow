package info.chrzanowski.idesynthwave.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorGlowTest : BasePlatformTestCase() {

    private val factory get() = EditorFactory.getInstance()

    private fun withMainEditor(text: String, block: (EditorEx) -> Unit) {
        val editor = factory.createEditor(factory.createDocument(text), project, EditorKind.MAIN_EDITOR) as EditorEx
        try {
            block(editor)
        } finally {
            factory.releaseEditor(editor)
        }
    }

    fun `test main editor gets exactly one glow highlighter spanning the document`() = withMainEditor("hello world") { editor ->
        val glow = EditorGlow.of(editor)
        assertNotNull("the editor factory listener must attach a glow", glow)
        val highlighters = glowHighlighters(editor)
        assertEquals(1, highlighters.size)
        val highlighter = highlighters.single()
        assertSame(glow!!.highlighter, highlighter)
        assertEquals(0, highlighter.startOffset)
        assertEquals(editor.document.textLength, highlighter.endOffset)
        assertEquals(CustomHighlighterOrder.AFTER_BACKGROUND, highlighter.customRenderer!!.order)
    }

    fun `test highlighter keeps covering the document across edits`() = withMainEditor("hello") { editor ->
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(0, "<<")
            editor.document.insertString(editor.document.textLength, ">>")
        }
        assertRange(editor, 0, "<<hello>>".length)

        WriteCommandAction.runWriteCommandAction(project) { editor.document.setText("") }
        assertRange(editor, 0, 0)

        WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "again") }
        assertRange(editor, 0, "again".length)
        assertEquals(1, glowHighlighters(editor).size)
    }

    fun `test detach removes the highlighter and attach is idempotent`() = withMainEditor("hello") { editor ->
        val glow = EditorGlow.of(editor)!!

        assertSame(glow, EditorGlow.attach(editor))
        assertEquals(1, glowHighlighters(editor).size)

        EditorGlow.detach(editor)
        assertNull(EditorGlow.of(editor))
        assertNull(glow.highlighter)
        assertEmpty(glowHighlighters(editor))

        EditorGlow.detach(editor) // no-op on a detached editor
        assertEmpty(glowHighlighters(editor))
    }

    fun `test released editor has no glow`() {
        val editor = factory.createEditor(factory.createDocument("hello"), project, EditorKind.MAIN_EDITOR)
        val glow = EditorGlow.of(editor)!!
        assertNotNull(glow.highlighter)

        factory.releaseEditor(editor)
        assertNull(EditorGlow.of(editor))
        assertNull(glow.highlighter)
    }

    fun `test editors without a project or of other kinds are ignored`() {
        val standalone = factory.createEditor(factory.createDocument("standalone"))
        try {
            assertNull(EditorGlow.of(standalone))
            assertEmpty(glowHighlighters(standalone))
        } finally {
            factory.releaseEditor(standalone)
        }

        val console = factory.createEditor(factory.createDocument("console"), project, EditorKind.CONSOLE)
        try {
            assertNull(EditorGlow.of(console))
            assertEmpty(glowHighlighters(console))
        } finally {
            factory.releaseEditor(console)
        }
    }

    private fun assertRange(editor: Editor, start: Int, end: Int) {
        val highlighter = EditorGlow.of(editor)!!.highlighter!!
        assertTrue(highlighter.isValid)
        assertEquals(start, highlighter.startOffset)
        assertEquals(end, highlighter.endOffset)
    }

    private fun glowHighlighters(editor: Editor): List<RangeHighlighter> =
        editor.markupModel.allHighlighters.filter { it.customRenderer is GlowHighlighterRenderer }
}
