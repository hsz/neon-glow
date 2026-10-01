package info.chrzanowski.idesynthwave.editor

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx

/**
 * Attaches an [EditorGlow] to every main editor that belongs to a project and detaches it when the editor is
 * released. Console, diff, preview and other auxiliary editors are left alone.
 */
class GlowEditorFactoryListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor as? EditorEx ?: return
        if (editor.editorKind != EditorKind.MAIN_EDITOR || editor.project == null) return
        EditorGlow.attach(editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        EditorGlow.detach(event.editor)
    }
}
