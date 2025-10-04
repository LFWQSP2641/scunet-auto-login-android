package com.lfwqsp2641.safa.domain.coordinator

import android.content.Context
import android.util.Log
import com.lfwqsp2641.safa.data.model.AccountConfig
import com.lfwqsp2641.safa.infrastructure.GoAuthService
import com.lfwqsp2641.safa.infrastructure.WifiConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AuthCoordinator(
    private val context: Context,
    private val wifiManager: WifiConnectionManager,
    private val goAuthService: GoAuthService
) {
    companion object {
        private const val TAG = "AuthCoordinator"
    }

    sealed class AuthState {
        object Idle : AuthState()
        object ConnectingWifi : AuthState()
        object WifiConnected : AuthState()
        object Authenticating : AuthState()
        data class Success(val message: String) : AuthState()
        data class Error(val message: String, val stage: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // 用于实时推送认证过程中的消息
    private val _progressMessages = MutableStateFlow("")
    val progressMessages: StateFlow<String> = _progressMessages.asStateFlow()

    /**
     * 完整的认证流程
     * 1. 连接 WiFi
     * 2. 等待连接成功但无网络
     * 3. 调用 Go 服务进行认证
     */
    suspend fun authenticate(
        accountConfig: AccountConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 清空之前的消息
            _progressMessages.value = ""

            emitMessage("账号名称: ${accountConfig.name}")
            emitMessage("用户名: ${accountConfig.username}")
            emitMessage("服务类型: ${accountConfig.serviceType}")
            emitMessage("连接 SCUNET 中...")

            Log.d(TAG, "开始认证流程")

            _authState.value = AuthState.ConnectingWifi
            val wifiResult = wifiManager.connectToSCUWiFi()
            if (wifiResult.isFailure) {
                val errorMsg = wifiResult.exceptionOrNull()?.message ?: "未知错误"
                Log.e(TAG, "WiFi 连接失败: $errorMsg")
                _authState.value = AuthState.Error("连接 WiFi 失败: $errorMsg", "wifi_connection")
                emitMessage("连接 WiFi 失败: $errorMsg")
                return@withContext Result.failure(Exception("连接 WiFi 失败: $errorMsg"))
            }

            _authState.value = AuthState.WifiConnected
            emitMessage("WiFi 连接成功")
            emitMessage("等待网络稳定...")

            Log.d(TAG, "WiFi 连接成功，绑定的网络: ${wifiManager.boundNetwork}")

            delay(2000) // 等待一段时间让系统处理连接

            emitMessage("开始认证...")
            _authState.value = AuthState.Authenticating
            Log.d(TAG, "开始调用认证服务")

            // 构建额外参数（如果需要）
            val extraParams = """{"service":"${accountConfig.serviceType}"}"""

            // 调用 Go 服务进行认证
            // 由于进程已绑定到 SCUNET WiFi，所有请求都会通过该 WiFi
            Log.d(TAG, "调用 authenticate，参数: username=${accountConfig.username}, service=${accountConfig.serviceType}")
            Log.d(TAG, "当前绑定的网络: ${wifiManager.boundNetwork}")

            val authResult = goAuthService.authenticate(
                username = accountConfig.username,
                password = accountConfig.password,
                extra = extraParams,
                boundNetwork = wifiManager.boundNetwork // 传递绑定的网络用于测试
            )

            if (authResult.isFailure) {
                val errorMsg = authResult.exceptionOrNull()?.message ?: "未知错误"
                Log.e(TAG, "认证失败: $errorMsg")
                _authState.value = AuthState.Error("认证失败: $errorMsg", "authentication")
                emitMessage("认证失败: $errorMsg")
                return@withContext Result.failure(Exception(_progressMessages.value))
            }

            val authMessage = authResult.getOrNull() ?: "认证成功"
            emitMessage(authMessage)
            Log.d(TAG, "认证成功: $authMessage")
            _authState.value = AuthState.Success(authMessage)

            Result.success(_progressMessages.value)
        } catch (e: Exception) {
            Log.e(TAG, "认证过程中发生异常", e)
            _authState.value = AuthState.Error("未知错误: ${e.message}", "unknown")
            emitMessage("未知错误: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 发送进度消息
     */
    private fun emitMessage(message: String) {
        val currentMessages = _progressMessages.value
        _progressMessages.value = if (currentMessages.isEmpty()) {
            message
        } else {
            "$currentMessages\n$message"
        }
    }
}