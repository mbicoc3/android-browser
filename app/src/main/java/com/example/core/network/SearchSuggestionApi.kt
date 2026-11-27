package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class SearchSuggestionDto(
    @Json(name = "phrase") val phrase: String
)

interface SearchSuggestionApi {
    @GET("ac/")
    suspend fun getSuggestions(
        @Query("q") query: String
    ): List<SearchSuggestionDto>
}
