package info.chrzanowski.idesynthwave

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAware
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.idesynthwave.actions.ToggleGlowAction
import info.chrzanowski.idesynthwave.editor.EditorGlow
import info.chrzanowski.idesynthwave.listeners.GlowColorsListener
import info.chrzanowski.idesynthwave.listeners.GlowPowerSaveListener
import info.chrzanowski.idesynthwave.render.GlyphKey
import info.chrzanowski.idesynthwave.settings.GlowSettings
import java.awt.Rectangle
import java.awt.image.BufferedImage

class ToggleGlowActionAndListenersTest : BasePlatformTestCase() {

    private lateinit var manager: GlowManager

    override fun setUp() {
        super.setUp()
        manager = GlowManager.getInstance()
        GlowSettings.getInstance().loadState(GlowSettings.State())
        manager.atlas.clear()
    }

    override fun tearDown() {
        try {
            PowerSaveMode.setEnabled(false)
            GlowSettings.getInstance().loadState(GlowSettings.State())
            manager.atlas.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test toggle action mirrors and drives the manager state`() {
        val action = ToggleGlowAction()
        val event = TestActionEvent.createTestEvent(action)
        assertTrue(action.isSelected(event))

        action.setSelected(event, false)
        assertFalse(manager.isEnabled)
        assertFalse(GlowSettings.getInstance().state.enabled)
        assertFalse(action.isSelected(event))

        action.setSelected(event, true)
        assertTrue(manager.isEnabled)
    }

    fun `test action is registered in the View Appearance group and is dumb aware`() {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(ACTION_ID)
        assertNotNull("action $ACTION_ID must be registered", action)
        assertTrue(action is ToggleGlowAction)
        assertTrue(action is DumbAware)
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
        assertEquals("Synthwave Glow", action.templatePresentation.text)

        val group = actionManager.getAction("UIToggleActions") as DefaultActionGroup
        assertTrue(group.getChildActionsOrStubs().any { actionManager.getId(it) == ACTION_ID })
    }

    fun `test colour scheme change drops the atlas`() {
        manager.atlas.get(GlyphKey(1, "Monospaced", 0, 13f, 0xFF00FF, 1f, 6f)) { Rectangle(0, -8, 6, 8) }
        assertEquals(1, manager.atlas.size)

        GlowColorsListener().globalSchemeChange(null)
        assertEquals(0, manager.atlas.size)

        manager.atlas.get(GlyphKey(1, "Monospaced", 0, 13f, 0xFF00FF, 1f, 6f)) { Rectangle(0, -8, 6, 8) }
        // The listener is registered in plugin.xml, so the real bus reaches it too.
        ApplicationManager.getApplication().messageBus.syncPublisher(EditorColorsManager.TOPIC)
            .globalSchemeChange(EditorColorsManager.getInstance().globalScheme)
        assertEquals(0, manager.atlas.size)
    }

    fun `test power save mode paints nothing and repaints on toggle`() {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("glow"), project, EditorKind.MAIN_EDITOR) as EditorEx
        try {
            editor.component.setSize(400, 200)
            editor.contentComponent.setSize(400, 200)
            val glow = EditorGlow.of(editor)!!
            assertTrue(manager.attached.contains(glow))

            PowerSaveMode.setEnabled(true)
            GlowPowerSaveListener().powerSaveStateChanged()
            paint(editor, glow)
            assertEquals(0, glow.renderer.lastGlyphCount)
            assertEquals(0L, manager.atlas.misses)

            PowerSaveMode.setEnabled(false)
            GlowPowerSaveListener().powerSaveStateChanged()
            paint(editor, glow)
            assertEquals(4, glow.renderer.lastGlyphCount)
        } finally {
            factory.releaseEditor(editor)
        }
        assertTrue(manager.attached.none { it.highlighter != null })
    }

    private fun paint(editor: EditorEx, glow: EditorGlow) {
        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.clip = Rectangle(0, 0, 400, 200)
            glow.renderer.paint(editor, glow.highlighter!!, g)
        } finally {
            g.dispose()
        }
    }

    private companion object {
        const val ACTION_ID = "info.chrzanowski.idesynthwave.ToggleGlowAction"
    }
}
