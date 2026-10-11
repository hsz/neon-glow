package info.chrzanowski.neonglow.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import info.chrzanowski.neonglow.NeonGlowConstants.SPONSOR_GITHUB_URL

/**
 * Opens the GitHub Sponsors page of the plugin. Available in the Neon Glow Tools menu, next to the settings and
 * diagnostics actions, so the sponsorship is always one click away for the users who actually use the plugin, without any nagging.
 */
class SponsorAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(SPONSOR_GITHUB_URL)
}
