package com.prempoddar.campfire.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NamesTest {
    @Test fun cleanTrimsAndCollapsesSpaces() {
        assertThat(Names.clean("  Asha   K  ", 32)).isEqualTo("Asha K")
    }

    @Test fun cleanRejectsBlankAndTooLong() {
        assertThat(Names.clean("   ", 32)).isNull()
        assertThat(Names.clean("x".repeat(33), 32)).isNull()
        assertThat(Names.clean("x".repeat(32), 32)).hasLength(32)
    }

    @Test fun cleanStripsControlCharacters() {
        assertThat(Names.clean("As\u0000ha\n", 32)).isEqualTo("Asha")
    }

    @Test fun dedupeAddsNumbersCaseInsensitively() {
        assertThat(Names.dedupe("Asha", emptySet())).isEqualTo("Asha")
        assertThat(Names.dedupe("Asha", setOf("asha"))).isEqualTo("Asha 2")
        assertThat(Names.dedupe("Asha", setOf("Asha", "Asha 2"))).isEqualTo("Asha 3")
    }
}
