package com.cashtable.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Retrofit 实例工厂：按 BaseUrl 缓存。
 * 无鉴权、无拦截器（内部自用小工具），仅配置基础超时。
 */
object ApiClient {

    private val cache = ConcurrentHashMap<String, ApiService>()

    fun getApi(baseUrl: String): ApiService = cache.getOrPut(baseUrl) {
        val client = OkHttpClient.Builder()
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
