package com.cashtable.app

import android.app.Application
import com.cashtable.app.data.CrashLog

/** 全局 Application：安装崩溃日志捕获，并在启动时重试未送达的异常日志。 */
class CashtableApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        CrashLog.flushPending(this)
    }
}
