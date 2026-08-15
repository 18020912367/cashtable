package com.cashtable.app.data

import android.content.Context
import android.content.SharedPreferences

/** 本地偏好：服务器地址 + 当前记账人（SharedPreferences，无登录）。 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("cashtable", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, DEFAULT_SERVER)!!.normalize()
        set(value) {
            sp.edit().putString(KEY_SERVER, value.normalize()).apply()
        }

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
        private const val KEY_SERVER = "server_url"
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_USER_NAME = "current_user_name"
        private const val KEY_DYNAMIC_EXPENSE = "dynamic_cat_expense"
        private const val KEY_DYNAMIC_INCOME = "dynamic_cat_income"
        const val DEFAULT_SERVER = "http://192.168.1.100:8000/"
    }
}
