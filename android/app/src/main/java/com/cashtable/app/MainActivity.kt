package com.cashtable.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cashtable.app.ui.CashtableApp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 安装完成后自动清理缓存中的更新包（覆盖安装后进程重启时执行）
        File(cacheDir, "update/app-update.apk").delete()
        enableEdgeToEdge()
        setContent {
            CashtableApp()
        }
    }
}
