package com.example.videoshield

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Coarse active-link type used only for recovery/adaptation policy. */
enum class NetworkTransportKind {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    OTHER
}

/** Immutable active-network snapshot. networkId changes when Android switches the active link. */
data class NetworkLinkState(
    val online: Boolean,
    val metered: Boolean,
    val transport: NetworkTransportKind,
    val networkId: String
) {
    val hasNetwork: Boolean get() = networkId.isNotBlank()

    companion object {
        val OFFLINE = NetworkLinkState(false, true, NetworkTransportKind.NONE, "")
    }
}

/**
 * Lifecycle-owned connectivity monitor. It reports validated internet plus active-link identity,
 * so playback recovery can distinguish a real Wi-Fi/cellular handoff from a quality-profile-only
 * callback and avoid unnecessary page reloads.
 */
class NetworkStateMonitor(
    context: Context,
    private val onChanged: (NetworkLinkState) -> Unit
) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registered = false
    private var lastState: NetworkLinkState? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
    }

    fun start() {
        if (registered) return
        registered = true
        publish()
        try {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback
            )
        } catch (_: Exception) {
            // The current snapshot is still useful if callback registration is rejected by policy.
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try { connectivity.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }

    fun currentState(): NetworkLinkState {
        val network = connectivity.activeNetwork ?: return NetworkLinkState.OFFLINE
        val caps = connectivity.getNetworkCapabilities(network) ?: return NetworkLinkState.OFFLINE
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransportKind.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportKind.ETHERNET
            else -> NetworkTransportKind.OTHER
        }
        return NetworkLinkState(
            online = online,
            metered = currentMetered(),
            transport = transport,
            networkId = network.toString()
        )
    }

    fun currentOnline(): Boolean = currentState().online

    fun currentMetered(): Boolean = try {
        connectivity.isActiveNetworkMetered
    } catch (_: Exception) {
        true
    }

    private fun publish() {
        val state = currentState()
        if (lastState == state) return
        lastState = state
        onChanged(state)
    }
}
