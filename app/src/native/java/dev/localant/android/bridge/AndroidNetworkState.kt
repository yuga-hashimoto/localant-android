package dev.localant.android.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.NetworkInterface
import java.util.Collections

@Serializable
internal data class AndroidNetworkAddress(
    val ip: String,
    val prefixLen: Int,
)

@Serializable
internal data class AndroidNetworkInterface(
    val name: String,
    val index: Int,
    val mtu: Int,
    val up: Boolean,
    val broadcast: Boolean,
    val loopback: Boolean,
    val pointToPoint: Boolean,
    val multicast: Boolean,
    val addrs: List<AndroidNetworkAddress>,
)

internal data class AndroidNetworkSnapshot(
    val interfacesJson: String,
    val defaultInterface: String,
    val gateway: String,
)

internal class AndroidNetworkStateProvider(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val json = Json { encodeDefaults = true }

    fun snapshot(): AndroidNetworkSnapshot {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?.let(Collections::list)
            .orEmpty()
            .mapNotNull { networkInterface ->
            runCatching {
                AndroidNetworkInterface(
                    name = networkInterface.name,
                    index = networkInterface.index,
                    mtu = networkInterface.mtu,
                    up = networkInterface.isUp,
                    broadcast = networkInterface.supportsMulticast(),
                    loopback = networkInterface.isLoopback,
                    pointToPoint = networkInterface.isPointToPoint,
                    multicast = networkInterface.supportsMulticast(),
                    addrs = networkInterface.interfaceAddresses.mapNotNull { address ->
                        val hostAddress = address.address?.hostAddress ?: return@mapNotNull null
                        AndroidNetworkAddress(
                            ip = hostAddress,
                            prefixLen = address.networkPrefixLength.toInt(),
                        )
                    },
                )
            }.getOrNull()
        }

        val activeNetwork = connectivityManager?.activeNetwork
        val linkProperties = activeNetwork?.let(connectivityManager::getLinkProperties)
        val defaultRoute = linkProperties?.routes?.firstOrNull { route -> route.isDefaultRoute }

        return AndroidNetworkSnapshot(
            interfacesJson = json.encodeToString(interfaces),
            defaultInterface = linkProperties?.interfaceName.orEmpty(),
            gateway = defaultRoute?.gateway?.hostAddress.orEmpty(),
        )
    }

    fun registerDefaultNetworkCallback(onChanged: () -> Unit): ConnectivityManager.NetworkCallback? {
        val manager = connectivityManager ?: return null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()
            override fun onLost(network: Network) = onChanged()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onChanged()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = onChanged()
        }
        manager.registerDefaultNetworkCallback(callback)
        return callback
    }

    fun unregister(callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}
