package com.lfwqsp2641.scunet_auto_login_android.data.model

import java.util.UUID

data class AccountConfig(
    val id: String = UUID.randomUUID().toString(), // 唯一标识
    val name: String,                              // 账号显示名称
    val username: String,                          // 用户名
    val password: String,                          // 密码
    val serviceType: String
)