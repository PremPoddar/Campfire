package com.prempoddar.campfire.server

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Link previews make the host phone fetch URLs that chat members choose. Only public internet
 * addresses are allowed, so nobody can use Campfire to reach the router, other devices on the
 * network or the phone itself.
 */
object AddressGuard {

    fun isPublic(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress
        ) {
            return false
        }
        val b = addr.address.map { it.toInt() and 0xFF }
        return when (addr) {
            is Inet4Address -> !(
                b[0] == 0 ||
                    (b[0] == 100 && b[1] in 64..127) || // carrier-grade NAT, also used by hotspots
                    (b[0] == 192 && b[1] == 0 && b[2] == 0) ||
                    (b[0] == 198 && b[1] in 18..19) ||
                    b[0] >= 240
                )
            is Inet6Address -> (b[0] and 0xFE) != 0xFC // unique local fc00::/7
            else -> false
        }
    }
}
