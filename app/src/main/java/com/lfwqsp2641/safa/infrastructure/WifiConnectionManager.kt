@file:Suppress("DEPRECATION")

package com.lfwqsp2641.safa.infrastructure

import android.content.Context
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.os.Build
import android.util.Log
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WifiConnectionManager(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    companion object {
        private const val TAG = "WifiConnectionManager"
    }

    // 保存当前绑定的网络，供后续使用
    var boundNetwork: Network? = null
        private set

    // 保存当前的网络回调，以便后续注销
    private var currentNetworkCallback: ConnectivityManager.NetworkCallback? = null

    suspend fun connectToSCUWiFi(): Result<Unit> {
        val ssid = "SCUNET"
        return connectToWifi(ssid, "")
    }

    /**
     * 检查默认网络是否为 WiFi
     * 如果移动数据开启且优先级高于 WiFi，返回 false
     */
    fun isDefaultNetworkWifi(): Boolean {
        try {
            // 获取当前默认（活动）网络
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                Log.d(TAG, "没有活动网络")
                return false
            }

            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            Log.d(TAG, "默认网络是否为 WiFi: $isWifi")
            Log.d(TAG, "网络详情: network=$activeNetwork, " +
                    "WIFI=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}, " +
                    "CELLULAR=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")

            return isWifi
        } catch (e: Exception) {
            Log.e(TAG, "检查默认网络失败", e)
            return false
        }
    }

    /**
     * 连接到指定 WiFi
     * 新策略：先检查是否已连接到目标WiFi，如果是则直接绑定
     * 这样可以支持需要Portal认证的WiFi网络
     */
    suspend fun connectToWifi(ssid: String, password: String): Result<Unit> = suspendCoroutine { continuation ->
        try {
            // 首先检查默认网络是否为 WiFi
            if (!isDefaultNetworkWifi()) {
                Log.w(TAG, "默认网络不是 WiFi，可能是移动数据优先")
                val currentSsid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")

                if (currentSsid == ssid) {
                    // 已连接到 SCUNET，但移动数据优先
                    continuation.resume(Result.failure(Exception(
                        "检测到移动数据网络优先\n\n" +
                                "虽然已连接到 $ssid，但系统默认使用移动数据\n" +
                                "这会导致认证请求无法到达校园网服务器\n\n" +
                                "请执行以下操作之一：\n" +
                                "1. 【推荐】临时关闭移动数据\n" +
                                "2. 在系统设置中取消\"自动切换移动数据\"\n" +
                                "3. 长按 SCUNET 网络，设置为\"保持连接\""
                    )))
                } else {
                    // 未连接到目标网络，且移动数据优先
                    continuation.resume(Result.failure(Exception(
                        "默认网络不是 WiFi\n" +
                                "请先连接到 $ssid 并关闭移动数据"
                    )))
                }
                return@suspendCoroutine
            }

            // 首先尝试获取当前连接的 WiFi 信息
            val connectionInfo = wifiManager.connectionInfo
            val currentSsid = connectionInfo?.ssid?.removeSurrounding("\"")

            Log.d(TAG, "当前连接的 SSID: $currentSsid, 目标 SSID: $ssid")
            Log.d(TAG, "网络ID: ${connectionInfo?.networkId}, BSSID: ${connectionInfo?.bssid}")

            if (currentSsid == ssid) {
                // 已经连接到目标 WiFi
                Log.d(TAG, "已连接到目标 WiFi: $ssid")

                // 关键：遍历所有网络，找到 WiFi 网络（即使它没有 Internet 能力）
                val networks = connectivityManager.allNetworks
                var targetNetwork: Network? = null

                Log.d(TAG, "开始遍历所有网络，共 ${networks.size} 个")

                for (network in networks) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val linkProperties = connectivityManager.getLinkProperties(network)

                    Log.d(TAG, "网络: $network")
                    Log.d(TAG, "  - 接口: ${linkProperties?.interfaceName}")
                    Log.d(TAG, "  - 传输类型: WIFI=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}, " +
                            "CELLULAR=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                    Log.d(TAG, "  - 能力: INTERNET=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}, " +
                            "VALIDATED=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")

                    if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                        // 找到 WiFi 网络
                        if (linkProperties?.interfaceName?.startsWith("wlan") == true) {
                            targetNetwork = network
                            Log.d(TAG, "选择此 WiFi 网络作为目标网络")
                            break
                        }
                    }
                }

                if (targetNetwork == null) {
                    Log.w(TAG, "未能通过 allNetworks 找到 WiFi 网络，尝试使用 activeNetwork")
                    // 如果找不到，尝试直接使用当前活动网络
                    val activeNetwork = connectivityManager.activeNetwork
                    val activeCap = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

                    if (activeCap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                        targetNetwork = activeNetwork
                        Log.d(TAG, "activeNetwork 是 WiFi，使用它")
                    }
                }

                if (targetNetwork != null) {
                    Log.d(TAG, "准备绑定到网络: $targetNetwork")

                    // 先解除之前的绑定
                    try {
                        connectivityManager.bindProcessToNetwork(null)
                        Log.d(TAG, "已解除之前的网络绑定")
                    } catch (e: Exception) {
                        Log.w(TAG, "解除绑定失败（可能之前没有绑定）", e)
                    }

                    // 等待一小段时间确保解除生效
                    Thread.sleep(100)

                    // 绑定进程到这个网络
                    // 重要：即使这个网络需要Portal认证（无Internet），也可以绑定
                    // bindProcessToNetwork 不会检查网络是否有Internet访问权限
                    // 这样即使移动数据开启，所有请求也会强制通过这个 WiFi
                    val bindResult = connectivityManager.bindProcessToNetwork(targetNetwork)

                    if (bindResult) {
                        boundNetwork = targetNetwork
                        Log.d(TAG, "✓ 网络绑定成功！")

                        // 验证绑定是否生效
                        val boundNetworkCheck = connectivityManager.boundNetworkForProcess
                        Log.d(TAG, "验证：当前绑定的网络 = $boundNetworkCheck")

                        if (boundNetworkCheck == targetNetwork) {
                            Log.d(TAG, "✓ 绑定验证成功，网络匹配")
                        } else {
                            Log.w(TAG, "⚠ 绑定验证失败，网络不匹配")
                        }

                        continuation.resume(Result.success(Unit))
                    } else {
                        Log.e(TAG, "✗ 网络绑定失败")
                        continuation.resume(Result.failure(Exception("绑定到 $ssid 网络失败")))
                    }
                } else {
                    Log.e(TAG, "✗ 无法找到目标网络对象")
                    continuation.resume(Result.failure(Exception("无法找到 $ssid 对应的网络对象\n请确保：\n1. 已授予位置权限\n2. WiFi 已连接")))
                }
                return@suspendCoroutine
            }

            // 未连接到目标 WiFi
            Log.d(TAG, "未连接到目标 WiFi，当前: $currentSsid, 目标: $ssid")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 无法直接连接，提示用户
                continuation.resume(Result.failure(Exception("未连接到 $ssid\n请在系统设置中连接到该 WiFi 后重试")))
            } else {
                // Android 9 及以下可以尝试使用旧方法连接
                val wifiConfig = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (password.isNotEmpty()) {
                        preSharedKey = "\"$password\""
                    } else {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                }

                val netId = wifiManager.addNetwork(wifiConfig)
                if (netId != -1) {
                    val enableResult = wifiManager.enableNetwork(netId, true)
                    Log.d(TAG, "尝试连接到网络，结果: $enableResult")

                    if (enableResult) {
                        // 等待连接完成
                        waitForConnection(ssid, continuation)
                    } else {
                        continuation.resume(Result.failure(Exception("启用网络失败")))
                    }
                } else {
                    Log.e(TAG, "添加网络配置失败")
                    continuation.resume(Result.failure(Exception("添加网络配置失败")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接WiFi时发生异常", e)
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * 等待连接到指定WiFi（仅用于 Android 9 及以下）
     */
    private fun waitForConnection(ssid: String, continuation: kotlin.coroutines.Continuation<Result<Unit>>) {
        var resumed = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val info = wifiManager.connectionInfo
                val connectedSsid = info?.ssid?.removeSurrounding("\"")

                Log.d(TAG, "网络可用，SSID: $connectedSsid")

                if (connectedSsid == ssid && !resumed) {
                    resumed = true

                    val bindResult = connectivityManager.bindProcessToNetwork(network)
                    if (bindResult) {
                        boundNetwork = network
                    }

                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                        currentNetworkCallback = null
                    } catch (e: Exception) {
                        Log.e(TAG, "注销回调失败", e)
                    }

                    if (bindResult) {
                        continuation.resume(Result.success(Unit))
                    } else {
                        continuation.resume(Result.failure(Exception("绑定网络失败")))
                    }
                }
            }

            override fun onLost(network: Network) {
                if (boundNetwork == network) {
                    boundNetwork = null
                }
            }
        }

        currentNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "注销旧回调失败", e)
            }
        }

        currentNetworkCallback = callback

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // 设置超时
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!resumed) {
                resumed = true
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                    currentNetworkCallback = null
                } catch (e: Exception) {
                    Log.e(TAG, "超时注销回调失败", e)
                }
                continuation.resume(Result.failure(Exception("WiFi 连接超时")))
            }
        }, 30_000)
    }

    /**
     * 解除网络绑定
     */
    fun unbindNetwork() {
        // 注销网络回调
        currentNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "注销回调失败", e)
            }
            currentNetworkCallback = null
        }

        connectivityManager.bindProcessToNetwork(null)
        boundNetwork = null
        Log.d(TAG, "网络绑定已解除")
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

    /**
     * 获取当前连接的 WiFi SSID
     */
    fun getCurrentSsid(): String? {
        return try {
            wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
        } catch (e: Exception) {
            Log.e(TAG, "获取当前SSID失败", e)
            null
        }
    }
}
