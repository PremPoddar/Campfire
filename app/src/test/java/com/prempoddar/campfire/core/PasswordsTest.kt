package com.prempoddar.campfire.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordsTest {
    @Test fun hashVerifies() {
        val h = Passwords.hash("marshmallow")
        assertThat(h).doesNotContain("marshmallow")
        assertThat(Passwords.verify("marshmallow", h)).isTrue()
        assertThat(Passwords.verify("Marshmallow", h)).isFalse()
    }

    @Test fun saltsDiffer() {
        assertThat(Passwords.hash("a")).isNotEqualTo(Passwords.hash("a"))
    }

    @Test fun malformedStoredValueNeverVerifies() {
        assertThat(Passwords.verify("a", "nonsense")).isFalse()
    }
}
