package info.chrzanowski.neonglow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import info.chrzanowski.neonglow.actions.ClearGlowCachesAction
import info.chrzanowski.neonglow.actions.CopyGlowDiagnosticsAction
import info.chrzanowski.neonglow.editor.EditorGlow
import info.chrzanowski.neonglow.render.GlyphKey
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor

class NeonGlowToolsTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            GlowSettings.getInstance().loadState(GlowSettings.State())
            GlowManager.getInstance().invalidate()
        } finally { super.tearDown() }
    }

    fun `test tools are registered dumb aware and discoverable in Appearance`() {
        val manager = ActionManager.getInstance()
        val group = manager.getAction("info.chrzanowski.neonglow.Tools") as DefaultActionGroup
        assertEquals("Neon Glow Tools", group.templatePresentation.text)
        for (name in listOf("OpenGlowSettingsAction", "ClearGlowCachesAction", "CopyGlowDiagnosticsAction")) {
            val action = manager.getAction("info.chrzanowski.neonglow.$name")
            assertNotNull(action)
            assertTrue(action is DumbAware)
            assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
            assertTrue(group.getChildActionsOrStubs().any { manager.getId(it) == manager.getId(action) })
        }
        val appearance = manager.getAction("UIToggleActions") as DefaultActionGroup
        assertTrue(appearance.getChildActionsOrStubs().contains(group))
    }

    fun `test diagnostic copy and cache reset preserve user settings`() {
        val manager = GlowManager.getInstance()
        val state = GlowSettings.State(brightness = 0.45f, synthwaveStyle = true)
        manager.applySettings(state)
        manager.atlas.get(GlyphKey(1, "Dialog", 0, 14f, 0x36f9f6, 1f, 6f)) { Rectangle(0, -8, 6, 8) }
        val copy = CopyGlowDiagnosticsAction()
        copy.actionPerformed(TestActionEvent.createTestEvent(copy))
        val report = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)!!
        assertTrue(report.startsWith("Neon Glow diagnostics\n"))
        assertTrue(report.contains("Glyph masks: 1"))
        assertTrue(report.contains("Power Save:"))
        assertTrue(report.contains("brightness=0.45"))
        assertFalse(report.contains(project.basePath ?: "DO_NOT_INCLUDE_PROJECT_PATH"))
        assertFalse(report.contains(System.getProperty("user.home")))
        val reset = ClearGlowCachesAction()
        reset.actionPerformed(TestActionEvent.createTestEvent(reset))
        assertEquals(0, manager.atlas.size)
        assertEquals(state, GlowSettings.getInstance().state)
    }

    fun `test background settings updates capture an independent normalized snapshot`() {
        val manager = GlowManager.getInstance()
        val state = GlowSettings.State(brightness = 0.45f)
        ApplicationManager.getApplication().executeOnPooledThread {
            manager.applySettings(state)
            state.brightness = 0.9f
        }.get()
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(0.45f, GlowSettings.getInstance().state.brightness)
    }

    fun `test target strength and same colour opacity edits preserve warm masks`() {
        val manager = GlowManager.getInstance()
        manager.applySettings(GlowSettings.State())
        val key = GlyphKey(1, "Dialog", 0, 14f, 0x36f9f6, 1f, 6f)
        val mask = manager.atlas.get(key) { Rectangle(0, -8, 6, 8) }
        manager.applySettings(GlowSettings.State(brightness = 0.45f, uiGlowStrength = 0.25f, icons = false))
        assertSame(mask, manager.atlas.find(key))
        manager.applySettings(GlowSettings.State(radiusPx = 9f))
        assertEquals(0, manager.atlas.size)
    }

    fun `test manager disposal removes highlighters and document listeners from still open editors`() {
        myFixture.configureByText(PlainTextFileType.INSTANCE, "unload")
        val local = GlowManager()
        val glow = EditorGlow(myFixture.editor as EditorEx, local, GlowSettings.getInstance())
        val highlighter = glow.highlighter!!
        try {
            local.dispose()
            assertTrue(Disposer.isDisposed(glow))
            assertNull(glow.highlighter)
            assertFalse(highlighter.isValid)
            assertTrue(local.attached.isEmpty())
            val repaints = glow.bleedRepaints
            WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, "after ") }
            assertEquals(repaints, glow.bleedRepaints)
            assertNull(glow.highlighter)
        } finally { if (!Disposer.isDisposed(glow)) Disposer.dispose(glow) }
    }

    fun `test editor registry snapshots are safe across asynchronous lifecycle callbacks`() {
        myFixture.configureByText(PlainTextFileType.INSTANCE, "registry")
        val local = GlowManager()
        val glow = EditorGlow(myFixture.editor as EditorEx, local, GlowSettings.getInstance())
        try {
            val worker = ApplicationManager.getApplication().executeOnPooledThread {
                repeat(5000) { local.unregister(glow); local.register(glow) }
            }
            repeat(5000) { assertTrue(local.attached.size <= 1) }
            worker.get()
            assertEquals(listOf(glow), local.attached)
        } finally { local.dispose() }
    }

    fun `test detachment clears the editors user data and is idempotent`() {
        myFixture.configureByText(PlainTextFileType.INSTANCE, "ownership")
        val editor = myFixture.editor as EditorEx
        val glow = EditorGlow.attach(editor)
        glow.detach()
        assertNull(EditorGlow.of(editor))
        assertNull(glow.highlighter)
        EditorGlow.detach(editor)
        assertNull(EditorGlow.of(editor))
    }
}