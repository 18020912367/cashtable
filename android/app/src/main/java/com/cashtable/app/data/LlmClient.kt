package com.cashtable.app.data

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 本地模式 LLM 直连识别：OpenAI 兼容 /chat/completions（视觉模型）。
 *
 * 使用个人中心维护的 LLM Key（EncryptedSharedPreferences），提示词与字段
 * 规范化逻辑与后端 vision_service.py 保持一致；429 限流时退避重试并尝试
 * 备用模型。
 */
class LlmClient(
    private val prefs: Prefs,
    private val local: LocalAccountDataSource,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 识别支付截图 → 结构化账单字段（与 /api/ai/extract/ 返回值一致）。 */
    fun extract(imageBytes: ByteArray, mime: String): VisionExtractDto {
        val categories = local.loadCategories()
        val rules = buildRulesText(categories)
        val raw = callLlm(imageBytes, mime, rules)

        var amount = normalizeAmount(raw.opt("amount"))
        var kind = if (amount != null && amount < 0) "expense" else "income"
        val (categoryId, catKind) = matchCategory(categories, raw.optString("transaction_type"))
        if (catKind != null) {
            kind = catKind
            if (amount != null) {
                if (kind == "expense" && amount > 0) amount = -amount
                if (kind == "income" && amount < 0) amount = -amount
            }
        }
        // 匹配到分类时用标准分类名回填（剔除 LLM 夹带的注释）
        val typeName = categoryId?.let { cid -> categories.firstOrNull { it.id == cid }?.name }

        return VisionExtractDto(
            transaction_time = normalizeTime(raw.optString("transaction_time")),
            description = raw.optString("description").trim().ifEmpty { null },
            transaction_type = typeName ?: raw.optString("transaction_type").trim().ifEmpty { null },
            category_id = categoryId,
            kind = kind,
            amount = amount,
            source = "llm",
        )
    }

    /** 测试 LLM 连通性：发一条最小请求，成功返回"连接成功"，失败返回错误信息。 */
    fun testConnection(): String {
        val key = prefs.llmApiKey
        if (key.isEmpty()) return "尚未配置 LLM API Key"
        return try {
            val payload = JSONObject().apply {
                put("model", prefs.llmModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "你好，请只回复 OK")
                    })
                })
                put("max_tokens", 8)
            }
            http.newCall(
                Request.Builder()
                    .url("${prefs.llmBaseUrl}/chat/completions")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .header("Authorization", "Bearer $key")
                    .build(),
            ).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return "连接失败（HTTP ${resp.code}）：${body.take(120)}"
                }
                "连接成功"
            }
        } catch (e: Exception) {
            "连接失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ---------- 内部：LLM 调用 ----------

    private fun callLlm(imageBytes: ByteArray, mime: String, rules: String): JSONObject {
        val models = listOfNotNull(prefs.llmModel, FALLBACK_MODEL).distinct()
        var lastErr: Exception? = null
        for (model in models) {
            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", 0.1)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put(
                                    "text",
                                    "可用分类（一级分类，请从中选择最匹配的作为 transaction_type）：\n$rules\n\n" +
                                        "请提取这张支付截图中的交易信息，严格按照以下JSON格式输出：\n" +
                                        "{\"transaction_time\": \"交易时间\", \"description\": \"交易内容\", \"transaction_type\": \"交易类型\", \"amount\": \"金额\"}\n\n" +
                                        "只输出JSON，不要有其他内容。",
                                )
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    JSONObject().apply {
                                        put("url", "data:$mime;base64,${Base64.encodeToString(imageBytes, Base64.NO_WRAP)}")
                                    },
                                )
                            })
                        })
                    })
                })
            }
            val request = Request.Builder()
                .url("${prefs.llmBaseUrl}/chat/completions")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer ${prefs.llmApiKey}")
                .build()

            var rateLimited = false
            var attempt = 0
            while (true) {
                attempt++
                try {
                    http.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            if (resp.code == 429) {
                                rateLimited = true
                                lastErr = Exception("大模型接口限流 HTTP 429：${body.take(120)}")
                            } else {
                                lastErr = Exception("大模型接口调用失败 HTTP ${resp.code}：${body.take(120)}")
                                return@use lastErr // 非限流直接失败
                            }
                        } else {
                            val obj = JSONObject(body)
                            val content = obj.getJSONArray("choices")
                                .getJSONObject(0).getJSONObject("message").getString("content").trim()
                            val m = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(content)
                                ?: throw Exception("大模型返回非 JSON：${content.take(120)}")
                            return JSONObject(m.value)
                        }
                    }
                    if (rateLimited && attempt < 3) {
                        Thread.sleep(2000L shl (attempt - 1))
                        continue
                    }
                    break
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < 3) {
                        Thread.sleep(2000L shl (attempt - 1))
                        continue
                    }
                    break
                }
            }
            if (rateLimited && lastErr != null) {
                // 本模型限流 → 尝试备用模型
                continue
            }
            lastErr?.let { throw it }
        }
        throw lastErr ?: Exception("大模型调用失败")
    }

    // ---------- 内部：规范化（与后端一致） ----------

    private fun buildRulesText(categories: List<CategoryDto>): String {
        val parents = categories.filter { it.parent_id == null }
        val children = categories.filter { it.parent_id != null }
        val lines = parents.map { p ->
            val subs = children.filter { it.parent_id == p.id }
            if (subs.isNotEmpty()) {
                "${p.name}(id=${p.id}, ${p.kind})，二级分类：${subs.joinToString("、") { "${it.name}(id=${it.id})" }}"
            } else {
                "${p.name}(id=${p.id}, ${p.kind})"
            }
        }
        return lines.joinToString("\n")
    }

    private fun matchCategory(categories: List<CategoryDto>, name: String?): Pair<Int?, String?> {
        val kw = name?.trim().orEmpty()
        if (kw.isEmpty()) return null to null
        // 精确 → 包含
        categories.firstOrNull { it.name == kw }?.let { return it.id to it.kind }
        categories.firstOrNull { it.name.contains(kw) }?.let { return it.id to it.kind }
        // 双向包含
        categories.firstOrNull { kw in it.name || it.name in kw }?.let { return it.id to it.kind }
        return null to null
    }

    private fun normalizeTime(raw: String?): String? {
        val s = raw?.trim()?.trim('"', '\'')?.takeIf { it.isNotEmpty() } ?: return null
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        )
        for (fmt in formatters) {
            try {
                return LocalDateTime.parse(s, fmt).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun normalizeAmount(raw: Any?): Double? {
        if (raw == null) return null
        var s = raw.toString().trim().trim('"', '\'')
        if (s.isEmpty()) return null
        // 全用 unicode 转义：避免 $元 被 Kotlin 当字符串模板、避免非 ASCII 字面量编码问题
        s = s.replace(Regex("[\\u00A5\\uFFE5\\u0024\\u5143]"), "").replace("，", "").replace(",", "")
        return s.toDoubleOrNull()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** 备用模型：主模型 429 限流时切换。 */
        private const val FALLBACK_MODEL = "glm-4.6v-flash"

        /** 与后端 vision_service.SYSTEM_PROMPT 一致。 */
        const val SYSTEM_PROMPT = "你是一个账单信息提取助手。你的任务是从用户提供的支付截图中，提取出以下4个字段：\n" +
            "1. 交易时间\n" +
            "2. 交易内容（即商品或服务描述或对方名称），如现场扫码支付，只有店铺信息没有商品信息则将店铺信息作为内容，如果有交易备注则备注也组合成内容\n" +
            "3. 交易类型（调用本项目内的已有分类表中的信息）\n" +
            "4. 金额（带正负号）\n" +
            "\n" +
            "**严格要求**：\n" +
            "- 你必须只输出一个合法的JSON对象，不要包含任何其他文字、解释或标记。\n" +
            "- JSON的key必须严格为：transaction_time, description, transaction_type, amount。\n" +
            "- 如果图片中无法识别某个字段，请将该字段的值设为null。\n" +
            "- 金额只保留数字，不要包含货币符号（如¥、$）。"
    }
}
