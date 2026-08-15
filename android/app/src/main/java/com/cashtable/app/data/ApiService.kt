package com.cashtable.app.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** 与 FastAPI 后端对接的接口定义（路径见 design.md 第 5 节）。 */
interface ApiService {

    @GET("api/users/")
    suspend fun users(): List<UserDto>

    @GET("api/categories/")
    suspend fun categories(@Query("kind") kind: String? = null): List<CategoryDto>

    @POST("api/categories/")
    suspend fun createCategory(@Body body: CategoryCreateRequest): CategoryDto

    @GET("api/transactions/")
    suspend fun transactions(
        @Query("month") month: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("keyword") keyword: String? = null,
        @Query("created_by") createdBy: Int? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): TransactionListDto

    @POST("api/transactions/")
    suspend fun createTransaction(@Body body: TransactionCreateRequest): TransactionDto

    @PATCH("api/transactions/{id}/")
    suspend fun updateTransaction(@Path("id") id: Int, @Body body: TransactionUpdateRequest): TransactionDto

    @DELETE("api/transactions/{id}/")
    suspend fun deleteTransaction(@Path("id") id: Int)

    @GET("api/statistics/monthly/")
    suspend fun monthlyStat(@Query("year") year: String): List<MonthlyStatDto>

    @GET("api/app/config")
    suspend fun appConfig(): AppConfigDto

    /** 截图识别（P3）：上传支付截图，返回交易时间/内容/类型/金额。 */
    @Multipart
    @POST("api/ai/extract/")
    suspend fun extractTransaction(@Part file: MultipartBody.Part): VisionExtractDto
}
