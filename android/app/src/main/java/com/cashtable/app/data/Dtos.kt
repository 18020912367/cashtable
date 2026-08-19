package com.cashtable.app.data

/** 后端响应 DTO（字段名与 FastAPI Pydantic 输出一致，snake_case）。 */

data class UserDto(
    val id: Int,
    val name: String,
    val created_at: String,
)

data class CategoryDto(
    val id: Int,
    val name: String,
    val parent_id: Int?,
    val kind: String, // expense / income
    val icon: String,
    val sort: Int,
)

data class TransactionDto(
    val id: Int,
    val amount: Double,
    val category_id: Int?,
    val category_name: String?,
    val happened_at: String,
    val created_at: String,
    val created_by: Int?,
    val content: String,
    val source: String,
    val external_id: String,
)

data class TransactionListDto(
    val total: Int,
    val items: List<TransactionDto>,
)

data class MonthlyStatDto(
    val month: String,
    val kind: String,
    val total: Double,
)

/** 服务端下发的 App 通用配置（/api/app/config）：版本检查 + 默认服务器地址。 */
data class AppConfigDto(
    val app_name: String,
    val latest_version_code: Int,
    val latest_version_name: String,
    val apk_url: String,
    /** 配置内容指纹（服务端 MD5）：用于排查本地缓存了旧配置的问题。 */
    val config_fingerprint: String? = null,
    val changelog: String,
    val default_server_url: String,
)

// ---------- 请求体 ----------

data class UserCreateRequest(
    val name: String,
)

data class CategoryCreateRequest(
    val name: String,
    val kind: String,
    val parent_id: Int? = null,
    val icon: String = "",
)

data class TransactionCreateRequest(
    val amount: Double,
    val category_id: Int?,
    val happened_at: String,
    val created_by: Int?,
    val content: String,
    val source: String = "manual",
    val external_id: String = "",
)

data class TransactionUpdateRequest(
    val amount: Double? = null,
    val category_id: Int? = null,
    val happened_at: String? = null,
    val content: String? = null,
)

data class BatchCreateRequest(
    val rows: List<TransactionCreateRequest>,
    val source: String = "manual",
)

data class BatchResultDto(
    val total: Int,
    val imported: Int,
    val skipped: Int,
)

/** 截图识别结果（/api/ai/extract/）：字段与后端 VisionExtractResponse 一致。 */
data class VisionExtractDto(
    val transaction_time: String?,
    val description: String?,
    val transaction_type: String?,
    val category_id: Int?,
    val kind: String?,
    val amount: Double?,
    val source: String,
)

// ---------- 全功能模式激活 ----------

data class ActivationCheckRequest(
    val code: String,
    val device_mac: String = "",
)

data class ActivationCheckResponse(
    val ok: Boolean,
    val message: String,
    val is_activated: Boolean = false,
    val activated_at: String = "",
    val device_mac: String = "",
)

data class ActivationUploadResponse(
    val ok: Boolean,
    val message: String,
    val activated_at: String = "",
)
