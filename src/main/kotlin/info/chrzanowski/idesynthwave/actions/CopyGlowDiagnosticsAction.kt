package info.chrzanowski.idesynthwave.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import info.chrzanowski.idesynthwave.GlowManager
import java.awt.datatransfer.StringSelection

class CopyGlowDiagnosticsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(GlowManager.getInstance().diagnostics()))
    }
}