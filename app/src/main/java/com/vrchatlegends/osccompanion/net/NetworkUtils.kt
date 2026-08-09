package com.vrchatlegends.osccompanion.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Quest network helpers.
 *
 * The app runs on the headset itself, so "the Quest IP" is our own Wi-Fi address. We
 * default the OSC target to it rather than to loopback: it is the address the user sees
 * everywhere else (VRChat's OSC debug panel, SideQuest, router UI), it keeps working if a
 * future Horizon OS sandboxes loopback between apps, and the same field then also lets
 * them point at a PC without changing anything else.
 */
object NetworkUtils {

    const val LOOPBACK = "127.0.0.1"

    /**
     * Preferred local IPv4. Uses ConnectivityManager first because it reports the address
     * of the network actually carrying traffic, then falls back to interface enumeration
     * (which also covers Link/USB tethering during adb development).
     */
    fun localIpv4(context: Context?): String? {
        context?.let { ctx ->
            runCatching {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val active = cm?.activeNetwork ?: return@runCatching null
                val props = cm.getLinkProperties(active) ?: return@runCatching null
                props.linkAddresses
                    .firstOrNull { it.isUsableIpv4() }
                    ?.address?.hostAddress
            }.getOrNull()?.let { return it }
        }
        return enumerateIpv4().firstOrNull()
    }

    fun localIpv4OrLoopback(context: Context?): String = localIpv4(context) ?: LOOPBACK

    /** Every non-loopback IPv4 on the device, best candidate first. */
    fun enumerateIpv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { iface ->
                when {
                    iface.name.startsWith("wlan") -> 0
                    iface.name.startsWith("eth") -> 1
                    else -> 2
                }
            }
            .flatMap { iface -> iface.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    }.getOrElse { emptyList() }

    /** Subnet broadcast for the current network, e.g. 192.168.1.255. Useful as a last resort. */
    fun broadcastAddress(context: Context?): String? {
        val ip = localIpv4(context) ?: return null
        val prefix = prefixLengthFor(context, ip) ?: 24
        return runCatching {
            val addr = InetAddress.getByName(ip).address
            val value = addr.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            val hostBits = 32 - prefix
            val broadcast = value or ((1L shl hostBits) - 1)
            (3 downTo 0).joinToString(".") { i -> ((broadcast shr (i * 8)) and 0xFF).toString() }
        }.getOrNull()
    }

    private fun prefixLengthFor(context: Context?, ip: String): Int? {
        val ctx = context ?: return null
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork ?: return@runCatching null
            cm.getLinkProperties(active)?.linkAddresses
                ?.firstOrNull { it.address.hostAddress == ip }
                ?.prefixLength
        }.getOrNull()
    }

    fun isValidHost(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || trimmed.length > 253) return false
        return trimmed.matches(Regex("^[A-Za-z0-9._:-]+$"))
    }

    fun isValidPort(port: Int) = port in 1..65535

    private fun LinkAddress.isUsableIpv4(): Boolean {
        val a = address
        return a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress
    }
}
