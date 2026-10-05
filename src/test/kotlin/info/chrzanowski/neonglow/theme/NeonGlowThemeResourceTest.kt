package info.chrzanowski.neonglow.theme

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.intellij.openapi.components.State
import info.chrzanowski.neonglow.NeonGlowBundle
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.attributeValues
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.attributes
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.color
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.colors
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.contrast
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.flatten
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.json
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.resolveColor
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.scheme
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.theme
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.uiColor
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.variants
import info.chrzanowski.neonglow.theme.NeonGlowThemeResources.xml
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color

class NeonGlowThemeResourceTest {
    @Test
    fun pluginIdentitySettingsAndThemeRegistrationsUseNeonGlow() {
        val descriptor = xml("/META-INF/plugin.xml")
        assertEquals("info.chrzanowski.neonglow", descriptor.getChildTextTrim("id"))
        assertEquals("Neon Glow", descriptor.getChildTextTrim("name"))
        assertEquals("messages.NeonGlowBundle", descriptor.getChildTextTrim("resource-bundle"))
        assertEquals("Neon Glow", NeonGlowBundle.message("settings.displayName"))

        val extensions = descriptor.getChild("extensions")!!
        val configurable = extensions.getChild("applicationConfigurable")!!
        assertEquals("Neon Glow", configurable.getAttributeValue("displayName"))
        assertEquals("info.chrzanowski.neonglow.settings.GlowConfigurable", configurable.getAttributeValue("id"))
        assertEquals(configurable.getAttributeValue("id"), configurable.getAttributeValue("instance"))
        val providers = extensions.getChildren("themeProvider")
            .associate { it.getAttributeValue("id") to it.getAttributeValue("path") }
        assertEquals(variants.associate { it.id to "/themes/${it.file}.theme.json" }, providers)

        val state = GlowSettings::class.java.getAnnotation(State::class.java)
        assertEquals("NeonGlow", state.name)
        assertEquals(listOf("neon-glow.xml"), state.storages.map { it.value })
    }

    @Test
    fun themesAndLinkedSchemesHaveUniqueIdentitiesAndValidStructure() {
        assertEquals(3, variants.map { it.id }.distinct().size)
        assertEquals(3, variants.map { it.name }.distinct().size)
        for (variant in variants) {
            val theme = theme(variant)
            assertEquals(variant.name, theme.required("name").asText())
            assertTrue(theme.required("dark").asBoolean())
            assertEquals("Islands Dark", theme.required("parentTheme").asText())
            assertEquals("/themes/${variant.file}.xml", theme.required("editorScheme").asText())
            assertEquals(1, theme.required("ui").required("Islands").asInt())
            assertFalse(theme.required("ui").has("colors"))
            assertEquals(uiColor(theme, "ToolWindow.background"), uiColor(theme, "Island.borderColor"))
            assertTrue(theme.required("author").asText().isNotBlank())
            assertEquals(setOf("ColorPalette"), theme.required("icons").fieldNames().asSequence().toSet())

            theme.required("colors").fieldNames().forEachRemaining { resolveColor(theme, it) }
            flatten(theme.required("ui")).forEach { (key, value) ->
                assertFalse("Broad UI wildcard: $key", key.contains('*'))
                if (value.isTextual) resolveColor(theme, value.asText())
                else assertTrue(
                    "Unexpected non-color UI setting: $key",
                    key in setOf("Islands", "Tree.forceFocusedSelectionForeground"),
                )
            }
            theme.required("icons").required("ColorPalette").elements().forEachRemaining {
                resolveColor(theme, it.asText())
            }

            val scheme = scheme(theme)
            assertEquals("scheme", scheme.name)
            assertEquals(variant.name, scheme.getAttributeValue("name"))
            assertEquals("142", scheme.getAttributeValue("version"))
            assertEquals("Darcula", scheme.getAttributeValue("parent_scheme"))
            val attributes = attributes(scheme)
            colors(scheme).values.forEach { color(it) }
            for ((name, option) in attributes) {
                val values = attributeValues(attributes, name)
                assertTrue("Empty attributes: $name", values.isNotEmpty())
                values.forEach { (field, value) ->
                    when (field) {
                        "FONT_TYPE", "EFFECT_TYPE" -> value.toInt()
                        "FOREGROUND", "BACKGROUND", "EFFECT_COLOR", "ERROR_STRIPE_COLOR" -> color(value)
                        else -> throw AssertionError("Unsupported attribute field: $name/$field")
                    }
                }
                if (option.getAttributeValue("baseAttributes") != null) {
                    assertTrue(
                        "Only native language fallback markers are allowed: $name",
                        name in nativeLanguageKeys || name.startsWith("JAVA_") || name.startsWith("KOTLIN_"),
                    )
                }
            }
            assertEquals(
                resolveColor(theme, "Editor"),
                color(attributeValues(attributes, "TEXT").getValue("BACKGROUND")),
            )
            assertEquals(
                resolveColor(theme, "Foreground"),
                color(attributeValues(attributes, "TEXT").getValue("FOREGROUND")),
            )
            assertEquals(resolveColor(theme, "Chrome"), color(colors(scheme).getValue("CONSOLE_BACKGROUND_KEY")))
            assertEquals(
                attributeValues(attributes, "DEFAULT_CONSTANT")["FOREGROUND"],
                attributeValues(attributes, "STATIC_FINAL_FIELD_ATTRIBUTES")["FOREGROUND"],
            )
            assertEquals(
                attributeValues(attributes, "DEFAULT_METADATA")["FOREGROUND"],
                attributeValues(attributes, "KOTLIN_BUILTIN_ANNOTATION")["FOREGROUND"],
            )
            assertEquals(
                resolveColor(theme, "Chrome"),
                color(colors(scheme).getValue("BLOCK_TERMINAL_DEFAULT_BACKGROUND")),
            )
        }
    }

    @Test
    fun uiKeysAreDocumentedOrUsedByTheInstalledBaseline() {
        val supported = buildSet {
            for (path in listOf(
                "/themes/metadata/IntelliJPlatform.themeMetadata.json",
                "/themes/metadata/JDK.themeMetadata.json",
            )) {
                json(path).required("ui").forEach { add(it.required("key").asText()) }
            }
            for (path in listOf(
                "/themes/darcula.theme.json",
                "/themes/expUI/expUI_dark.theme.json",
                "/themes/islands/ManyIslandsDark.theme.json",
            )) {
                addAll(flatten(json(path).required("ui")).keys)
            }
        }
        for (variant in variants) {
            val unknown = flatten(theme(variant).required("ui")).keys - supported
            assertTrue("Unknown installed-platform UI keys in ${variant.file}: $unknown", unknown.isEmpty())
        }
    }

    @Test
    fun parentThemeIsAnInstalledProviderIdWithExistingResources() {
        fun descendants(element: org.jdom.Element): Sequence<org.jdom.Element> = sequence {
            yield(element)
            element.children.forEach { yieldAll(descendants(it)) }
        }

        val providers = descendants(xml("/META-INF/PlatformExtensions.xml"))
            .filter { it.name == "themeProvider" }
            .associate { it.getAttributeValue("id") to it.getAttributeValue("path") }
        for (variant in variants) {
            val parentId = theme(variant).required("parentTheme").asText()
            val path = checkNotNull(providers[parentId]) { "Missing installed theme provider: $parentId" }
            assertTrue(json(path).required("dark").asBoolean())
        }
    }

    @Test
    fun colorAliasesResolveAndInvalidAliasesAreRejected() {
        val fixture = JsonNodeFactory.instance.objectNode()
        val palette = fixture.putObject("colors")
        palette.put("literal", "#36F9F6")
        palette.put("alias", "literal")
        palette.put("chain", "alias")
        assertEquals(color("36F9F6"), resolveColor(fixture, "chain"))
        assertThrows(IllegalStateException::class.java) { resolveColor(fixture, "missing") }
        palette.put("cycleA", "cycleB")
        palette.put("cycleB", "cycleA")
        assertThrows(IllegalStateException::class.java) { resolveColor(fixture, "cycleA") }
        palette.put("transparent", "#FFFFFF80")
        assertThrows(IllegalStateException::class.java) { resolveColor(fixture, "transparent") }
    }

    @Test
    fun schemeKeyTypesMatchTheBundledPlatformSchemes() {
        val bundled = xml("/DefaultColorSchemesManager.xml").getChildren("scheme") +
                xml("/themes/islands/IslandSchemeDark.xml")
        val colorNames = bundled.flatMap { it.getChild("colors")?.getChildren("option").orEmpty() }
            .map { it.getAttributeValue("name") }.toSet()
        val attributeNames = bundled.flatMap { it.getChild("attributes")?.getChildren("option").orEmpty() }
            .map { it.getAttributeValue("name") }.toSet()
        for (variant in variants) {
            val scheme = scheme(theme(variant))
            val wrongColors = colors(scheme).keys.intersect(attributeNames - colorNames)
            val wrongAttributes = attributes(scheme).keys.intersect(colorNames - attributeNames)
            assertTrue("Text attributes incorrectly placed in colors: $wrongColors", wrongColors.isEmpty())
            assertTrue("Colors incorrectly placed in attributes: $wrongAttributes", wrongAttributes.isEmpty())
            assertTrue(attributes(scheme).containsKey("CODE_LENS_BORDER_COLOR"))
            assertFalse(colors(scheme).containsKey("CODE_LENS_BORDER_COLOR"))
        }
    }

    @Test
    fun hoverSelectionAndDisabledStatesRemainDistinct() {
        for (variant in variants) {
            val theme = theme(variant)
            assertNotEquals(resolveColor(theme, "Hover"), resolveColor(theme, "Pressed"))
            assertNotEquals(resolveColor(theme, "Foreground"), resolveColor(theme, "Disabled"))
            assertNotEquals(uiColor(theme, "TextField.background"), uiColor(theme, "TextField.disabledBackground"))
            for (component in listOf("List", "Tree", "Table")) {
                assertNotEquals(
                    uiColor(theme, "$component.background"),
                    uiColor(theme, "$component.selectionBackground"),
                )
                assertNotEquals(
                    uiColor(theme, "$component.selectionBackground"),
                    uiColor(theme, "$component.selectionInactiveBackground"),
                )
                readable(
                    "${variant.name}/$component selected",
                    uiColor(theme, "$component.selectionForeground"),
                    uiColor(theme, "$component.selectionBackground"),
                )
                readable(
                    "${variant.name}/$component inactive selected",
                    uiColor(theme, "$component.selectionInactiveForeground"),
                    uiColor(theme, "$component.selectionInactiveBackground"),
                )
            }
            assertNotEquals(
                uiColor(theme, "EditorTabs.background"),
                uiColor(theme, "EditorTabs.underlinedTabBackground"),
            )
            assertNotEquals(
                uiColor(theme, "EditorTabs.underlinedTabBackground"),
                uiColor(theme, "EditorTabs.inactiveUnderlinedTabBackground"),
            )
            assertEquals(resolveColor(theme, "Accent"), uiColor(theme, "EditorTabs.underlinedBorderColor"))
            assertEquals(resolveColor(theme, "Focus"), uiColor(theme, "Component.focusedBorderColor"))
        }
    }

    @Test
    fun diagnosticsAndSvgColorsPreserveTheirSemantics() {
        for (variant in variants) {
            val theme = theme(variant)
            val scheme = scheme(theme)
            val attributes = attributes(scheme)
            val warning = resolveColor(theme, "Warning")
            val error = resolveColor(theme, "Error")
            val success = resolveColor(theme, "Success")
            val info = resolveColor(theme, "Info")
            assertEquals(4, setOf(warning, error, success, info).size)
            assertTrue("Warning must be amber, not green", warning.red > warning.blue && warning.green > warning.blue)
            assertTrue("Success must be mint", success.green > success.red && success.green > success.blue)
            for ((key, expected) in mapOf(
                "WARNING_ATTRIBUTES" to warning,
                "INFO_ATTRIBUTES" to warning,
                "ERRORS_ATTRIBUTES" to error,
                "INFORMATION_ATTRIBUTES" to info,
            )) {
                val values = attributeValues(attributes, key)
                assertEquals("$key effect", expected, color(values.getValue("EFFECT_COLOR")))
                assertEquals("$key stripe", expected, color(values.getValue("ERROR_STRIPE_COLOR")))
                assertEquals("$key wave underline", "2", values["EFFECT_TYPE"])
                assertFalse("Diagnostics must not wash out syntax: $key", values.containsKey("FOREGROUND"))
            }
            assertEquals(success, color(colors(scheme).getValue("ADDED_LINES_COLOR")))
            assertEquals(success, color(attributeValues(attributes, "DIFF_INSERTED").getValue("ERROR_STRIPE_COLOR")))
            assertEquals(error, color(attributeValues(attributes, "DIFF_DELETED").getValue("ERROR_STRIPE_COLOR")))
            assertEquals(warning, uiColor(theme, "Notification.ToolWindow.warningForeground"))
            assertEquals(error, uiColor(theme, "Notification.ToolWindow.errorForeground"))
            assertEquals(success, uiColor(theme, "ProgressBar.passedColor"))
            val palette = theme.required("icons").required("ColorPalette")
            for ((key, expected) in mapOf(
                "Actions.Red" to error,
                "Red.Stroke" to error,
                "Actions.Yellow" to warning,
                "Yellow.Stroke" to warning,
                "Actions.Green" to success,
                "Green.Stroke" to success,
            )) {
                assertEquals(
                    "SVG semantic palette: $key",
                    expected,
                    resolveColor(theme, palette.required(key).asText()),
                )
            }
        }
    }

    @Test
    fun classicAndMidnightRetainTheUpstreamSyntaxMapping() {
        for (variant in variants.take(2)) {
            val attributes = attributes(scheme(theme(variant)))
            for ((key, expected) in mapOf(
                "DEFAULT_FUNCTION_DECLARATION" to "36F9F6",
                "DEFAULT_CLASS_NAME" to "FEDE5D",
                "DEFAULT_LOCAL_VARIABLE" to "FE4450",
                "DEFAULT_KEYWORD" to "FF7EDB",
                "DEFAULT_CONSTANT" to "72F1B8",
                "DEFAULT_NUMBER" to "F97E72",
                "DEFAULT_STRING" to "FF8B39",
            )) {
                assertEquals(expected, attributeValues(attributes, key)["FOREGROUND"])
            }
        }
    }

    @Test
    fun accessibleTextIsReadableOnNormalAndSelectedSurfacesWithoutGlow() {
        val theme = theme(variants.last())
        val surfaces = listOf(
            "Editor",
            "Chrome",
            "Frame",
            "Input",
            "Hover",
            "Pressed",
            "Selection",
            "InactiveSelection",
            "Popup",
            "SearchBackground",
            "ErrorBackground",
            "WarningBackground",
            "SuccessBackground",
            "InfoBackground",
        )
        val foregrounds = flatten(theme.required("ui")).filter { (key, value) ->
            value.isTextual && key.endsWith("foreground", ignoreCase = true)
        }
        for ((key, value) in foregrounds) {
            val foreground = resolveColor(theme, value.asText())
            if (key == "Button.default.foreground") {
                readable(key, foreground, uiColor(theme, "Button.default.startBackground"))
            } else {
                for (surface in surfaces) readable("$key/$surface", foreground, resolveColor(theme, surface))
            }
        }
        val scheme = scheme(theme)
        val attributes = attributes(scheme)
        val selection = color(colors(scheme).getValue("SELECTION_BACKGROUND"))
        val editor = color(attributeValues(attributes, "TEXT").getValue("BACKGROUND"))
        val glowSources = setOf("36F9F6", "FEDE5D", "FE4450", "FF7EDB", "72F1B8", "F97E72", "FF8B39")
        for (key in attributes.keys) {
            val values = attributeValues(attributes, key)
            val foreground = values["FOREGROUND"] ?: continue
            assertFalse(
                "Accessible text must not depend on source glow colors: $key",
                foreground.uppercase() in glowSources,
            )
            readable("$key/editor", color(foreground), editor)
            readable("$key/selected", color(foreground), selection)
            values["BACKGROUND"]?.let { readable("$key/own background", color(foreground), color(it)) }
        }
        for (key in listOf(
            "LINE_NUMBERS_COLOR",
            "LINE_NUMBER_ON_CARET_ROW_COLOR",
            "ANNOTATIONS_COLOR",
            "BLOCK_TERMINAL_DEFAULT_FOREGROUND",
            "BLOCK_TERMINAL_GENERATE_COMMAND_PLACEHOLDER_FOREGROUND",
        )) {
            readable(key, color(colors(scheme).getValue(key)), editor)
            readable("$key/selected", color(colors(scheme).getValue(key)), selection)
        }
        readable("editor selection", color(colors(scheme).getValue("SELECTION_FOREGROUND")), selection)
        val console = color(colors(scheme).getValue("CONSOLE_BACKGROUND_KEY"))
        for (key in attributes.keys.filter { it.startsWith("CONSOLE_") || it.startsWith("BLOCK_TERMINAL_") }) {
            attributeValues(attributes, key)["FOREGROUND"]?.let { readable("$key/console", color(it), console) }
        }
    }

    @Test
    fun contrastUsesLinearSrgbLuminance() {
        assertEquals(21.0, contrast(Color.WHITE, Color.BLACK), 0.00001)
        assertEquals(1.0, contrast(Color.WHITE, Color.WHITE), 0.00001)
        assertEquals(contrast(color("C6BED8"), color("211E30")), contrast(color("211E30"), color("C6BED8")), 0.00001)
    }

    private fun readable(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$label contrast $ratio must be >= 4.5:1", ratio >= 4.5)
    }

    private val nativeLanguageKeys = setOf(
        "LOCAL_VARIABLE_ATTRIBUTES",
        "PARAMETER_ATTRIBUTES",
        "INSTANCE_FIELD_ATTRIBUTES",
        "STATIC_FIELD_ATTRIBUTES",
        "STATIC_FINAL_FIELD_ATTRIBUTES",
        "CLASS_NAME_ATTRIBUTES",
        "INTERFACE_NAME_ATTRIBUTES",
        "METHOD_DECLARATION_ATTRIBUTES",
        "METHOD_CALL_ATTRIBUTES",
        "STATIC_METHOD_ATTRIBUTES",
        "ANNOTATION_NAME_ATTRIBUTES",
        "KDOC_TAG_NAME",
    )
}
