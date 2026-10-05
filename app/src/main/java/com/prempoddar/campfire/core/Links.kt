package com.prempoddar.campfire.core

object Links {
    private val url = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** The first http(s) link in [text], without trailing punctuation. */
    fun firstUrl(text: String): String? =
        url.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
}
