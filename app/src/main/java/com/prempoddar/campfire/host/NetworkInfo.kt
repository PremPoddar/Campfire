package com.prempoddar.campfire.host

import java.net.Inet4Address
import java.net.NetworkInterface

/** An address other devices can open, and whether it belongs to Wi-Fi or this phone's hotspot. */
data class LanAddress(val ip: String, val kind: Kind) {
    enum class Kind { WIFI, HOTSPOT, OTHER }

    fun url(port: Int) = if (port == 80) "http://$ip" else "http://$ip:$port"
}

object NetworkInfo {
    // Mobile data interfaces: other people can't reach the phone through these.
    private val cellular = listOf("rmnet", "ccmni", "pdp", "v4-rmnet", "dummy", "tun", "ppp", "clat")

    /** LAN addresses, hotspot first (people joining a hotspot can only use that one), then Wi-Fi. */
    fun addresses(): List<LanAddress> {
        val result = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .filterNot { iface -> cellular.any { iface.name.startsWith(it) } }
            .flatMap { iface ->
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress || it.hostAddress?.startsWith("100.") == true }
                    .map { LanAddress(it.hostAddress!!, kindOf(iface.name)) }
            }
        return result.distinctBy { it.ip }.sortedBy {
            when (it.kind) {
                LanAddress.Kind.HOTSPOT -> 0
                LanAddress.Kind.WIFI -> 1
                LanAddress.Kind.OTHER -> 2
            }
        }
    }

    private fun kindOf(name: String): LanAddress.Kind = when {
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") || name.startsWith("wlan1") -> LanAddress.Kind.HOTSPOT
        name.startsWith("wlan") -> LanAddress.Kind.WIFI
        else -> LanAddress.Kind.OTHER
    }
}
