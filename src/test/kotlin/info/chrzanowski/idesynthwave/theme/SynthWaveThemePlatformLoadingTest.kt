package info.chrzanowski.idesynthwave.theme

import com.intellij.ide.ui.UITheme
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.attributeValues
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.attributes
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.bytes
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.color
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.colors
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.resolveColor
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.scheme
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.theme
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.uiColor
import info.chrzanowski.idesynthwave.theme.SynthWaveThemeResources.variants
import javax.swing.UIDefaults

class SynthWaveThemePlatformLoadingTest : BasePlatformTestCase() {
    fun testResourcesLoadThroughInstalledThemeAndSchemeParsers() {
        val parent = checkNotNull(EditorColorsManager.getInstance().getScheme("Darcula"))
        for (variant in variants) {
            val json = theme(variant)
            val loadedTheme = UITheme.loadFromJsonWithParent(
                bytes("/themes/${variant.file}.theme.json"),
                variant.id,
                javaClass.classLoader,
            )
            assertEquals(variant.name, loadedTheme.name)
            val defaults = UIDefaults()
            loadedTheme.applyTheme(defaults)
            for (key in listOf(
                "MainWindow.background",
                "ToolWindow.background",
                "Component.focusedBorderColor",
                "EditorTabs.underlinedBorderColor",
                "List.selectionBackground",
                "Tree.selectionInactiveBackground",
                "Notification.ToolWindow.warningForeground",
            )) {
                assertEquals("Loaded theme UI value: $key", uiColor(json, key), defaults.getColor(key))
            }
            assertEquals(1, defaults["Islands"])

            val xml = scheme(json)
            val loadedScheme = EditorColorsSchemeImpl(parent)
            loadedScheme.readExternal(xml)
            assertEquals(variant.name, loadedScheme.name)
            assertEquals(resolveColor(json, "Editor"), loadedScheme.defaultBackground)
            assertEquals(resolveColor(json, "Foreground"), loadedScheme.defaultForeground)
            assertEquals(
                "Unspecified attributes still inherit from Darcula",
                parent.getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES),
                loadedScheme.getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES),
            )

            val attributes = attributes(xml)
            for ((name, value) in colors(xml)) {
                assertEquals("Loaded scheme color: $name", color(value), loadedScheme.getColor(ColorKey.createColorKey(name)))
            }
            for ((name, option) in attributes) {
                if (option.getChild("value") == null) continue
                val expected = attributeValues(attributes, name)
                val actual = checkNotNull(loadedScheme.getAttributes(TextAttributesKey.createTextAttributesKey(name))) {
                    "Platform failed to load $name from ${variant.file}"
                }
                expected["FOREGROUND"]?.let {
                    assertEquals(
                        "Loaded $name foreground",
                        color(it),
                        actual.foregroundColor,
                    )
                }
                expected["BACKGROUND"]?.let {
                    assertEquals(
                        "Loaded $name background",
                        color(it),
                        actual.backgroundColor,
                    )
                }
                expected["EFFECT_COLOR"]?.let { assertEquals("Loaded $name effect", color(it), actual.effectColor) }
                expected["ERROR_STRIPE_COLOR"]?.let {
                    assertEquals(
                        "Loaded $name stripe",
                        color(it),
                        actual.errorStripeColor,
                    )
                }
            }
            assertEquals(
                color(attributeValues(attributes, "DEFAULT_NUMBER").getValue("FOREGROUND")),
                loadedScheme.getAttributes(DefaultLanguageHighlighterColors.NUMBER).foregroundColor,
            )
            assertEquals(
                color(attributeValues(attributes, "DEFAULT_DOC_COMMENT").getValue("FOREGROUND")),
                loadedScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT).foregroundColor,
            )
            assertEquals(
                color(attributeValues(attributes, "CODE_LENS_BORDER_COLOR").getValue("EFFECT_COLOR")),
                loadedScheme.getAttributes(EditorColors.CODE_LENS_BORDER_COLOR).effectColor,
            )
            assertEquals(
                EffectType.WAVE_UNDERSCORE,
                loadedScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).effectType,
            )
            assertEquals(
                EffectType.WAVE_UNDERSCORE,
                loadedScheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).effectType,
            )
        }
    }
}
