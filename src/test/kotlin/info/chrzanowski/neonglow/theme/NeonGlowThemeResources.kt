package info.chrzanowski.neonglow.theme

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal object NeonGlowThemeResources {
    data class Variant(val file: String, val name: String, val id: String)

    val variants = listOf(
        Variant("neon-glow", "Neon Glow", "info.chrzanowski.neonglow.theme.classic"),
        Variant("neon-glow-midnight", "Neon Glow Midnight", "info.chrzanowski.neonglow.theme.midnight"),
        Variant("neon-glow-accessible", "Neon Glow Accessible", "info.chrzanowski.neonglow.theme.accessible"),
    )

    private val mapper = ObjectMapper(JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))

    fun bytes(path: String): ByteArray = checkNotNull(javaClass.getResourceAsStream(path)) {
        "Missing resource: $path"
    }.use { it.readBytes() }

    fun json(path: String): JsonNode = mapper.readTree(bytes(path))

    fun theme(variant: Variant): JsonNode = json("/themes/${variant.file}.theme.json")

    fun xml(path: String): Element = bytes(path).inputStream().use { JDOMUtil.load(it) }

    fun scheme(theme: JsonNode): Element = xml(theme.required("editorScheme").asText())

    fun options(element: Element): Map<String, Element> {
        val children = element.getChildren("option")
        val names = children.map { checkNotNull(it.getAttributeValue("name")) }
        check(names.distinct().size == names.size) { "Duplicate options in ${element.name}: $names" }
        return children.associateBy { it.getAttributeValue("name") }
    }

    fun attributes(scheme: Element): Map<String, Element> = options(checkNotNull(scheme.getChild("attributes")))

    fun colors(scheme: Element): Map<String, String> = options(checkNotNull(scheme.getChild("colors")))
        .mapValues { (_, option) -> checkNotNull(option.getAttributeValue("value")) }

    fun attributeValues(
        attributes: Map<String, Element>,
        name: String,
        visiting: Set<String> = emptySet(),
    ): Map<String, String> {
        check(name !in visiting) { "Cyclic XML fallback: $visiting -> $name" }
        val element = checkNotNull(attributes[name]) { "Missing XML attribute: $name" }
        val base = element.getAttributeValue("baseAttributes")
        if (base != null) return attributeValues(attributes, base, visiting + name)
        return options(checkNotNull(element.getChild("value"))).mapValues { (_, option) ->
            checkNotNull(option.getAttributeValue("value"))
        }
    }

    fun resolveColor(theme: JsonNode, value: String, visiting: Set<String> = emptySet()): Color {
        if (value.startsWith('#')) {
            check(value.matches(Regex("#[0-9A-Fa-f]{6}"))) { "Expected opaque RGB color: $value" }
            return color(value)
        }
        check(value !in visiting) { "Cyclic JSON color alias: $visiting -> $value" }
        val alias = checkNotNull(theme.required("colors").get(value)) { "Unresolved JSON alias: $value" }
        check(alias.isTextual) { "Non-string JSON color alias: $value" }
        return resolveColor(theme, alias.asText(), visiting + value)
    }

    fun uiColor(theme: JsonNode, key: String): Color = resolveColor(theme, theme.required("ui").required(key).asText())

    fun color(hex: String): Color {
        val digits = hex.removePrefix("#")
        check(digits.matches(Regex("[0-9A-Fa-f]{6}"))) { "Invalid RGB color: $hex" }
        return Color(digits.toInt(16))
    }

    fun contrast(foreground: Color, background: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(channel: Int): Double {
                val value = channel / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        }

        val first = luminance(foreground)
        val second = luminance(background)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    fun flatten(node: JsonNode, prefix: String = ""): Map<String, JsonNode> = buildMap {
        node.properties().forEach { (key, value) ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            if (value.isObject) putAll(flatten(value, path)) else put(path, value)
        }
    }
}
