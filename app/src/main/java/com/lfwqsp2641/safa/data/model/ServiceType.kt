package com.lfwqsp2641.safa.data.model

/**
 * 服务类型枚举
 * 封装了UI显示名称和后端值之间的映射关系
 */
enum class ServiceType(
    val displayName: String,  // UI显示的名称
    val backendValue: String  // 后端使用的值
) {
    CAMPUS_NET("校园网", "EDUNET"),
    CHINA_TELECOM("中国电信", "CHINATELECOM"),
    CHINA_MOBILE("中国移动", "CHINAMOBILE"),
    CHINA_UNICOM("中国联通", "CHINAUNICOM");

    companion object {
        /**
         * 获取所有UI显示名称列表
         */
        fun getDisplayNames(): List<String> {
            return entries.map { it.displayName }
        }

        /**
         * 根据UI显示名称获取后端值
         */
        fun getBackendValueByDisplayName(displayName: String): String? {
            return entries.find { it.displayName == displayName }?.backendValue
        }

        /**
         * 根据后端值获取UI显示名称
         */
        fun getDisplayNameByBackendValue(backendValue: String): String? {
            return entries.find { it.backendValue == backendValue }?.displayName
        }

        /**
         * 根据UI显示名称获取枚举实例
         */
        fun fromDisplayName(displayName: String): ServiceType? {
            return entries.find { it.displayName == displayName }
        }

        /**
         * 根据后端值获取枚举实例
         */
        fun fromBackendValue(backendValue: String): ServiceType? {
            return entries.find { it.backendValue == backendValue }
        }
    }
}

