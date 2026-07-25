package com.example.dynamiclock.locker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/**
 * v5: Checks if the device is connected to a trusted WiFi network.
 * When connected to a trusted SSID, the lock screen can be auto-dismissed.
 */
object WiFiTrustManager {

    /** Check if connected to a trusted WiFi network. */
    fun isOnTrustedNetwork(ctx: Context, trustedSsids: Set<String>): Boolean {
        if (trustedSsids.isEmpty()) return false
        val currentSsid = getCurrentSsid(ctx) ?: return false
        return trustedSsids.any { ssid ->
            currentSsid.equals(ssid, ignoreCase = true) ||
            currentSsid.trim('"').equals(ssid.trim('"'), ignoreCase = true)
        }
    }

    private fun getCurrentSsid(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        // Try getting SSID from WifiManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val connectionInfo = wifi.connectionInfo ?: return null
        val ssid = connectionInfo.ssid ?: return null
        return if (ssid == "<unknown ssid>") null else ssid
    }
}
