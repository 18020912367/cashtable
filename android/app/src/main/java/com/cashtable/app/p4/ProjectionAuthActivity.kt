package com.cashtable.app.p4

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.cashtable.app.MainActivity
import com.cashtable.app.data.CrashLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自动模式授权失效后的「就地重新授权」透明中转页：
 * 悬浮球点击发现投影会话失效（如熄屏被系统回收）时由服务直接拉起本页——页面全透明且
 * 使用独立任务栈，用户视觉上停留在原界面（如支付页），系统授权弹窗直接盖在当前画面上。
 * 同意后重启截屏服务并补拍「当前屏幕」（保住用户原本要记账的画面），再携带截图路径
 * 进入自动记账页识别；拒绝/失败则提示后静默消失，不打断用户。
 *
 * 为什么必须有一个 Activity：MediaProjection 授权结果（resultCode/data）只能通过
 * onActivityResult 回传给 Activity，Service 无法接收；本页作为不可见的结果接收器。
 */
class ProjectionAuthActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            // 必须在页面仍处于前台时启动 FGS（Android 12+ 后台启动前台服务受限）
            ScreenCaptureService.start(this, r.resultCode, data)
            lifecycleScope.launch {
                var ready = false
                repeat(40) {
                    if (ScreenCaptureService.isReady) {
                        ready = true
                        return@repeat
                    }
                    delay(50)
                }
                if (ready) {
                    // 等系统授权弹窗退场动画结束，避免截到弹窗残影
                    delay(400)
                    val path = ScreenCaptureService.captureNow(this@ProjectionAuthActivity)
                    if (path != null) {
                        // 与悬浮球正常自动流程同一交接方式：携带截图路径进入自动记账页
                        startActivity(
                            Intent(this@ProjectionAuthActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(FloatingBallService.EXTRA_AUTO_ACCOUNTING, true)
                                putExtra(FloatingBallService.EXTRA_CAPTURE_PATH, path)
                            }
                        )
                    } else {
                        CrashLog.log(this@ProjectionAuthActivity, "CAPTURE_FAIL", "重新授权成功但补拍当前屏失败")
                        toast("自动截屏失败，请手动截屏后点击悬浮球")
                    }
                } else {
                    CrashLog.log(this@ProjectionAuthActivity, "PROJECTION_EXPIRED", "重新授权后截屏服务未就绪（2s 超时）")
                    toast("截屏服务启动超时，请稍后重试")
                }
                finish()
            }
        } else {
            toast("未授予截屏授权，可手动截屏后点击悬浮球")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 仅首次创建时发起授权（配置变更重建时由 ActivityResultRegistry 自动回传既有结果）
        if (savedInstanceState == null) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
