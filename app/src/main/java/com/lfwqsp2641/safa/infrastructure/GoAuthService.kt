package com.lfwqsp2641.safa.infrastructure

import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import litelib.Litelib

class GoAuthService {
    companion object {
        private const val TAG = "GoAuthService"
    }

    /**
     * 调用 Go 服务进行认证
     * @param username 用户名
     * @param password 密码
     * @param extra 额外参数（JSON 格式）
     * @param boundNetwork 绑定的网络（用于日志）
     */
    suspend fun authenticate(
        username: String,
        password: String,
        extra: String = "{}",
        boundNetwork: Network? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "调用 Go 认证服务")
            Log.d(TAG, "  - username: $username")
            Log.d(TAG, "  - boundNetwork: $boundNetwork")
            Log.d(TAG, "注意：Go 代码可能不会遵循 Android 的网络绑定")
            Log.d(TAG, "如果认证失败，请确保已关闭移动数据")

            val result = Litelib.login(username, password, extra)
            Log.d(TAG, "Go 认证返回结果: $result")

            if (result.contains("成功")) {
                Result.success(result)
            } else {
                Result.failure(Exception(result))
            }
        } catch (e: Exception) {
            Log.e(TAG, "认证异常", e)
            Result.failure(Exception("认证异常: ${e.message}"))
        }
    }
}