package com.example.model

data class NetworkTelemetry(
    val isConnected: Boolean = false,
    val transportLabel: String = "Offline",
    val isWifi: Boolean = false,
    val isMetered: Boolean = false,
    val wifiSsid: String = "Unavailable",
    val signalDbm: Int = -127,
    val signalBars: Int = 0,
    val linkDownMbps: Int = 0,
    val linkUpMbps: Int = 0,
    val localIpAddress: String = "0.0.0.0",
    val privateDnsActive: Boolean = false,
    val privateDnsServer: String = "System DNS",
    val blockedTrackersCount: Long = 0L,
    val httpsUpgradesCount: Long = 0L
)
