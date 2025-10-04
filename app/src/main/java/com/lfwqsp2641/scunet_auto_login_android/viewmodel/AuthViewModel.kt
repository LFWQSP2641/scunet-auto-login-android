package com.lfwqsp2641.scunet_auto_login_android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lfwqsp2641.scunet_auto_login_android.data.repository.AccountRepository
import com.lfwqsp2641.scunet_auto_login_android.data.model.AccountConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the form. Business logic is left empty per request.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AccountRepository.getInstance(application)

    // 所有账号列表
    private val _accounts = MutableStateFlow<List<AccountConfig>>(emptyList())
    val accounts = _accounts.asStateFlow()

    // 当前选中的账号
    private val _selectedAccount = MutableStateFlow<AccountConfig?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    // Available options for the dropdown
    val options = listOf("校园网", "中国电信", "中国移动", "中国联通")

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    init {
        loadAccounts()
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
        val account = AccountConfig(
            name = name,
            username = username,
            password = password,
            serviceType = serviceType
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

        // TODO: implement login logic
        _output.value = """
            账号名称: ${account.name}
            用户名: ${account.username}
            服务类型: ${account.serviceType}
            正在登录...
        """.trimIndent()
    }
}
