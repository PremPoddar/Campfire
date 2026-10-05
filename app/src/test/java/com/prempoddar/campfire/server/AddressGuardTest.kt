package com.prempoddar.campfire.server

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.net.InetAddress
import org.junit.Test

class AddressGuardTest {
    private fun ok(ip: String) = AddressGuard.isPublic(InetAddress.getByName(ip))

    @Test fun publicAddressesAllowed() {
        assertThat(ok("93.184.215.14")).isTrue()
        assertThat(ok("2606:2800:21f:cb07:6820:80da:af6b:8b2c")).isTrue()
    }

    @Test fun localAndPrivateRangesBlocked() {
        listOf(
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.43.1",
            "169.254.1.1", "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255",
            "::1", "fe80::1", "fd12:3456::1", "::ffff:192.168.1.1",
        ).forEach { assertWithMessage(it).that(ok(it)).isFalse() }
    }

    @Test fun edgesOfPrivateRangesAreRight() {
        assertThat(ok("172.15.255.255")).isTrue()
        assertThat(ok("172.32.0.0")).isTrue()
        assertThat(ok("100.128.0.0")).isTrue()
    }
}
