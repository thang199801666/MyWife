package com.example.videoshield

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Small lifecycle-owned connectivity monitor. It deliberately reports internet
 * capability rather than Wi-Fi/cellular type so playback recovery can suspend
 * itself cleanly while the device is offline.
 */
class NetworkStateMonitor(
    context: Context,
    private val onChanged: (online: Boolean) -> Unit
) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registered = false
    private var lastOnline: Boolean? = null
    private var lastMetered: Boolean? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish(currentOnline())
        override fun onLost(network: Network) = publish(currentOnline())
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish(currentOnline())
    }

    fun start() {
        if (registered) return
        registered = true
        publish(currentOnline())
        try {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback
            )
        } catch (_: Exception) {
            // The initial state is still useful even if callback registration is
            // rejected by an unusual device policy.
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try { connectivity.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }

    fun currentOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun currentMetered(): Boolean = try {
        connectivity.isActiveNetworkMetered
    } catch (_: Exception) {
        true
    }

    private fun publish(value: Boolean) {
        val metered = currentMetered()
        if (lastOnline == value && lastMetered == metered) return
        lastOnline = value
        lastMetered = metered
        onChanged(value)
    }
}
