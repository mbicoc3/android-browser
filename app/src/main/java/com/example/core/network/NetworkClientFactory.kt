package com.example.core.network

import com.example.BuildConfig
import com.example.feature.shield.network.FilterListApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClientFactory {

    private const val SUGGESTIONS_BASE_URL = "https://duckduckgo.com/"

    val dohDns: OrbitDohDns by lazy { OrbitDohDns(DohProvider.CLOUDFLARE) }

    fun setDohProvider(provider: DohProvider) {
        dohDns.setProvider(provider)
    }

    fun currentDohProvider(): DohProvider = dohDns.currentProvider

    fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(dohDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (BuildConfig.DEBUG) {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logger)
        }
        return builder.build()
    }

    fun createDownloadOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 16
        }
        return OkHttpClient.Builder()
            .dns(dohDns)
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(16, 2, TimeUnit.MINUTES))
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun createMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun createSearchSuggestionApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): SearchSuggestionApi {
        return Retrofit.Builder()
            .baseUrl(SUGGESTIONS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SearchSuggestionApi::class.java)
    }

    fun createFilterListApi(okHttpClient: OkHttpClient): FilterListApi {
        return Retrofit.Builder()
            .baseUrl("https://easylist.to/")
            .client(okHttpClient)
            .build()
            .create(FilterListApi::class.java)
    }
}
