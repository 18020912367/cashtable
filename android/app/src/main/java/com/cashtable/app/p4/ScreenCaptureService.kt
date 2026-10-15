package com.cashtable.app.p4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cashtable.app.data.CrashLog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 悬浮球记账·自动模式截屏服务：
 * 开启悬浮球选择「自动模式」时经用户一次性授权（MediaProjection），本服务常驻持有
 * VirtualDisplay；点击悬浮球时立即截取当前屏幕——此刻用户仍停留在原页面（如支付页），
 * 截图保存到应用私有目录后跳转「自动记账」页识别，后续识别/保存流程与手动模式一致。
 */
class ScreenCaptureService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14+（targetSdk 34+）要求：mediaProjection 类型前台服务必须
        // 先以 FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 调用 startForeground，再 getMediaProjection。
        // 兜底：manifest 权限缺失/投影授权已失效时系统抛 SecurityException，捕获避免主进程闪退。
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFY_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else 0,
            )
        } catch (e: Exception) {
            CrashLog.log(this, "SYSTEM_INCOMPATIBLE", "截屏前台服务启动被拒: ${e.stackTraceToString()}")
            toast("功能和当前系统不兼容，服务崩溃，请联系管理员处理")
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.parcelableIntentExtra(EXTRA_RESULT_DATA)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startCapture(resultCode, resultData)
        } catch (e: Exception) {
            releaseLocked()
            // MediaProjection 授权后初始化仍失败（授权未正常生效/系统限制）：记录日志
            CrashLog.log(this, "PROJECTION_FAIL", "屏幕截取初始化失败: ${e.stackTraceToString()}")
            toast("屏幕截取初始化失败：${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocked()
        super.onDestroy()
    }

    // ---------- 授权 → 常驻 VirtualDisplay ----------

    private fun startCapture(resultCode: Int, resultData: Intent) {
        releaseLocked()
        val mp = getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(resultCode, resultData) ?: throw IllegalStateException("未获得截屏授权")
        synchronized(LOCK) {
            projection = mp
            // 用户从系统状态栏/快捷开关停止投屏 → 释放并标记失效
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseLocked()
                    stopSelf()
                }
            }, mainHandler)
            val (w, h, dpi) = realMetrics()
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mp.createVirtualDisplay(
                "cashtable-capture", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, mainHandler,
            )
            isReady = true
        }
    }

    private fun releaseLocked() {
        synchronized(LOCK) {
            isReady = false
            try { virtualDisplay?.release() } catch (_: Exception) {}
            virtualDisplay = null
            try { reader?.close() } catch (_: Exception) {}
            reader = null
            try { projection?.stop() } catch (_: Exception) {}
            projection = null
        }
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), resources.displayMetrics.densityDpi)
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "自动截屏记账", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动截屏已就绪")
            .setContentText("点击悬浮球自动截取当前屏幕记账")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun Intent.parcelableIntentExtra(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") getParcelableExtra(key)
        }

    companion object {
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFY_ID = 1002
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val ACTION_STOP = "com.cashtable.app.p4.CAPTURE_STOP"

        private val LOCK = Any()

        /** 截屏会话是否就绪（授权成功且 VirtualDisplay 存活）。 */
        @Volatile
        var isReady = false
            private set

        private var projection: MediaProjection? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var reader: ImageReader? = null

        /** 携带授权结果启动截屏前台服务（须在授权回调内尽快调用）。 */
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
            )
        }

        /**
         * 立即截取当前屏幕，保存 JPEG 到应用私有目录并返回绝对路径；失败返回 null。
         * 在悬浮球点击时调用：此刻用户仍在原页面，截到的正是待记账画面。
         */
        fun captureNow(context: Context): String? {
            val r = synchronized(LOCK) { if (isReady) reader else null } ?: return null
            var image: Image? = null
            return try {
                // 常驻 VirtualDisplay 已有画面；个别机型首帧未合成时短暂等待重试
                repeat(5) {
                    if (image == null) {
                        image = r.acquireLatestImage()
                        if (image == null) Thread.sleep(40)
                    }
                }
                val img = image ?: return null
                val bitmap = imageToBitmap(img) ?: return null
                val dir = File(context.filesDir, "captures").apply { mkdirs() }
                val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                // 只保留最新一张，清理历史残留
                dir.listFiles()?.forEach { if (it.absolutePath != file.absolutePath) it.delete() }
                file.absolutePath
            } catch (_: Exception) {
                null
            } finally {
                try { image?.close() } catch (_: Exception) {}
            }
        }

        /** Image(RGBA_8888) → Bitmap(ARGB_8888)，处理行尾 padding；失败返回 null。 */
        private fun imageToBitmap(image: Image): Bitmap? = try {
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val buffer = plane.buffer
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (rowStride == width * pixelStride) {
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                val rowBytes = width * pixelStride
                val tmp = ByteBuffer.allocate(rowBytes * height).order(ByteOrder.nativeOrder())
                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    val line = buffer.slice()
                    line.limit(rowBytes)
                    tmp.put(line)
                }
                tmp.position(0)
                bitmap.copyPixelsFromBuffer(tmp)
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
