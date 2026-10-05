package com.prempoddar.campfire.core

/** Cleaning and de-duplicating display names and room names. */
object Names {
    private val whitespace = Regex("""\s+""")

    /** Trimmed, single-spaced, control characters removed; null if blank or longer than [max]. */
    fun clean(raw: String, max: Int): String? {
        val text = raw.filterNot { it.isISOControl() && it != ' ' && it != '\n' && it != '\t' }
            .replace(whitespace, " ")
            .trim()
        return text.takeIf { it.isNotEmpty() && it.length <= max }
    }

    /** [name] if free, else "name 2", "name 3", … Comparison ignores case. */
    fun dedupe(name: String, taken: Set<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        var n = 2
        while ("$name $n".lowercase() in lower) n++
        return "$name $n"
    }
}
