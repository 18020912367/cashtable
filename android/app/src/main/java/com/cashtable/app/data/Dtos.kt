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
    val changelog: String,
    val default_server_url: String,
)

// ---------- 请求体 ----------

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
