package com.cashtable.app.data

/** 数据仓库：封装 API 调用，页面层只与 Repository 交互。 */
class Repository(private val prefs: Prefs) {

    private val api: ApiService
        get() = ApiClient.getApi(prefs.serverUrl)

    suspend fun loadUsers(): List<UserDto> = api.users()

    suspend fun loadCategories(kind: String? = null): List<CategoryDto> = api.categories(kind)

    suspend fun createCategory(name: String, kind: String, icon: String = "", parentId: Int? = null): CategoryDto =
        api.createCategory(CategoryCreateRequest(name = name, kind = kind, icon = icon, parent_id = parentId))

    suspend fun loadTransactions(
        month: String? = null,
        categoryId: Int? = null,
        keyword: String? = null,
        createdBy: Int? = null,
        page: Int = 1,
        pageSize: Int = 200,
    ): TransactionListDto = api.transactions(month, categoryId, keyword, createdBy, page, pageSize)

    suspend fun createTransaction(
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
        createdBy: Int?,
    ): TransactionDto = api.createTransaction(
        TransactionCreateRequest(
            amount = amount,
            category_id = categoryId,
            happened_at = happenedAt,
            created_by = createdBy,
            content = content,
        )
    )

    suspend fun updateTransaction(
        id: Int,
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
    ): TransactionDto = api.updateTransaction(
        id,
        TransactionUpdateRequest(
            amount = amount,
            category_id = categoryId,
            happened_at = happenedAt,
            content = content,
        )
    )

    suspend fun deleteTransaction(id: Int) = api.deleteTransaction(id)

    suspend fun loadMonthlyStats(year: String): List<MonthlyStatDto> = api.monthlyStat(year)

    suspend fun loadAppConfig(): AppConfigDto = api.appConfig()
}
