package info.chrzanowski.idesynthwave.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import info.chrzanowski.idesynthwave.GlowManager

class ClearGlowCachesAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        GlowManager.getInstance().invalidate()
    }
}