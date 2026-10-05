package com.prempoddar.campfire.server

import java.net.URI

/** Reads a page's preview: Open Graph tags, then Twitter tags, then plain HTML. */
object OgParser {

    data class Parsed(val title: String?, val description: String?, val image: String?, val site: String?)

    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attr = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val titleTag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val whitespace = Regex("""\s+""")
    private val numericEntity = Regex("""&#(x?)([0-9a-fA-F]+);""")

    fun parse(html: String, base: String): Parsed {
        val meta = HashMap<String, String>()
        metaTag.findAll(html).forEach { tag ->
            val attrs = attr.findAll(tag.value).associate { m ->
                m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] })
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@forEach
            val content = attrs["content"] ?: return@forEach
            meta.putIfAbsent(key, content)
        }
        fun pick(vararg keys: String, max: Int) = keys.firstNotNullOfOrNull { meta[it] }?.let { clean(it, max) }

        val title = pick("og:title", "twitter:title", max = 200)
            ?: titleTag.find(html)?.groupValues?.get(1)?.let { clean(it, 200) }
        val image = pick("og:image", "og:image:url", "twitter:image", "twitter:image:src", max = 2000)
            ?.let { runCatching { URI(base).resolve(it).toString() }.getOrNull() }
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return Parsed(
            title = title,
            description = pick("og:description", "twitter:description", "description", max = 300),
            image = image,
            site = pick("og:site_name", max = 80) ?: runCatching { URI(base).host?.removePrefix("www.") }.getOrNull(),
        )
    }

    private fun clean(raw: String, max: Int): String? {
        val text = decode(raw).replace(whitespace, " ").trim()
        if (text.isEmpty()) return null
        return if (text.length > max) text.take(max).trimEnd() + "…" else text
    }

    private fun decode(s: String): String = numericEntity.replace(s) { m ->
        val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        code?.let { String(Character.toChars(it)) } ?: m.value
    }
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
