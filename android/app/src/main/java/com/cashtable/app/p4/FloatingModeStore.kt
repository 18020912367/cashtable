package com.cashtable.app.p4

import android.content.Context

/** 悬浮球记账模式（SharedPreferences 持久化）：手动 / 自动。 */
object FloatingModeStore {
    private const val PREF = "floating_ball"
    private const val KEY_MODE = "mode"

    /** 手动：先使用系统截屏，点击悬浮球后自动读取相册最新截图。 */
    const val MANUAL = "manual"

    /** 自动：MediaProjection 一次性授权后，点击悬浮球立即自动截取当前屏幕。 */
    const val AUTO = "auto"

    fun get(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MANUAL) ?: MANUAL

    fun set(context: Context, mode: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode).apply()
    }
}
