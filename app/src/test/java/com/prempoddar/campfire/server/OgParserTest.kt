package com.prempoddar.campfire.server

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OgParserTest {
    @Test fun readsOpenGraphTags() {
        val html = """
            <html><head>
            <meta property="og:title" content="Trail &amp; Camp">
            <meta content="A guide to fires." property="og:description" />
            <meta property="og:image" content="/img/fire.jpg">
            <meta property="og:site_name" content="Outdoors">
            <title>Ignored</title>
            </head></html>
        """.trimIndent()
        val p = OgParser.parse(html, "https://example.com/blog/post")
        assertThat(p.title).isEqualTo("Trail & Camp")
        assertThat(p.description).isEqualTo("A guide to fires.")
        assertThat(p.image).isEqualTo("https://example.com/img/fire.jpg")
        assertThat(p.site).isEqualTo("Outdoors")
    }

    @Test fun fallsBackToTitleTagTwitterAndHost() {
        val html = """<head><TITLE> Hello  there </TITLE><meta name="twitter:image" content="https://cdn.x/i.png"><meta name="description" content="desc"></head>"""
        val p = OgParser.parse(html, "https://www.example.org/x")
        assertThat(p.title).isEqualTo("Hello there")
        assertThat(p.description).isEqualTo("desc")
        assertThat(p.image).isEqualTo("https://cdn.x/i.png")
        assertThat(p.site).isEqualTo("example.org")
    }

    @Test fun emptyPageGivesOnlySite() {
        val p = OgParser.parse("<html></html>", "https://example.org")
        assertThat(p.title).isNull()
        assertThat(p.image).isNull()
        assertThat(p.site).isEqualTo("example.org")
    }

    @Test fun longValuesAreTrimmed() {
        val p = OgParser.parse("""<meta property="og:description" content="${"a".repeat(1000)}">""", "https://e.com")
        assertThat(p.description!!.length).isAtMost(301)
    }
}
