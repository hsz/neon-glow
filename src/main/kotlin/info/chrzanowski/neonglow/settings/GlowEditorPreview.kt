package info.chrzanowski.neonglow.settings

import com.intellij.ide.PowerSaveMode
import com.intellij.lang.Language
import com.intellij.lexer.LexerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.NeonGlowBundle
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.ui.GlowGraphics2D
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** A real editor with a private glow policy and cache; never applies a draft to the rest of the IDE. */
internal class GlowEditorPreview(state: GlowSettings.State) : JPanel(BorderLayout()), Disposable {

    var previewState: GlowSettings.State = state.normalized()
        private set
    private val atlas = GlyphGlowAtlas(capacity = 256, maxBytes = 4L * 1024 * 1024)
    private val imageAtlas = ImageGlowAtlas()
    private var disposed = false
    internal val editor: EditorEx
    private val transform: Disposable

    init {
        name = "editorPreview"
        getAccessibleContext().accessibleName = NeonGlowBundle.message("settings.preview.title")
        val factory = EditorFactory.getInstance()
        editor = factory.createViewer(factory.createDocument(SAMPLE), null, EditorKind.PREVIEW) as EditorEx
        editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
        editor.highlighter = LexerEditorHighlighter(PreviewSyntaxHighlighter(), editor.colorsScheme)
        editor.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = false
            isLineMarkerAreaShown = false
            isRightMarginShown = false
            isCaretRowShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
        }
        editor.setBorder(JBUI.Borders.empty(8))
        add(editor.component, BorderLayout.CENTER)
        preferredSize = JBUI.size(520, 200)
        // Child-only repaints (selection, scrolling, etc.) must also use the draft, not inherited live glow.
        transform = JBSwingUtilities.addGlobalCGTransform { component, graphics ->
            if (!disposed && (component === this || SwingUtilities.isDescendingFrom(component, this))) wrap(graphics)
            else graphics
        }
    }

    fun update(state: GlowSettings.State) {
        if (disposed) return
        previewState = state.normalized()
        repaint()
    }

    private fun wrap(graphics: Graphics2D): Graphics2D = GlowGraphics2D(
        GlowGraphics2D.withoutGlow(graphics), atlas, { previewState }, { disposed || PowerSaveMode.isEnabled() },
        imageAtlas = imageAtlas, editorText = true, textBackground = editor.colorsScheme.defaultBackground,
    )

    override fun getComponentGraphics(graphics: Graphics): Graphics =
        if (disposed || graphics !is Graphics2D) super.getComponentGraphics(graphics)
        else wrap(super.getComponentGraphics(graphics) as Graphics2D)

    override fun dispose() {
        if (disposed) return
        disposed = true
        transform.dispose()
        EditorFactory.getInstance().releaseEditor(editor)
        atlas.clear()
        imageAtlas.clear()
        removeAll()
    }

    private companion object {
        val SAMPLE = """
            // Keep your theme. Add some light.
            public final class NeonGlowDemo {
                private static final String MESSAGE = "Hello, neon!";

                public static void main(String[] args) {
                    int brightness = 75;
                    System.out.println(MESSAGE + brightness);
                }
            }
        """.trimIndent()
    }
}

/** Generic scheme keys keep the sample coloured even in IDEs without Java language support. */
private class PreviewSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = object : LexerBase() {
        private var text: CharSequence = ""
        private var end = 0
        private var match: MatchResult? = null
        override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
            text = buffer
            end = endOffset
            match = if (startOffset < end) TOKENS.find(text.subSequence(0, end), startOffset) else null
        }
        override fun getState() = 0
        override fun getTokenStart() = match?.range?.first ?: end
        override fun getTokenEnd() = match?.range?.last?.plus(1) ?: end
        override fun getBufferSequence() = text
        override fun getBufferEnd() = end
        override fun getTokenType(): IElementType? = match?.value?.let {
            when {
                it.startsWith("//") -> COMMENT
                it.startsWith('"') -> STRING
                it in KEYWORDS -> KEYWORD
                it.first().isDigit() -> NUMBER
                it.first().isLetter() || it.first() == '_' -> IDENTIFIER
                else -> TEXT
            }
        }
        override fun advance() {
            val next = tokenEnd
            match = if (next < end) TOKENS.find(text.subSequence(0, end), next) else null
        }
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(when (tokenType) {
        COMMENT -> DefaultLanguageHighlighterColors.LINE_COMMENT
        STRING -> DefaultLanguageHighlighterColors.STRING
        KEYWORD -> DefaultLanguageHighlighterColors.KEYWORD
        NUMBER -> DefaultLanguageHighlighterColors.NUMBER
        IDENTIFIER -> DefaultLanguageHighlighterColors.IDENTIFIER
        else -> DefaultLanguageHighlighterColors.OPERATION_SIGN
    })

    private companion object {
        val COMMENT = IElementType("PREVIEW_COMMENT", Language.ANY)
        val STRING = IElementType("PREVIEW_STRING", Language.ANY)
        val KEYWORD = IElementType("PREVIEW_KEYWORD", Language.ANY)
        val NUMBER = IElementType("PREVIEW_NUMBER", Language.ANY)
        val IDENTIFIER = IElementType("PREVIEW_IDENTIFIER", Language.ANY)
        val TEXT = IElementType("PREVIEW_TEXT", Language.ANY)
        val KEYWORDS = setOf("public", "private", "final", "class", "static", "void", "int")
        val TOKENS = Regex("//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|[A-Za-z_]\\w*|\\d+|\\s+|.")
    }
}