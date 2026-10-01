package info.chrzanowski.idesynthwave.theme

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class SynthWaveThemeResourceTest {

    private fun resource(path: String) = checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource: $path" }

    @Test
    fun `registered theme has a matching editor scheme`() {
        val plugin = resource("/META-INF/plugin.xml").use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val providers = plugin.getElementsByTagName("themeProvider")
        assertEquals(1, providers.length)
        val provider = providers.item(0) as Element
        assertEquals("info.chrzanowski.idesynthwave.theme.classic", provider.getAttribute("id"))
        val theme = resource(provider.getAttribute("path")).use { ObjectMapper().readTree(it) }
        assertEquals("SynthWave '84", theme.required("name").asText())
        assertTrue(theme.required("dark").asBoolean())
        val scheme = resource(theme.required("editorScheme").asText()).use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement
        }
        assertEquals("scheme", scheme.tagName)
        assertEquals(theme.required("name").asText(), scheme.getAttribute("name"))
        assertEquals("Darcula", scheme.getAttribute("parent_scheme"))
    }

    @Test
    fun `ui colours resolve to the palette`() {
        val theme = resource("/themes/synthwave.theme.json").use { ObjectMapper().readTree(it) }
        val colors = theme.required("colors")
        colors.elements().forEachRemaining { assertTrue(it.asText().matches(Regex("#[0-9A-Fa-f]{6}"))) }
        theme.required("ui").elements().forEachRemaining {
            if (it.isTextual) assertTrue("Unknown palette colour: $it", colors.has(it.asText()))
        }
        assertEquals("#262335", colors.required("Editor").asText())
        assertEquals("#FF7EDB", colors.required("Accent").asText())
    }

    @Test
    fun `upstream licence is included in the plugin resources`() {
        val license = resource("/META-INF/LICENSE.synthwave84").bufferedReader().use { it.readText() }
        assertTrue(license.contains("Copyright (c) 2019 Robb Owen"))
        assertTrue(license.contains("MIT License"))
    }
}