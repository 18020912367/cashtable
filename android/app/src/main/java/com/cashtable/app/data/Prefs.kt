package com.cashtable.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** 运行模式。 */
enum class AppMode(val key: String, val label: String) {
    Local("local", "本地模式"),
    Custom("custom", "自定义模式"),
    Full("full", "全功能模式");

    companion object {
        fun from(key: String): AppMode =
            entries.firstOrNull { it.key == key } ?: Local
    }
}

/**
 * 本地偏好：运行模式 / 服务器地址 / 记账人 / 激活码 / LLM Key。
 *
 * - 普通字段走 SharedPreferences；
 * - LLM Key 走 EncryptedSharedPreferences（Android Keystore 加密），
 *   对外只显示头尾、不可复制。
 */
class Prefs(context: Context) {

    private val appContext = context.applicationContext

    private val sp: SharedPreferences =
        appContext.getSharedPreferences("cashtable", Context.MODE_PRIVATE)

    // 加密偏好：存放 LLM Key（security-crypto 1.0.0 旧 API：MasterKeys + 主密钥别名）
    private val masterKeyAlias: String =
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val secureSp: SharedPreferences = EncryptedSharedPreferences.create(
        "cashtable_secure",
        masterKeyAlias,
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ---------- 运行模式 ----------

    var mode: AppMode
        get() = AppMode.from(sp.getString(KEY_MODE, AppMode.Local.key)!!)
        set(value) {
            sp.edit().putString(KEY_MODE, value.key).apply()
        }

    /** 当前模式生效的服务器地址（自定义=自定义地址；全功能=公共服务器；本地=默认占位）。 */
    fun activeServerUrl(): String = when (mode) {
        AppMode.Local -> DEFAULT_SERVER
        AppMode.Custom -> customServerUrl
        AppMode.Full -> PUBLIC_SERVER
    }

    /** 当前模式生效的服务器地址（Repository/ViewModel 依赖的属性名）。 */
    val serverUrl: String get() = activeServerUrl()

    // ---------- 服务器地址 ----------

    /** 自定义模式服务器地址（默认继承历史"服务器地址"配置）。 */
    var customServerUrl: String
        get() = sp.getString(KEY_CUSTOM_SERVER, DEFAULT_SERVER)!!.normalize()
        set(value) {
            sp.edit().putString(KEY_CUSTOM_SERVER, value.normalize()).apply()
        }

    /** 自定义模式：服务器上 sqlite 文件的目录地址（信息性，供用户部署参考）。 */
    var customDbPath: String
        get() = sp.getString(KEY_CUSTOM_DB_PATH, "")!!
        set(value) {
            sp.edit().putString(KEY_CUSTOM_DB_PATH, value.trim()).apply()
        }

    // ---------- 全功能模式激活 ----------

    var activationCode: String
        get() = sp.getString(KEY_ACT_CODE, "")!!
        set(value) {
            sp.edit().putString(KEY_ACT_CODE, value.trim()).apply()
        }

    /** 设备标识：ANDROID_ID（Android 10+ 无法可靠获取真实 MAC，用系统唯一 ID 代替）。 */
    var deviceMac: String
        get() = sp.getString(KEY_DEVICE_MAC, "")!!
        set(value) {
            sp.edit().putString(KEY_DEVICE_MAC, value).apply()
        }

    // ---------- 记账人 ----------

    var currentUserId: Int?
        get() = if (sp.contains(KEY_USER_ID)) sp.getInt(KEY_USER_ID, -1) else null
        set(value) {
            sp.edit().putInt(KEY_USER_ID, value ?: -1).apply()
        }

    var currentUserName: String
        get() = sp.getString(KEY_USER_NAME, "")!!
        set(value) {
            sp.edit().putString(KEY_USER_NAME, value).apply()
        }

    // ---------- LLM（个人中心维护，加密存储） ----------

    /** LLM API Key（加密存储；空=未配置）。 */
    var llmApiKey: String
        get() = secureSp.getString(KEY_LLM_API_KEY, "")!!
        set(value) {
            secureSp.edit().putString(KEY_LLM_API_KEY, value.trim()).apply()
        }

    /** LLM Base URL（默认智谱，OpenAI 兼容）。 */
    var llmBaseUrl: String
        get() = sp.getString(KEY_LLM_BASE_URL, DEFAULT_LLM_BASE_URL)!!
        set(value) {
            sp.edit().putString(KEY_LLM_BASE_URL, value.trim().trimEnd('/')).apply()
        }

    /** LLM 模型名。 */
    var llmModel: String
        get() = sp.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL)!!
        set(value) {
            sp.edit().putString(KEY_LLM_MODEL, value.trim()).apply()
        }

    /** 动态分类（从"更多"加载的快捷分类），按 kind 分开存储。 */
    fun getDynamicCategoryId(kind: String): Int? {
        val key = if (kind == "income") KEY_DYNAMIC_INCOME else KEY_DYNAMIC_EXPENSE
        return if (sp.contains(key)) sp.getInt(key, -1) else null
    }

    fun setDynamicCategoryId(kind: String, id: Int?) {
        val key = if (kind == "income") KEY_DYNAMIC_INCOME else KEY_DYNAMIC_EXPENSE
        sp.edit().putInt(key, id ?: -1).apply()
    }

    private fun String.normalize(): String {
        val t = trim()
        // 无协议时补 http://
        val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_CUSTOM_SERVER = "custom_server_url"
        private const val KEY_CUSTOM_DB_PATH = "custom_db_path"
        private const val KEY_ACT_CODE = "activation_code"
        private const val KEY_DEVICE_MAC = "device_mac"
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_USER_NAME = "current_user_name"
        private const val KEY_DYNAMIC_EXPENSE = "dynamic_cat_expense"
        private const val KEY_DYNAMIC_INCOME = "dynamic_cat_income"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_BASE_URL = "llm_base_url"
        private const val KEY_LLM_MODEL = "llm_model"

        const val DEFAULT_SERVER = "http://192.168.1.100:8000/"
        const val PUBLIC_SERVER = "http://124.223.74.139:8000/"
        const val DEFAULT_LLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        const val DEFAULT_LLM_MODEL = "glm-4v-flash"

        /** 展示用：LLM Key 只显示头尾（如 sk-ab...xyz），不可复制。 */
        fun maskKey(key: String): String {
            if (key.isEmpty()) return "未配置"
            if (key.length <= 8) return "***"
            return key.take(4) + "..." + key.takeLast(4)
        }
    }
}
