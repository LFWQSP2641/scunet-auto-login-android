package com.lfwqsp2641.safa.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lfwqsp2641.safa.data.repository.AccountRepository
import com.lfwqsp2641.safa.data.model.AccountConfig
import com.lfwqsp2641.safa.data.model.ServiceType
import com.lfwqsp2641.safa.domain.coordinator.AuthCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the form. Business logic is left empty per request.
 */
class AuthViewModel(
    application: Application,
    private val authCoordinator: AuthCoordinator
) : AndroidViewModel(application) {

    private val repository = AccountRepository.getInstance(application)

    // 所有账号列表
    private val _accounts = MutableStateFlow<List<AccountConfig>>(emptyList())
    val accounts = _accounts.asStateFlow()

    // 当前选中的账号
    private val _selectedAccount = MutableStateFlow<AccountConfig?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    // 使用ServiceType枚举获取UI显示选项
    val options = ServiceType.getDisplayNames()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    init {
        loadAccounts()

        // 订阅认证进度消息，实时更新输出
        viewModelScope.launch {
            authCoordinator.progressMessages.collect { message ->
                _output.value = message
            }
        }
    }

    /**
     * 从持久化存储加载账号列表
     */
    private fun loadAccounts() {
        _accounts.value = repository.getAccounts()
        _selectedAccount.value = repository.getSelectedAccount()
    }

    /**
     * 添加账号
     */
    fun addAccount(name: String, username: String, password: String, serviceType: String) {
        // 将UI显示名称转换为后端值
        val backendValue = ServiceType.getBackendValueByDisplayName(serviceType) ?: serviceType

        val account = AccountConfig(
            name = name,
            username = username,
            password = password,
            serviceType = backendValue
        )
        repository.addAccount(account)
        loadAccounts()

        // 如果是第一个账号，自动选中
        if (_accounts.value.size == 1) {
            selectAccount(account)
        }
    }

    /**
     * 删除当前选中的账号
     */
    fun deleteSelectedAccount() {
        val account = _selectedAccount.value ?: return
        repository.deleteAccount(account.id)
        loadAccounts()
    }

    /**
     * 选择账号
     */
    fun selectAccount(account: AccountConfig) {
        _selectedAccount.value = account
        repository.saveSelectedAccountId(account.id)
    }

    fun onButtonClicked() {
        val account = _selectedAccount.value
        if (account == null) {
            _output.value = "请先选择一个账号"
            return
        }

        _output.value = ""

        // 在协程作用域中调用挂起函数
        viewModelScope.launch {
            val result = authCoordinator.authenticate(account)
            result.onSuccess { message ->
                _output.value = "登录成功: $message\n" +
                                "认证流程结束\n" +
                                "关闭了移动数据记得打开哦\n" +
                                "如果 WiFi 还是感叹号可以尝试手动关闭打开 WiFi\n"
            }.onFailure { error ->
                _output.value = "登录失败: ${error.message}\n"
            }
        }
    }
}
