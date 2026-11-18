package com.example.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.NetworkTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address

class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _telemetry = MutableStateFlow(NetworkTelemetry())
    val telemetry: StateFlow<NetworkTelemetry> = _telemetry.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshSnapshot()
        }

        override fun onLost(network: Network) {
            refreshSnapshot()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            refreshSnapshot(capabilities = capabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            refreshSnapshot(linkProperties = linkProperties)
        }
    }

    init {
        refreshSnapshot()
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Best-effort registration
        }
    }

    fun refreshSnapshot(
        capabilities: NetworkCapabilities? = null,
        linkProperties: LinkProperties? = null
    ) {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = capabilities ?: activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val props = linkProperties ?: activeNetwork?.let { connectivityManager.getLinkProperties(it) }

        if (caps == null) {
            _telemetry.update { current ->
                current.copy(
                    isConnected = false,
                    transportLabel = "Offline",
                    isWifi = false,
                    signalDbm = -127,
                    signalBars = 0,
                    linkDownMbps = 0,
                    linkUpMbps = 0,
                    localIpAddress = "0.0.0.0"
                )
            }
            return
        }

        val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        val transportLabel = buildString {
            when {
                hasWifi -> append("Wi-Fi")
                hasEthernet -> append("Ethernet")
                hasCellular -> append("Cellular")
                else -> append("Network")
            }
            if (hasVpn) append(" + VPN")
        }

        val downMbps = (caps.linkDownstreamBandwidthKbps / 1000).coerceAtLeast(0)
        val upMbps = (caps.linkUpstreamBandwidthKbps / 1000).coerceAtLeast(0)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val rssi = extractRssi(caps, hasWifi)
        val bars = if (rssi <= -120) 0 else WifiManager.calculateSignalLevel(rssi, 5)

        val ipv4 = props?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address?.hostAddress
            ?: "127.0.0.1"

        val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            props?.isPrivateDnsActive == true
        } else {
            false
        }
        val privateDnsName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            props?.privateDnsServerName ?: if (privateDnsActive) "Private DNS (DoT)" else "System DNS"
        } else {
            "System DNS"
        }

        val ssidLabel = if (hasWifi) {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager?.connectionInfo
            }
            val rawSsid = info?.ssid?.trim('"')
            if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
                "Wi-Fi Connected"
            } else {
                rawSsid
            }
        } else {
            transportLabel
        }

        _telemetry.update { current ->
            current.copy(
                isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                transportLabel = transportLabel,
                isWifi = hasWifi,
                isMetered = isMetered,
                wifiSsid = ssidLabel,
                signalDbm = rssi,
                signalBars = bars,
                linkDownMbps = downMbps,
                linkUpMbps = upMbps,
                localIpAddress = ipv4,
                privateDnsActive = privateDnsActive,
                privateDnsServer = privateDnsName
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun extractRssi(caps: NetworkCapabilities, hasWifi: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val strength = caps.signalStrength
            if (strength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                return strength
            }
            val wifiInfo = caps.transportInfo as? WifiInfo
            if (wifiInfo != null && wifiInfo.rssi > -127) {
                return wifiInfo.rssi
            }
        }
        if (hasWifi) {
            val info = wifiManager?.connectionInfo
            if (info != null && info.rssi > -127) {
                return info.rssi
            }
        }
        return -55
    }

    fun recordTrackerBlocked() {
        _telemetry.update { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1L) }
    }

    fun recordHttpsUpgrade() {
        _telemetry.update { it.copy(httpsUpgradesCount = it.httpsUpgradesCount + 1L) }
    }

    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}
