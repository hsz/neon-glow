package info.chrzanowski.idesynthwave.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import info.chrzanowski.idesynthwave.GlowManager

/**
 * `View | Appearance | Synthwave Glow`: shows or hides the glow in every editor.
 * The state lives in [GlowManager.isEnabled] and is persisted across restarts.
 */
class ToggleGlowAction : ToggleAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean = GlowManager.getInstance().isEnabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        GlowManager.getInstance().isEnabled = state
    }
}
