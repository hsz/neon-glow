package info.chrzanowski.neonglow.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import info.chrzanowski.neonglow.NeonGlowBundle

class GlowPresetTest : BasePlatformTestCase() {

    fun `test presets have deterministic independent settings`() {
        val classic = GlowSettings.State(enabled = true, brightness = 0.45f, intensity = 1f, radiusPx = 6f,
            synthwaveStyle = true, editorGlowStrength = 1f, uiGlowStrength = 0.25f, iconGlowStrength = 0.35f,
            editorText = true, uiText = true, icons = true, performanceMode = false, regularText = true)
        val expected = mapOf(
            GlowPreset.CLASSIC to classic,
            GlowPreset.NEON to classic.copy(brightness = 0.7f, intensity = 2f, uiGlowStrength = 0.45f, iconGlowStrength = 0.65f),
            GlowPreset.FOCUS to classic.copy(uiText = false, icons = false, uiGlowStrength = 0f, iconGlowStrength = 0f,
                performanceMode = true),
            GlowPreset.ACCESSIBLE to GlowSettings.State(enabled = false, brightness = 1f, intensity = 3f,
                synthwaveStyle = false, editorGlowStrength = 1f, uiGlowStrength = 1f, iconGlowStrength = 1f,
                regularText = true),
        )
        assertEquals(expected.keys, GlowPreset.entries.toSet())
        for ((preset, state) in expected) {
            val first = preset.createState()
            val second = preset.createState()
            assertEquals(state, first)
            assertEquals(state, second)
            assertNotSame(first, second)
            assertEquals(first, first.normalized())
            first.enabled = !first.enabled
            first.radiusPx = 16f
            first.editorGlowStrength = 0f
            first.performanceMode = !first.performanceMode
            assertEquals(state, second)
            assertEquals(state, preset.createState())
        }
        for ((preset, state) in expected) assertEquals(state, preset.createState())
    }

    fun `test preset names are localized and identity is not persisted`() {
        val keys = mapOf(
            GlowPreset.CLASSIC to "settings.preset.classic",
            GlowPreset.NEON to "settings.preset.neon",
            GlowPreset.FOCUS to "settings.preset.focus",
            GlowPreset.ACCESSIBLE to "settings.preset.accessible",
        )
        for ((preset, key) in keys) {
            assertEquals(NeonGlowBundle.message(key), preset.toString())
            val xml = XmlSerializer.serialize(preset.createState())
            assertFalse(xml.children.any { it.getAttributeValue("name")?.contains("preset", ignoreCase = true) == true })
        }
    }
}