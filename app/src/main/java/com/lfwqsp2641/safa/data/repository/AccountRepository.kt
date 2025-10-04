package com.lfwqsp2641.safa.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lfwqsp2641.safa.data.model.AccountConfig

/**
 * 账号配置持久化管理器（单例模式）
 */
class AccountRepository private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "account_prefs"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SELECTED_ACCOUNT_ID = "selected_account_id"

        @Volatile
        private var instance: AccountRepository? = null

        fun getInstance(context: Context): AccountRepository {
            return instance ?: synchronized(this) {
                instance ?: AccountRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 保存账号列表
     */
    fun saveAccounts(accounts: List<AccountConfig>) {
        val json = gson.toJson(accounts)
        sharedPreferences.edit {
            putString(KEY_ACCOUNTS, json)
        }
    }

    /**
     * 获取账号列表
     */
    fun getAccounts(): List<AccountConfig> {
        val json = sharedPreferences.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AccountConfig>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 添加账号
     */
    fun addAccount(account: AccountConfig) {
        val accounts = getAccounts().toMutableList()
        accounts.add(account)
        saveAccounts(accounts)
    }

    /**
     * 删除账号
     */
    fun deleteAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.id == accountId }
        saveAccounts(accounts)

        // 如果删除的是当前选中的账号，清除选中状态
        if (getSelectedAccountId() == accountId) {
            clearSelectedAccount()
        }
    }

    /**
     * 更新账号
     */
    fun updateAccount(account: AccountConfig) {
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index != -1) {
            accounts[index] = account
            saveAccounts(accounts)
        }
    }

    /**
     * 根据 ID 获取账号
     */
    fun getAccountById(id: String): AccountConfig? {
        return getAccounts().firstOrNull { it.id == id }
    }

    /**
     * 保存选中的账号 ID
     */
    fun saveSelectedAccountId(accountId: String?) {
        sharedPreferences.edit {
            putString(KEY_SELECTED_ACCOUNT_ID, accountId)
        }
    }

    /**
     * 获取选中的账号 ID
     */
    fun getSelectedAccountId(): String? {
        return sharedPreferences.getString(KEY_SELECTED_ACCOUNT_ID, null)
    }

    /**
     * 获取选中的账号
     */
    fun getSelectedAccount(): AccountConfig? {
        val selectedId = getSelectedAccountId() ?: return null
        return getAccountById(selectedId)
    }

    /**
     * 清除选中的账号
     */
    fun clearSelectedAccount() {
        sharedPreferences.edit {
            remove(KEY_SELECTED_ACCOUNT_ID)
        }
    }

    /**
     * 清除所有数据
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}