package com.lfwqsp2641.safa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.lfwqsp2641.safa.domain.coordinator.AuthCoordinator
import com.lfwqsp2641.safa.infrastructure.GoAuthService
import com.lfwqsp2641.safa.infrastructure.WifiConnectionManager
import com.lfwqsp2641.safa.ui.theme.SafaTheme
import com.lfwqsp2641.safa.view.AuthView
import com.lfwqsp2641.safa.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val wifiManager = WifiConnectionManager(applicationContext)
                val goAuthService = GoAuthService()
                val authCoordinator = AuthCoordinator(applicationContext, wifiManager, goAuthService)
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(application, authCoordinator) as T
            }
        }
    }

    // 权限请求启动器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // 可以提示用户权限未授予，但不阻止应用运行
            android.widget.Toast.makeText(
                this,
                "部分权限未授予，WiFi 连接功能可能无法正常使用",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 申请必要的权限
        requestNecessaryPermissions()

        enableEdgeToEdge()
        setContent {
            SafaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Show the form screen created below
                    AuthView(
                        viewModel = authViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    /**
     * 申请必要的运行时权限
     */
    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 位置权限（WiFi 扫描和连接需要）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Android 13+ 需要 NEARBY_WIFI_DEVICES 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        // 如果有需要申请的权限，则申请
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
