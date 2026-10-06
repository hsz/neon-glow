package info.chrzanowski.neonglow.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Scheme
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.util.ui.UIUtil
import info.chrzanowski.neonglow.listeners.GlowApplicationListener
import info.chrzanowski.neonglow.listeners.GlowDynamicPluginListener
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.ui.GlowStartupActivity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.UIManager

class NeonGlowThemesTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        GlowSettings.getInstance().loadState(GlowSettings.State())
    }

    override fun tearDown() {
        try {
            GlowSettings.getInstance().loadState(GlowSettings.State())
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `test delivered theme identification by id and name`() {
        assertFalse(NeonGlowThemes.isDeliveredTheme(null))

        val classic = mockLaf(NeonGlowThemes.CLASSIC_ID, "Neon Glow")
        val midnight = mockLaf(NeonGlowThemes.MIDNIGHT_ID, "Neon Glow Midnight")
        val accessible = mockLaf(NeonGlowThemes.ACCESSIBLE_ID, "Neon Glow Accessible")
        val byIdOnly = mockLaf(NeonGlowThemes.CLASSIC_ID, "Other Custom Name")
        val byNameOnly = mockLaf("custom.id", "Neon Glow Midnight")

        assertTrue(NeonGlowThemes.isDeliveredTheme(classic))
        assertTrue(NeonGlowThemes.isDeliveredTheme(midnight))
        assertTrue(NeonGlowThemes.isDeliveredTheme(accessible))
        assertTrue(NeonGlowThemes.isDeliveredTheme(byIdOnly))
        assertTrue(NeonGlowThemes.isDeliveredTheme(byNameOnly))

        val darcula = mockLaf("Darcula", "Darcula")
        val light = mockLaf("Light", "Light")
        val custom = mockLaf("com.custom.theme", "My Awesome Theme")

        assertFalse(NeonGlowThemes.isDeliveredTheme(darcula))
        assertFalse(NeonGlowThemes.isDeliveredTheme(light))
        assertFalse(NeonGlowThemes.isDeliveredTheme(custom))
    }

    @Test
    fun `test delivered scheme identification by name`() {
        assertFalse(NeonGlowThemes.isDeliveredScheme(null))

        val classicScheme = mockScheme("Neon Glow")
        val midnightScheme = mockScheme("Neon Glow Midnight")
        val accessibleScheme = mockScheme("Neon Glow Accessible")

        assertTrue(NeonGlowThemes.isDeliveredScheme(classicScheme))
        assertTrue(NeonGlowThemes.isDeliveredScheme(midnightScheme))
        assertTrue(NeonGlowThemes.isDeliveredScheme(accessibleScheme))
        assertTrue(NeonGlowThemes.isDeliveredScheme(mockScheme(Scheme.EDITABLE_COPY_PREFIX + "Neon Glow Accessible")))

        val darculaScheme = mockScheme("Darcula")
        val defaultScheme = mockScheme("Default")
        val customScheme = mockScheme("Monokai Pro")

        assertFalse(NeonGlowThemes.isDeliveredScheme(darculaScheme))
        assertFalse(NeonGlowThemes.isDeliveredScheme(defaultScheme))
        assertFalse(NeonGlowThemes.isDeliveredScheme(customScheme))
    }

    @Test
    fun `test restoreUserTheme restores previous user laf and scheme when delivered theme was active`() {
        val userLaf = mockLaf("custom.user.theme", "User Custom Theme")
        val userScheme = mockScheme("User Custom Scheme")

        val testLafManager = TestLafManager(mockLaf(NeonGlowThemes.CLASSIC_ID, "Neon Glow"))

        val originalGlobalScheme = EditorColorsManager.getInstance().globalScheme
        try {
            EditorColorsManager.getInstance().setGlobalScheme(mockScheme("Neon Glow"))

            NeonGlowThemes.restoreUserTheme(testLafManager, userLaf, userScheme)

            assertEquals(userLaf, testLafManager.currentUIThemeLookAndFeel)
            assertEquals(userScheme, EditorColorsManager.getInstance().globalScheme)
        } finally {
            EditorColorsManager.getInstance().setGlobalScheme(originalGlobalScheme)
        }
    }

    @Test
    fun `test restoreUserTheme preserves user theme if delivered theme is not active`() {
        val userLaf = mockLaf("custom.user.theme", "User Custom Theme")
        val testLafManager = TestLafManager(userLaf)

        NeonGlowThemes.restoreUserTheme(testLafManager, null, null)
        assertEquals(userLaf, testLafManager.currentUIThemeLookAndFeel)
    }

    @Test
    fun `test restoreUserTheme leaves current theme and scheme alone without a captured choice`() {
        val currentLaf = mockLaf(NeonGlowThemes.CLASSIC_ID, "Neon Glow")
        val currentScheme = mockScheme("Neon Glow")
        val testLafManager = TestLafManager(currentLaf)
        val colorsManager = EditorColorsManager.getInstance()
        val originalGlobalScheme = colorsManager.globalScheme
        try {
            colorsManager.setGlobalScheme(currentScheme)

            NeonGlowThemes.restoreUserTheme(testLafManager)

            assertSame(currentLaf, testLafManager.currentUIThemeLookAndFeel)
            assertSame(currentScheme, colorsManager.globalScheme)
        } finally {
            colorsManager.setGlobalScheme(originalGlobalScheme)
        }
    }

    @Test
    fun `test restoreUserTheme restores captured light and dark themes with custom schemes`() {
        val colorsManager = EditorColorsManager.getInstance()
        val originalGlobalScheme = colorsManager.globalScheme
        try {
            for (isDark in listOf(false, true)) {
                val userLaf = mockLaf("custom.user.theme", "User Custom Theme", isDark)
                val userScheme = mockScheme("User Custom Scheme")
                val testLafManager = TestLafManager(mockLaf(NeonGlowThemes.CLASSIC_ID, "Neon Glow"))
                colorsManager.setGlobalScheme(mockScheme("Neon Glow"))

                NeonGlowThemes.restoreUserTheme(testLafManager, userLaf, userScheme)

                assertSame(userLaf, testLafManager.currentUIThemeLookAndFeel)
                assertSame(userScheme, colorsManager.globalScheme)
            }
        } finally {
            colorsManager.setGlobalScheme(originalGlobalScheme)
        }
    }

    @Test
    fun `test restoreUserTheme does not guess a scheme when only the previous theme is known`() {
        val userLaf = mockLaf("custom.light.theme", "User Light Theme", false)
        val currentScheme = mockScheme("Neon Glow")
        val testLafManager = TestLafManager(mockLaf(NeonGlowThemes.CLASSIC_ID, "Neon Glow"))
        val colorsManager = EditorColorsManager.getInstance()
        val originalGlobalScheme = colorsManager.globalScheme
        try {
            colorsManager.setGlobalScheme(currentScheme)

            NeonGlowThemes.restoreUserTheme(testLafManager, userLaf)

            assertSame(userLaf, testLafManager.currentUIThemeLookAndFeel)
            assertSame(currentScheme, colorsManager.globalScheme)
        } finally {
            colorsManager.setGlobalScheme(originalGlobalScheme)
        }
    }

    @Test
    fun `test restoreUserTheme preserves deliberately selected bundled choices`() {
        val userLaf = mockLaf(NeonGlowThemes.CLASSIC_ID, "Neon Glow")
        val userScheme = mockScheme("Neon Glow Midnight")
        val testLafManager = TestLafManager(mockLaf(NeonGlowThemes.ACCESSIBLE_ID, "Neon Glow Accessible"))
        val colorsManager = EditorColorsManager.getInstance()
        val originalGlobalScheme = colorsManager.globalScheme
        try {
            colorsManager.setGlobalScheme(mockScheme("Neon Glow Accessible"))

            NeonGlowThemes.restoreUserTheme(testLafManager, userLaf, userScheme)

            assertSame(userLaf, testLafManager.currentUIThemeLookAndFeel)
            assertSame(userScheme, colorsManager.globalScheme)
        } finally {
            colorsManager.setGlobalScheme(originalGlobalScheme)
        }
    }

    @Test
    fun `test dynamic plugin listener captures and restores user theme on install`() {
        val listener = GlowDynamicPluginListener()
        val pluginDescriptor = mockDescriptor(NeonGlowThemes.PLUGIN_ID)
        val otherPlugin = mockDescriptor("com.example.other")

        // Other plugin loaded does not affect settings
        listener.beforePluginLoaded(otherPlugin)
        listener.pluginLoaded(otherPlugin)
        assertFalse(GlowSettings.getInstance().state.initialThemePreserved)

        // Neon Glow plugin loaded triggers theme preservation
        listener.beforePluginLoaded(pluginDescriptor)
        listener.pluginLoaded(pluginDescriptor)
        assertTrue(GlowSettings.getInstance().state.initialThemePreserved)
    }

    @Test
    fun `test first dynamic install preserves light and dark custom themes without a before load event`() {
        val application = ApplicationManager.getApplication()
        val testLafManager = TestLafManager(null)
        application.replaceService(LafManager::class.java, testLafManager, testRootDisposable)
        val colorsManager = EditorColorsManager.getInstance()
        val originalGlobalScheme = colorsManager.globalScheme
        try {
            for (isDark in listOf(false, true)) {
                val userLaf = mockLaf("custom.user.theme", "User Custom Theme", isDark)
                val userScheme = mockScheme("User Custom Scheme")
                testLafManager.setCurrentLookAndFeel(userLaf, true)
                colorsManager.setGlobalScheme(userScheme)

                // The platform schedules theme activation before publishing pluginLoaded.
                application.invokeLater {
                    testLafManager.setCurrentLookAndFeel(mockLaf(NeonGlowThemes.ACCESSIBLE_ID, "Neon Glow Accessible"), false)
                    val schemeName = if (isDark) Scheme.EDITABLE_COPY_PREFIX + "Neon Glow Accessible" else "Neon Glow Accessible"
                    colorsManager.setGlobalScheme(mockScheme(schemeName))
                }
                GlowDynamicPluginListener().pluginLoaded(mockDescriptor(NeonGlowThemes.PLUGIN_ID))
                UIUtil.dispatchAllInvocationEvents()

                assertSame(userLaf, testLafManager.currentUIThemeLookAndFeel)
                assertSame(userScheme, colorsManager.globalScheme)
            }
        } finally {
            UIUtil.dispatchAllInvocationEvents()
            colorsManager.setGlobalScheme(originalGlobalScheme)
        }
    }

    @Test
    fun `test application listener preserves theme on first run and marks initial run handled`() {
        val settings = GlowSettings.getInstance()
        assertFalse(settings.state.initialThemePreserved)

        val listener = GlowApplicationListener()
        listener.appFrameCreated(emptyList())

        assertTrue(settings.state.initialThemePreserved)

        // Second invocation does nothing harmful
        listener.welcomeScreenDisplayed()
        assertTrue(settings.state.initialThemePreserved)
    }

    @Test
    fun `test initial startup and plugin load preserve the current editor scheme`() {
        val colorsManager = EditorColorsManager.getInstance()
        val originalGlobalScheme = colorsManager.globalScheme
        val originalLaf = LafManager.getInstance().currentUIThemeLookAndFeel
        val chosenScheme = mockScheme("Neon Glow Midnight")
        try {
            colorsManager.setGlobalScheme(chosenScheme)

            GlowDynamicPluginListener().pluginLoaded(mockDescriptor(NeonGlowThemes.PLUGIN_ID))
            assertSame(chosenScheme, colorsManager.globalScheme)
            assertSame(originalLaf, LafManager.getInstance().currentUIThemeLookAndFeel)

            GlowSettings.getInstance().loadState(GlowSettings.State())
            GlowApplicationListener().appFrameCreated(emptyList())
            assertSame(chosenScheme, colorsManager.globalScheme)
            assertSame(originalLaf, LafManager.getInstance().currentUIThemeLookAndFeel)

            GlowSettings.getInstance().loadState(GlowSettings.State())
            GlowApplicationListener().welcomeScreenDisplayed()
            assertSame(chosenScheme, colorsManager.globalScheme)
            assertSame(originalLaf, LafManager.getInstance().currentUIThemeLookAndFeel)

            GlowSettings.getInstance().loadState(GlowSettings.State())
            runBlocking { GlowStartupActivity().execute(project) }
            assertSame(chosenScheme, colorsManager.globalScheme)
            assertSame(originalLaf, LafManager.getInstance().currentUIThemeLookAndFeel)
        } finally {
            colorsManager.setGlobalScheme(originalGlobalScheme)
        }
    }

    private fun mockLaf(id: String, name: String, isDark: Boolean = true): UIThemeLookAndFeelInfo {
        var proxyInstance: UIThemeLookAndFeelInfo? = null
        val proxy = Proxy.newProxyInstance(
            UIThemeLookAndFeelInfo::class.java.classLoader,
            arrayOf(UIThemeLookAndFeelInfo::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getId" -> id
                "getName" -> name
                "isDark" -> isDark
                "toString" -> "UIThemeLookAndFeelInfo($id, $name)"
                "equals" -> {
                    val other = args?.getOrNull(0)
                    other === proxyInstance || (other is UIThemeLookAndFeelInfo && other.id == id && other.name == name)
                }
                "hashCode" -> id.hashCode()
                else -> null
            }
        } as UIThemeLookAndFeelInfo
        proxyInstance = proxy
        return proxy
    }

    private fun mockScheme(name: String): EditorColorsScheme {
        val parent = EditorColorsManager.getInstance().getScheme("Darcula") ?: EditorColorsSchemeImpl(null)
        val scheme = EditorColorsSchemeImpl(parent)
        scheme.name = name
        return scheme
    }

    private fun mockDescriptor(id: String): IdeaPluginDescriptor {
        return Proxy.newProxyInstance(
            IdeaPluginDescriptor::class.java.classLoader,
            arrayOf(IdeaPluginDescriptor::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPluginId" -> PluginId.getId(id)
                "getPluginClassLoader" -> javaClass.classLoader
                else -> null
            }
        } as IdeaPluginDescriptor
    }

    private class TestLafManager(private var currentLaf: UIThemeLookAndFeelInfo?) : LafManager() {
        private val delegate: LafManager = LafManager.getInstance()

        override fun getInstalledLookAndFeels(): Array<UIManager.LookAndFeelInfo> = delegate.installedLookAndFeels
        override fun getInstalledThemes(): Sequence<UIThemeLookAndFeelInfo> = delegate.installedThemes
        override fun getLafComboBoxModel(): CollectionComboBoxModel<LafReference> = delegate.lafComboBoxModel
        override fun findLaf(themeId: String): UIThemeLookAndFeelInfo? = delegate.findLaf(themeId)
        @Deprecated("Deprecated in Java")
        override fun getCurrentLookAndFeel(): UIManager.LookAndFeelInfo = delegate.currentLookAndFeel
        override fun getCurrentUIThemeLookAndFeel(): UIThemeLookAndFeelInfo? = currentLaf
        override fun getLookAndFeelReference(): LafReference = delegate.lookAndFeelReference
        override fun getLookAndFeelCellRenderer(component: JComponent): ListCellRenderer<LafReference> = delegate.getLookAndFeelCellRenderer(component)
        override fun createSettingsToolbar(): JComponent = delegate.createSettingsToolbar()
        override fun setCurrentLookAndFeel(lookAndFeelInfo: UIThemeLookAndFeelInfo, lockEditorScheme: Boolean) {
            currentLaf = lookAndFeelInfo
        }
        override fun updateUI() {}
        override fun repaintUI() {}
        override fun getAutodetect(): Boolean = delegate.autodetect
        override fun setAutodetect(value: Boolean) { delegate.autodetect = value }
        override fun getAutodetectSupported(): Boolean = delegate.autodetectSupported
        override fun setPreferredDarkLaf(value: UIThemeLookAndFeelInfo) { delegate.setPreferredDarkLaf(value) }
        override fun setPreferredLightLaf(value: UIThemeLookAndFeelInfo) { delegate.setPreferredLightLaf(value) }
        override fun resetPreferredEditorColorScheme() { delegate.resetPreferredEditorColorScheme() }
        override fun setRememberSchemeForLaf(rememberSchemeForLaf: Boolean) { delegate.setRememberSchemeForLaf(rememberSchemeForLaf) }
        override fun rememberSchemeForLaf(scheme: EditorColorsScheme) { delegate.rememberSchemeForLaf(scheme) }
        @Deprecated("Deprecated in Java")
        override fun addLafManagerListener(listener: LafManagerListener) {}
        @Deprecated("Deprecated in Java")
        override fun removeLafManagerListener(listener: LafManagerListener) {}
        override fun getDefaultLightLaf(): UIThemeLookAndFeelInfo? = delegate.defaultLightLaf
        override fun getDefaultDarkLaf(): UIThemeLookAndFeelInfo? = delegate.defaultDarkLaf
    }
}
