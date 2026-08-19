package com.cashtable.app.p4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cashtable.app.MainActivity
import kotlin.math.abs

/**
 * 悬浮球记账入口前台服务：
 * 常驻悬浮球（可拖动）。点击不再截屏，而是跳转 App「自动记账」页——
 * 由用户手动使用系统截屏（完全合规），自动记账页自动读取相册最新截图并识别。
 * 已按技术路线变更移除全部 MediaProjection 截屏代码（0.7.0）。
 */
class FloatingBallService : Service() {

    private var windowManager: WindowManager? = null
    private var ballView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFY_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0,
        )
        isActive = true
        showFloatingBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isActive = false
        ballView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        ballView = null
        super.onDestroy()
    }

    // ---------- 悬浮球 ----------

    private fun showFloatingBall() {
        if (ballView != null) return
        windowManager = getSystemService(WindowManager::class.java)
        val density = resources.displayMetrics.density
        val size = (52 * density).toInt()
        ballView = BallView(this).apply { layoutParams = WindowManager.LayoutParams(size, size) }
        ballParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenSize().first - size - (24 * density).toInt()
            y = (screenSize().second / 3f).toInt()
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        ballView!!.setOnTouchListener { _, event ->
            val p = ballParams ?: return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = p.x; startY = p.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    p.x = (startX + dx).toInt()
                    p.y = (startY + dy).toInt()
                    try { windowManager?.updateViewLayout(ballView, p) } catch (_: Exception) {}
                }
                MotionEvent.ACTION_UP -> if (!moved) openAutoAccount()
            }
            true
        }
        try {
            windowManager?.addView(ballView, ballParams)
        } catch (e: Exception) {
            toast("未获得悬浮窗权限，请到「我的 → 悬浮球记账」开启")
        }
    }

    // ---------- 点击悬浮球：跳转自动记账页 ----------

    private fun openAutoAccount() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_AUTO_ACCOUNTING, true)
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = windowManager ?: getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮球记账", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮球记账已开启")
            .setContentText("先截图，再点击悬浮球自动记账")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** 悬浮球点击跳转自动记账页的 Intent 标志。 */
        const val EXTRA_AUTO_ACCOUNTING = "auto_accounting"
        private const val ACTION_STOP = "com.cashtable.app.p4.STOP"
        private const val CHANNEL_ID = "floating_ball"
        private const val NOTIFY_ID = 1001

        /** 悬浮球服务是否在运行（供设置页开关状态展示）。 */
        @Volatile
        var isActive = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingBallService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

/** 悬浮球本体：绿色圆形 + 「记」字。 */
private class BallView(context: Context) : View(context) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00965E.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        canvas.drawCircle(cx, cy, r, bg)
        val baseline = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText("记", cx, baseline, text)
    }
}
