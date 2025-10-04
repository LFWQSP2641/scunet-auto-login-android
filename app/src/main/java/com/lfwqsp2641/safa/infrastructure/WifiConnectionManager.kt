@file:Suppress("DEPRECATION")

package com.lfwqsp2641.safa.infrastructure

import android.content.Context
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WifiConnectionManager(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun connectToSCUWiFi(): Result<Unit> {
        val ssid = "SCUNET"
        return connectToWifi(ssid, "")
    }

    /**
     * 连接到指定 WiFi
     * Android 10+ 需要使用 NetworkSpecifier
     */
    suspend fun connectToWifi(ssid: String, password: String): Result<Unit> = suspendCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 NetworkSpecifier
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    continuation.resume(Result.success(Unit))
                }

                override fun onUnavailable() {
                    continuation.resume(Result.failure(Exception("WiFi 连接超时")))
                }
            }

            connectivityManager.requestNetwork(request, callback, 30_000)
        } else {
            // Android 9 及以下的旧方法
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }
            val netId = wifiManager.addNetwork(wifiConfig)
            wifiManager.enableNetwork(netId, true)
            continuation.resume(Result.success(Unit))
        }
    }

    /**
     * 检查当前网络状态
     */
    fun isConnectedButNoInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
