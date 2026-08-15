package com.cashtable.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.cashtable.app.p4.FloatingBallService
import com.cashtable.app.ui.CashtableApp
import java.io.File

class MainActivity : ComponentActivity() {

    /**
     * 悬浮球点击次数：每次点击自增，App 据此跳转「自动记账」页。
     * 不归零（递增即可），保证重复点击时进入的是全新页面。
     */
    private var autoAccountingSeq by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 安装完成后自动清理缓存中的更新包（覆盖安装后进程重启时执行）
        File(cacheDir, "update/app-update.apk").delete()
        if (intent.getBooleanExtra(FloatingBallService.EXTRA_AUTO_ACCOUNTING, false)) {
            autoAccountingSeq++
        }
        enableEdgeToEdge()
        setContent {
            CashtableApp(autoAccountingSeq = autoAccountingSeq)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(FloatingBallService.EXTRA_AUTO_ACCOUNTING, false)) {
            autoAccountingSeq++
        }
    }
}
