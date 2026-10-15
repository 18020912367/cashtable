package com.cashtable.app.data

import android.app.Application
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 崩溃与功能异常日志（全功能模式）：
 * - 全局崩溃/闪退：install() 设置默认未捕获异常处理器，崩溃瞬间同步上报一次 + 本地兜底；
 * - 功能异常：关键功能失效点（同步失败、权限授予后未生效、识别失败等）调用 [log]；
 * - 仅全功能模式（已激活）上报；云端按激活码写 logs/{code}.log，一行 = 时间 | 设备标识 | 类型 | 详情；
 * - 未送达的记录落本地 pending 文件，下次启动 [flushPending] 自动重试。
 */
object CrashLog {

    private const val KIND_CRASH = "CRASH"
    private const val MAX_DETAIL = 4000

    private val executor = Executors.newSingleThreadExecutor()

    /** 安装默认未捕获异常处理器（Application.onCreate 调用；保留原处理器兜底）。 */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                reportNow(app, KIND_CRASH, "线程=${thread.name} 堆栈=${error.stackTraceToString()}")
            } catch (_: Throwable) {
                // 崩溃处理器内绝不再抛
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 启动时重试上次未送达的日志（后台线程执行）。 */
    fun flushPending(context: Context) {
        executor.execute {
            val file = pendingFile(context)
            if (!file.exists()) return@execute
            val lines = try {
                file.readLines()
            } catch (_: Exception) {
                return@execute
            }
            val remain = mutableListOf<String>()
            for (line in lines) {
                if (line.isBlank()) continue
                if (!postRaw(line)) remain += line
            }
            try {
                if (remain.isEmpty()) file.delete() else file.writeText(remain.joinToString("\n"))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 功能异常记录入口（仅全功能模式上报；后台线程执行，不阻塞调用方）。
     * kind 建议取值：SYNC_FAIL / ACTIVATE_FAIL / OCR_FAIL / UPDATE_FAIL / INSTALL_FAIL /
     * FGS_FAIL / OVERLAY_FAIL / PROJECTION_FAIL / PROJECTION_EXPIRED / CAPTURE_FAIL。
     */
    fun log(context: Context, kind: String, detail: String) {
        executor.execute { reportNow(context, kind, detail) }
    }

    // ---------- 内部实现 ----------

    /** 同步上报（崩溃处理器与后台线程共用）：未激活跳过；上报失败写 pending 兜底。 */
    private fun reportNow(context: Context, kind: String, detail: String) {
        val p = Prefs(context)
        val code = p.activationCode
        if (p.mode != AppMode.Full || code.isBlank()) return
        val body = JSONObject().apply {
            put("code", code)
            put("device_mac", deviceId(context, p))
            put("kind", kind)
            put("detail", oneLine(detail))
        }.toString()
        if (!postRaw(body)) {
            try {
                pendingFile(context).appendText(body + "\n")
            } catch (_: Exception) {
            }
        }
    }

    /** 设备标识：ANDROID_ID（Android 10+ 无法取真实 MAC，与激活记录的设备标识保持一致）。 */
    private fun deviceId(context: Context, p: Prefs): String {
        if (p.deviceMac.isNotEmpty()) return p.deviceMac
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        p.deviceMac = id
        return id
    }

    /** POST JSON 到云端日志接口；2xx 返回 true。 */
    private fun postRaw(json: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(Prefs.PUBLIC_SERVER + "api/logs/report/").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** 未送达日志兜底文件：每行一条完整 JSON 请求体。 */
    private fun pendingFile(context: Context): File =
        File(File(context.filesDir, "logs").apply { mkdirs() }, "pending.jsonl")

    /** 多行堆栈压成单行（保留可读性）并截断，保证云端一行一条记录。 */
    private fun oneLine(text: String): String =
        text.replace("\r", "").replace("\n", " \\n ").take(MAX_DETAIL)
}
