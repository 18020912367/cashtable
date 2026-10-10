package com.cashtable.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Retrofit 实例工厂：按 BaseUrl 缓存。
 * OkHttpClient 带动态请求头拦截器：全功能模式由 Repository 注入
 * X-Activate-Code / X-Device-Mac，后端据此路由到对应租户库（{激活码}_cashtable.db）。
 */
object ApiClient {

    private val cache = ConcurrentHashMap<String, ApiService>()

    /** 动态请求头提供者（返回的空值头会被跳过），由 Repository 初始化时注入。 */
    var headerProvider: (() -> Map<String, String>)? = null

    fun getApi(baseUrl: String): ApiService = cache.getOrPut(baseUrl) {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                headerProvider?.invoke()?.forEach { (k, v) ->
                    if (v.isNotBlank()) builder.header(k, v)
                }
                chain.proceed(builder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
