package com.example.feature.shield.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface FilterListApi {
    @Streaming
    @GET
    suspend fun downloadFilterList(@Url url: String): ResponseBody
}
