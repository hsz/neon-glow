package info.chrzanowski.idesynthwave.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import info.chrzanowski.idesynthwave.GlowManager

/** Also initializes the hook when the plugin is loaded into an already running project. */
class GlowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        GlowManager.getInstance().installUi()
    }
}